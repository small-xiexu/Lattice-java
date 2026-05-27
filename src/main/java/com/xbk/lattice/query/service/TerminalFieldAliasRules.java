package com.xbk.lattice.query.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 末级字段别名规则
 *
 * 职责：从通用查询语义配置读取可审计的字段别名映射，避免主链硬编码中文字段词表
 *
 * @author xiexu
 */
@Slf4j
class TerminalFieldAliasRules {

    private static final String DEFAULT_RESOURCE = "config/lattice-query-semantic.yml";

    private static final TerminalFieldAliasRules EMPTY = new TerminalFieldAliasRules(Map.of());

    private final Map<String, String> canonicalByAlias;

    /**
     * 创建末级字段别名规则。
     *
     * @param canonicalByAlias alias 到 canonical 字段名的映射
     */
    TerminalFieldAliasRules(Map<String, String> canonicalByAlias) {
        this.canonicalByAlias = normalizeAliasMap(canonicalByAlias);
    }

    /**
     * 返回空别名规则。
     *
     * @return 空规则
     */
    static TerminalFieldAliasRules empty() {
        return EMPTY;
    }

    /**
     * 从默认 classpath 配置加载末级字段别名规则。
     *
     * @return 末级字段别名规则
     */
    static TerminalFieldAliasRules loadDefault() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = TerminalFieldAliasRules.class.getClassLoader();
        }
        try (InputStream inputStream = classLoader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (inputStream == null) {
                return empty();
            }
            Yaml yaml = new Yaml();
            Object rootNode = yaml.load(inputStream);
            List<?> aliasEntries = readTerminalFieldAliasEntries(rootNode);
            Map<String, String> aliasMap = readAliasMap(aliasEntries);
            return new TerminalFieldAliasRules(aliasMap);
        }
        catch (RuntimeException ex) {
            String errorMessage = ex.toString();
            log.warn("加载末级字段别名配置失败，已按空配置继续: {}", errorMessage);
            return empty();
        }
        catch (Exception ex) {
            String errorMessage = ex.toString();
            log.warn("读取末级字段别名配置失败，已按空配置继续: {}", errorMessage);
            return empty();
        }
    }

    /**
     * 从 canonical 到 aliases 的结构构造规则。
     *
     * @param aliasesByCanonical canonical 到 aliases 的映射
     * @return 末级字段别名规则
     */
    static TerminalFieldAliasRules fromCanonicalAliases(Map<String, List<String>> aliasesByCanonical) {
        if (aliasesByCanonical == null || aliasesByCanonical.isEmpty()) {
            return empty();
        }
        Map<String, String> aliasMap = new LinkedHashMap<String, String>();
        for (Map.Entry<String, List<String>> entry : aliasesByCanonical.entrySet()) {
            String canonical = normalizeToken(entry.getKey());
            if (!StringUtils.hasText(canonical) || entry.getValue() == null) {
                continue;
            }
            for (String alias : entry.getValue()) {
                String normalizedAlias = normalizeToken(alias);
                if (StringUtils.hasText(normalizedAlias)) {
                    aliasMap.put(normalizedAlias, canonical);
                }
            }
        }
        return new TerminalFieldAliasRules(aliasMap);
    }

    /**
     * 查找 alias 对应的 canonical 字段名。
     *
     * @param alias 字段别名
     * @return canonical 字段名；不存在返回空串
     */
    String canonicalForAlias(String alias) {
        String normalizedAlias = normalizeToken(alias);
        if (!StringUtils.hasText(normalizedAlias)) {
            return "";
        }
        return canonicalByAlias.getOrDefault(normalizedAlias, "");
    }

    /**
     * 归一化 alias 映射。
     *
     * @param rawAliasMap 原始映射
     * @return 归一化映射
     */
    private static Map<String, String> normalizeAliasMap(Map<String, String> rawAliasMap) {
        if (rawAliasMap == null || rawAliasMap.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalizedMap = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : rawAliasMap.entrySet()) {
            String alias = normalizeToken(entry.getKey());
            String canonical = normalizeToken(entry.getValue());
            if (StringUtils.hasText(alias) && StringUtils.hasText(canonical)) {
                normalizedMap.put(alias, canonical);
            }
        }
        return Map.copyOf(normalizedMap);
    }

    /**
     * 从 YAML 根节点读取末级字段别名配置。
     *
     * @param rootNode YAML 根节点
     * @return 配置项列表
     */
    private static List<?> readTerminalFieldAliasEntries(Object rootNode) {
        List<String> semanticPath = List.of("lattice", "query", "semantic");
        Object semanticNode = readNestedMapValue(rootNode, semanticPath);
        Object aliasEntries = readMapValue(semanticNode, "terminal-field-aliases");
        if (aliasEntries instanceof List<?>) {
            return (List<?>) aliasEntries;
        }
        return List.of();
    }

    /**
     * 从配置项列表读取 alias 映射。
     *
     * @param aliasEntries 配置项列表
     * @return alias 到 canonical 字段名的映射
     */
    private static Map<String, String> readAliasMap(List<?> aliasEntries) {
        Map<String, String> aliasMap = new LinkedHashMap<String, String>();
        for (Object aliasEntry : aliasEntries) {
            Object canonicalNode = readMapValue(aliasEntry, "canonical");
            Object aliasesNode = readMapValue(aliasEntry, "aliases");
            String canonical = normalizeToken(canonicalNode);
            if (!StringUtils.hasText(canonical) || !(aliasesNode instanceof List<?>)) {
                continue;
            }
            for (Object aliasNode : (List<?>) aliasesNode) {
                String alias = normalizeToken(aliasNode);
                if (StringUtils.hasText(alias)) {
                    aliasMap.put(alias, canonical);
                }
            }
        }
        return aliasMap;
    }

    /**
     * 按路径读取嵌套 Map 值。
     *
     * @param rootNode 根节点
     * @param keys 路径键
     * @return 节点值
     */
    private static Object readNestedMapValue(Object rootNode, List<String> keys) {
        Object currentNode = rootNode;
        for (String key : keys) {
            currentNode = readMapValue(currentNode, key);
            if (currentNode == null) {
                return null;
            }
        }
        return currentNode;
    }

    /**
     * 读取 Map 的单个键值。
     *
     * @param node Map 节点
     * @param key 键
     * @return 节点值
     */
    private static Object readMapValue(Object node, String key) {
        if (!(node instanceof Map<?, ?>)) {
            return null;
        }
        Map<?, ?> mapNode = (Map<?, ?>) node;
        return mapNode.get(key);
    }

    /**
     * 归一化字段 token。
     *
     * @param token 原始 token
     * @return 归一化 token
     */
    private static String normalizeToken(Object token) {
        if (token == null) {
            return "";
        }
        String tokenValue = String.valueOf(token);
        String trimmedToken = tokenValue.trim();
        return trimmedToken.toLowerCase(Locale.ROOT);
    }
}
