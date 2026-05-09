package com.xbk.lattice.compiler.service;

import com.xbk.lattice.shared.json.JsonMappers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xbk.lattice.compiler.service.mapper.FactCardGenerationMapper;
import com.xbk.lattice.infra.persistence.FactCardJdbcRepository;
import com.xbk.lattice.infra.persistence.FactCardRecord;
import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;
import com.xbk.lattice.query.evidence.domain.FactCardType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 事实卡生成基础支持
 *
 * 职责：承载 FactCardGenerationService 拆分出来的同类辅助逻辑
 *
 * @author xiexu
 */
abstract class FactCardGenerationBaseSupport {

protected static final ObjectMapper OBJECT_MAPPER = JsonMappers.moduleAwareMapper();

    protected static final Pattern BULLET_PATTERN = Pattern.compile("^\\s*[-*+]\\s+(.+?)\\s*$");

    protected static final Pattern ORDERED_PATTERN = Pattern.compile(
            "^\\s*((?:\\d+)|(?:[一二三四五六七八九十]+))[.)、．]\\s*(.+?)\\s*$"
    );

    protected static final Pattern KEY_VALUE_PATTERN = Pattern.compile(
            "^\\s*([^:=：]{1,80}?)\\s*[:=：]\\s*(.+?)\\s*$"
    );

    protected static final List<StatusDefinition> STATUS_DEFINITIONS = List.of(
            new StatusDefinition("待确认", "PENDING"),
            new StatusDefinition("未确认", "PENDING"),
            new StatusDefinition("待处理", "PENDING"),
            new StatusDefinition("pending", "PENDING"),
            new StatusDefinition("已确认", "CONFIRMED"),
            new StatusDefinition("已完成", "CONFIRMED"),
            new StatusDefinition("完成", "CONFIRMED"),
            new StatusDefinition("done", "CONFIRMED"),
            new StatusDefinition("无流量", "FLOW_OFF"),
            new StatusDefinition("有流量", "FLOW_ON"),
            new StatusDefinition("删除", "REMOVED"),
            new StatusDefinition("移除", "REMOVED"),
            new StatusDefinition("下线", "REMOVED"),
            new StatusDefinition("废弃", "REMOVED"),
            new StatusDefinition("禁用", "REMOVED"),
            new StatusDefinition("改为", "CHANGED"),
            new StatusDefinition("调整为", "CHANGED"),
            new StatusDefinition("变更为", "CHANGED")
    );

    protected static final List<String> POLICY_MARKERS = List.of(
            "必须",
            "不得",
            "禁止",
            "统一要求",
            "原则",
            "强约束",
            "只能",
            "需要",
            "应当",
            "应该",
            "不可",
            "不允许"
    );

    protected static final List<String> POLICY_SCOPE_MARKERS = List.of(
            "适用范围",
            "适用于",
            "适用",
            "对于",
            "当",
            "在",
            "范围"
    );

    boolean isTableLine(String line) {
        return line != null && line.trim().startsWith("|") && line.trim().endsWith("|");
    }

    /**
     * 判断是否为 bullet 行。
     *
     * @param line 文本行
     * @return 是否为 bullet 行
     */
    boolean isBulletLine(String line) {
        return line != null && BULLET_PATTERN.matcher(line).matches();
    }

    /**
     * 判断是否为有序列表行。
     *
     * @param line 文本行
     * @return 是否为有序列表行
     */
    boolean isOrderedLine(String line) {
        return line != null && ORDERED_PATTERN.matcher(line).matches();
    }

    /**
     * 判断两个有序列表行是否可能是连续结构。
     *
     * @param previousLine 前一行
     * @param nextLine 后一行
     * @return 是连续结构返回 true
     */
    boolean isLikelyOrderedContinuation(String previousLine, String nextLine) {
        Integer previousOrder = parseOrderNumber(previousLine);
        Integer nextOrder = parseOrderNumber(nextLine);
        if (previousOrder == null || nextOrder == null) {
            return true;
        }
        return nextOrder.intValue() == previousOrder.intValue() + 1;
    }

    /**
     * 解析有序列表行的数字序号。
     *
     * @param line 文本行
     * @return 序号数字
     */
    Integer parseOrderNumber(String line) {
        Matcher matcher = ORDERED_PATTERN.matcher(line == null ? "" : line);
        if (!matcher.matches()) {
            return null;
        }
        String rawOrder = matcher.group(1).trim();
        if (rawOrder.matches("\\d+")) {
            return Integer.valueOf(rawOrder);
        }
        return parseChineseOrderNumber(rawOrder);
    }

    /**
     * 解析常见中文序号。
     *
     * @param value 中文序号
     * @return 序号数字
     */
    Integer parseChineseOrderNumber(String value) {
        Map<Character, Integer> numberByCharacter = new LinkedHashMap<Character, Integer>();
        numberByCharacter.put(Character.valueOf('一'), Integer.valueOf(1));
        numberByCharacter.put(Character.valueOf('二'), Integer.valueOf(2));
        numberByCharacter.put(Character.valueOf('三'), Integer.valueOf(3));
        numberByCharacter.put(Character.valueOf('四'), Integer.valueOf(4));
        numberByCharacter.put(Character.valueOf('五'), Integer.valueOf(5));
        numberByCharacter.put(Character.valueOf('六'), Integer.valueOf(6));
        numberByCharacter.put(Character.valueOf('七'), Integer.valueOf(7));
        numberByCharacter.put(Character.valueOf('八'), Integer.valueOf(8));
        numberByCharacter.put(Character.valueOf('九'), Integer.valueOf(9));
        String normalized = safeText(value).trim();
        if (normalized.isBlank()) {
            return null;
        }
        if ("十".equals(normalized)) {
            return Integer.valueOf(10);
        }
        if (normalized.startsWith("十") && normalized.length() == 2) {
            Integer suffix = numberByCharacter.get(Character.valueOf(normalized.charAt(1)));
            return suffix == null ? null : Integer.valueOf(10 + suffix.intValue());
        }
        if (normalized.endsWith("十") && normalized.length() == 2) {
            Integer prefix = numberByCharacter.get(Character.valueOf(normalized.charAt(0)));
            return prefix == null ? null : Integer.valueOf(prefix.intValue() * 10);
        }
        if (normalized.length() == 1) {
            return numberByCharacter.get(Character.valueOf(normalized.charAt(0)));
        }
        if (normalized.length() == 3 && normalized.charAt(1) == '十') {
            Integer prefix = numberByCharacter.get(Character.valueOf(normalized.charAt(0)));
            Integer suffix = numberByCharacter.get(Character.valueOf(normalized.charAt(2)));
            if (prefix != null && suffix != null) {
                return Integer.valueOf(prefix.intValue() * 10 + suffix.intValue());
            }
        }
        return null;
    }

    /**
     * 判断是否为键值行。
     *
     * @param line 文本行
     * @return 是否为键值行
     */
    boolean isKeyValueLine(String line) {
        return line != null && KEY_VALUE_PATTERN.matcher(stripListMarker(line)).matches();
    }

    /**
     * 判断是否为规则行。
     *
     * @param line 文本行
     * @return 是否为规则行
     */
    boolean isPolicyLine(String line) {
        String text = stripListMarker(line).trim();
        return hasAnyMarker(text, POLICY_MARKERS) || hasAnyMarker(text, POLICY_SCOPE_MARKERS);
    }

    /**
     * 判断是否可能是标题行。
     *
     * @param line 文本行
     * @return 可能是标题返回 true
     */
    boolean isLikelyTitleLine(String line) {
        String normalized = safeText(line).trim();
        if (normalized.isBlank()) {
            return false;
        }
        if (isStructuralStartLine(normalized) || normalized.length() > 80) {
            return false;
        }
        return normalized.startsWith("#")
                || normalized.endsWith("：")
                || normalized.endsWith(":")
                || (!normalized.matches(".*[。.!！?？]$") && !KEY_VALUE_PATTERN.matcher(normalized).matches());
    }

    /**
     * 判断是否为结构块起始行。
     *
     * @param line 文本行
     * @return 是结构块起始行返回 true
     */
    boolean isStructuralStartLine(String line) {
        return isTableLine(line)
                || isBulletLine(line)
                || isOrderedLine(line)
                || isKeyValueLine(line)
                || isPolicyLine(line);
    }

    /**
     * 判断是否为 Markdown 表格分隔行。
     *
     * @param line 文本行
     * @return 是否为分隔行
     */
    boolean isSeparatorRow(String line) {
        List<String> cells = splitTableCells(line);
        if (cells.isEmpty()) {
            return false;
        }
        for (String cell : cells) {
            String normalized = cell.replace(":", "").replace("-", "").trim();
            if (!normalized.isBlank()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 拆分 Markdown 表格单元格。
     *
     * @param line 表格行
     * @return 单元格列表
     */
    List<String> splitTableCells(String line) {
        if (line == null || line.isBlank()) {
            return List.of();
        }
        String normalized = line.trim();
        if (normalized.startsWith("|")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("|")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        String[] rawCells = normalized.split("\\|", -1);
        List<String> cells = new ArrayList<String>();
        for (String rawCell : rawCells) {
            cells.add(rawCell.trim());
        }
        return cells;
    }

    List<LineItem> findBulletItems(List<String> lines) {
        List<LineItem> items = new ArrayList<LineItem>();
        int order = 1;
        for (String line : lines) {
            Matcher matcher = BULLET_PATTERN.matcher(line);
            if (matcher.matches()) {
                items.add(new LineItem(String.valueOf(order), matcher.group(1).trim(), line.trim()));
                order++;
            }
        }
        return items;
    }

    /**
     * 查找有序列表项。
     *
     * @param lines chunk 行
     * @return 有序列表项
     */
    List<LineItem> findOrderedItems(List<String> lines) {
        List<LineItem> items = new ArrayList<LineItem>();
        for (String line : lines) {
            Matcher matcher = ORDERED_PATTERN.matcher(line);
            if (matcher.matches()) {
                items.add(new LineItem(matcher.group(1).trim(), matcher.group(2).trim(), line.trim()));
            }
        }
        return items;
    }

    /**
     * 查找键值列表项。
     *
     * @param lines chunk 行
     * @return 键值列表项
     */
    List<KeyValueItem> findKeyValueItems(List<String> lines) {
        List<KeyValueItem> items = new ArrayList<KeyValueItem>();
        for (String line : lines) {
            Matcher matcher = KEY_VALUE_PATTERN.matcher(stripListMarker(line));
            if (!matcher.matches()) {
                continue;
            }
            String key = matcher.group(1).trim();
            String value = matcher.group(2).trim();
            if (key.isBlank() || value.isBlank()) {
                continue;
            }
            items.add(new KeyValueItem(key, value, line.trim()));
        }
        return items;
    }

    /**
     * 查找状态条目。
     *
     * @param lines chunk 行
     * @return 状态条目
     */
    List<StatusItem> findStatusItems(List<String> lines) {
        List<StatusItem> items = new ArrayList<StatusItem>();
        for (String line : lines) {
            String text = stripListMarker(line).trim();
            StatusDefinition statusDefinition = findStatusDefinition(text);
            if (statusDefinition == null) {
                continue;
            }
            String subject = resolveStatusSubject(text, statusDefinition.getValue());
            if (subject.isBlank()) {
                continue;
            }
            items.add(new StatusItem(subject, statusDefinition.getValue(), statusDefinition.getGroup(), line.trim()));
        }
        return items;
    }

    /**
     * 查找规则约束条目。
     *
     * @param lines chunk 行
     * @return 规则约束条目
     */
    List<PolicyItem> findPolicyItems(List<String> lines) {
        List<PolicyItem> items = new ArrayList<PolicyItem>();
        for (String line : lines) {
            String text = stripListMarker(line).trim();
            if (!hasAnyMarker(text, POLICY_MARKERS)) {
                continue;
            }
            items.add(new PolicyItem(text, line.trim()));
        }
        return items;
    }

    /**
     * 查找规则适用范围。
     *
     * @param lines chunk 行
     * @return 适用范围行
     */
    List<String> findPolicyScopes(List<String> lines) {
        List<String> scopes = new ArrayList<String>();
        for (String line : lines) {
            String text = stripListMarker(line).trim();
            if (hasAnyMarker(text, POLICY_SCOPE_MARKERS)) {
                scopes.add(line.trim());
            }
        }
        return scopes;
    }

    /**
     * 查找状态定义。
     *
     * @param text 文本
     * @return 状态定义
     */
    StatusDefinition findStatusDefinition(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (StatusDefinition statusDefinition : STATUS_DEFINITIONS) {
            if (text.contains(statusDefinition.getValue())) {
                return statusDefinition;
            }
        }
        return null;
    }

    /**
     * 解析状态条目的主语。
     *
     * @param text 文本
     * @param status 状态词
     * @return 状态主语
     */
    String resolveStatusSubject(String text, String status) {
        Matcher matcher = KEY_VALUE_PATTERN.matcher(text);
        if (matcher.matches()) {
            String key = matcher.group(1).trim();
            String value = matcher.group(2).trim();
            if (value.contains(status)) {
                return normalizeStatusSubject(key);
            }
            if (key.contains(status)) {
                return normalizeStatusSubject(value);
            }
        }
        String subject = text.replace(status, " ");
        return normalizeStatusSubject(subject);
    }

    /**
     * 规范化状态主语。
     *
     * @param value 原始主语
     * @return 规范化主语
     */
    String normalizeStatusSubject(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("^[\\s:：,，;；\\-—>]+", "")
                .replaceAll("[\\s:：,，;；\\-—>]+$", "")
                .trim();
    }

    /**
     * 查询状态冲突主语。
     *
     * @param items 状态条目
     * @return 冲突主语
     */
    List<String> findConflictSubjects(List<StatusItem> items) {
        Map<String, Set<String>> groupsBySubject = new LinkedHashMap<String, Set<String>>();
        Map<String, String> displaySubjectByKey = new LinkedHashMap<String, String>();
        for (StatusItem item : items) {
            String key = conflictSubjectKey(item.getSubject());
            displaySubjectByKey.putIfAbsent(key, item.getSubject());
            groupsBySubject.computeIfAbsent(key, ignored -> new LinkedHashSet<String>()).add(item.getStatusGroup());
        }
        List<String> conflictSubjects = new ArrayList<String>();
        for (Map.Entry<String, Set<String>> entry : groupsBySubject.entrySet()) {
            if (entry.getValue().size() > 1) {
                conflictSubjects.add(displaySubjectByKey.get(entry.getKey()));
            }
        }
        return conflictSubjects;
    }

    /**
     * 生成状态冲突判定 key。
     *
     * @param subject 主语
     * @return 判定 key
     */
    String conflictSubjectKey(String subject) {
        return safeText(subject)
                .replaceAll("\\s+", "")
                .replaceAll("[：:，,；;\\-—>]", "")
                .toLowerCase();
    }

    /**
     * 判断文本是否包含任一标记。
     *
     * @param text 文本
     * @param markers 标记列表
     * @return 是否包含
     */
    boolean hasAnyMarker(String text, List<String> markers) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String marker : markers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 去掉列表标记，便于识别列表内键值结构。
     *
     * @param line 原始行
     * @return 去标记后的行
     */
    String stripListMarker(String line) {
        if (line == null) {
            return "";
        }
        Matcher bulletMatcher = BULLET_PATTERN.matcher(line);
        if (bulletMatcher.matches()) {
            return bulletMatcher.group(1);
        }
        Matcher orderedMatcher = ORDERED_PATTERN.matcher(line);
        if (orderedMatcher.matches()) {
            return orderedMatcher.group(2);
        }
        return line;
    }

    FactCardReviewStatus resolveReviewStatus(FactCardSourceChunkView chunk, String evidenceText, boolean complete) {
        if (!complete) {
            return FactCardReviewStatus.INCOMPLETE;
        }
        String chunkText = safeText(chunk.getChunkText());
        if (!chunkText.contains(safeText(evidenceText))) {
            return FactCardReviewStatus.LOW_CONFIDENCE;
        }
        return FactCardReviewStatus.VALID;
    }

    /**
     * 拼接列表项证据文本。
     *
     * @param items 列表项
     * @return 证据文本
     */
    String joinEvidence(List<LineItem> items) {
        List<String> lines = new ArrayList<String>();
        for (LineItem item : items) {
            lines.add(item.getRaw());
        }
        return String.join("\n", lines);
    }

    /**
     * 拼接键值项证据文本。
     *
     * @param items 键值项
     * @return 证据文本
     */
    String joinKeyValueEvidence(List<KeyValueItem> items) {
        List<String> lines = new ArrayList<String>();
        for (KeyValueItem item : items) {
            lines.add(item.getRaw());
        }
        return String.join("\n", lines);
    }

    /**
     * 拼接状态条目证据文本。
     *
     * @param items 状态条目
     * @return 证据文本
     */
    String joinStatusEvidence(List<StatusItem> items) {
        List<String> lines = new ArrayList<String>();
        for (StatusItem item : items) {
            lines.add(item.getRaw());
        }
        return String.join("\n", lines);
    }

    /**
     * 拼接规则条目证据文本。
     *
     * @param constraints 规则约束
     * @param scopes 适用范围
     * @return 证据文本
     */
    String joinPolicyEvidence(List<String> sourceLines, List<PolicyItem> constraints, List<String> scopes) {
        List<String> lines = new ArrayList<String>();
        Set<String> relevantLines = new LinkedHashSet<String>();
        for (PolicyItem constraint : constraints) {
            relevantLines.add(constraint.getRaw());
        }
        for (String scope : scopes) {
            relevantLines.add(scope);
        }
        for (String sourceLine : sourceLines) {
            String normalizedLine = sourceLine == null ? "" : sourceLine.trim();
            if (relevantLines.contains(normalizedLine)) {
                addUniqueLine(lines, normalizedLine);
            }
        }
        return String.join("\n", lines);
    }

    /**
     * 按原始顺序添加唯一证据行。
     *
     * @param lines 已收集证据行
     * @param line 待添加证据行
     */
    void addUniqueLine(List<String> lines, String line) {
        if (line == null || line.isBlank() || lines.contains(line)) {
            return;
        }
        lines.add(line);
    }

    /**
     * 读取最后一个非空行。
     *
     * @param text 文本
     * @return 最后一个非空行
     */
    String lastMeaningfulLine(String text) {
        List<String> lines = splitLines(text);
        for (int index = lines.size() - 1; index >= 0; index--) {
            String line = lines.get(index);
            if (line != null && !line.trim().isBlank()) {
                return line.trim();
            }
        }
        return "";
    }

    /**
     * 读取第一个非空行。
     *
     * @param text 文本
     * @return 第一个非空行
     */
    String firstMeaningfulLine(String text) {
        List<String> lines = splitLines(text);
        for (String line : lines) {
            if (line != null && !line.trim().isBlank()) {
                return line.trim();
            }
        }
        return "";
    }

    /**
     * 拆分文本行。
     *
     * @param text 文本
     * @return 行列表
     */
    List<String> splitLines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] rawLines = text.split("\\R");
        List<String> lines = new ArrayList<String>();
        Collections.addAll(lines, rawLines);
        return lines;
    }

    /**
     * 序列化 JSON 节点。
     *
     * @param node JSON 节点
     * @return JSON 字符串
     */
    String writeJson(ObjectNode node) {
        try {
            return OBJECT_MAPPER.writeValueAsString(node);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to write fact card items json", ex);
        }
    }

    /**
     * 读取列表指定下标的值。
     *
     * @param values 值列表
     * @param index 下标
     * @return 值
     */
    String valueAt(List<String> values, int index) {
        if (index < 0 || index >= values.size()) {
            return "";
        }
        return values.get(index);
    }

    /**
     * 规范化 JSON 字段名。
     *
     * @param header 表头
     * @param index 下标
     * @return JSON 字段名
     */
    String normalizeJsonField(String header, int index) {
        String normalized = safeText(header)
                .trim()
                .replaceAll("\\s+", "_")
                .replaceAll("[^A-Za-z0-9_\\u4e00-\\u9fa5]", "");
        if (normalized.isBlank()) {
            return "column_" + (index + 1);
        }
        return normalized;
    }

    /**
     * 计算 SHA-256 十六进制哈希。
     *
     * @param value 原始值
     * @return 哈希值
     */
    String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(safeText(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) {
                builder.append(String.format("%02x", Byte.valueOf(item)));
            }
            return builder.toString();
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    /**
     * 返回非空文本。
     *
     * @param value 原始值
     * @return 非空文本
     */
    String safeText(String value) {
        return value == null ? "" : value;
    }

}
