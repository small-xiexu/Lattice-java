package com.xbk.lattice.query.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 查询 token 提取器
 *
 * 职责：复用查询阶段的关键 token 提取逻辑
 *
 * @author xiexu
 */
public final class QueryTokenExtractor {

    private static final Pattern ASCII_TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9=_-]{2,}");

    private static final Pattern PATH_TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_./-]+\\.[A-Za-z0-9_./-]+");

    private static final Pattern CONFIG_KEY_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*(?:\\.[A-Za-z0-9_-]+)+");

    private static final Pattern CAMEL_TOKEN_PATTERN = Pattern.compile("[A-Z][A-Za-z0-9]+(?:[A-Z][A-Za-z0-9]+)+");

    private static final Pattern CAMEL_PART_PATTERN = Pattern.compile("[A-Z]?[a-z0-9]+|[A-Z]+(?=[A-Z]|$)");

    private static final Pattern HAN_TEXT_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}");

    private static final String CJK_STOP_CHARS = "的是了和及或在将把与就还都也着";

    private QueryTokenExtractor() {
    }

    /**
     * 从查询语句中提取稳定 token。
     *
     * @param question 查询问题
     * @return 去重后的 token 列表
     */
    public static List<String> extract(String question) {
        Set<String> tokens = new LinkedHashSet<String>();
        if (question == null || question.isBlank()) {
            return new ArrayList<String>(tokens);
        }
        appendPathAndConfigTokens(tokens, question);
        appendCamelCaseTokens(tokens, question);
        String normalizedQuestion = question.toLowerCase(Locale.ROOT);
        Matcher asciiMatcher = ASCII_TOKEN_PATTERN.matcher(normalizedQuestion);
        while (asciiMatcher.find()) {
            tokens.add(asciiMatcher.group());
        }
        Matcher hanMatcher = HAN_TEXT_PATTERN.matcher(question);
        while (hanMatcher.find()) {
            appendChineseTokens(tokens, hanMatcher.group());
        }
        return new ArrayList<String>(tokens);
    }

    /**
     * 提取路径与配置键 token。
     *
     * @param tokens token 集合
     * @param question 查询问题
     */
    private static void appendPathAndConfigTokens(Set<String> tokens, String question) {
        Matcher pathMatcher = PATH_TOKEN_PATTERN.matcher(question);
        while (pathMatcher.find()) {
            tokens.add(pathMatcher.group().toLowerCase(Locale.ROOT));
        }
        Matcher configMatcher = CONFIG_KEY_PATTERN.matcher(question);
        while (configMatcher.find()) {
            tokens.add(configMatcher.group().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * 提取类名、方法名等 camelCase/PascalCase token。
     *
     * @param tokens token 集合
     * @param question 查询问题
     */
    private static void appendCamelCaseTokens(Set<String> tokens, String question) {
        Matcher camelMatcher = CAMEL_TOKEN_PATTERN.matcher(question);
        while (camelMatcher.find()) {
            String camelToken = camelMatcher.group();
            tokens.add(camelToken.toLowerCase(Locale.ROOT));
            Matcher partMatcher = CAMEL_PART_PATTERN.matcher(camelToken);
            while (partMatcher.find()) {
                String part = partMatcher.group().toLowerCase(Locale.ROOT);
                if (part.length() >= 2) {
                    tokens.add(part);
                }
            }
        }
    }

    /**
     * 从连续中文片段中提取稳定 token。
     *
     * @param tokens token 集合
     * @param hanText 连续中文片段
     */
    private static void appendChineseTokens(Set<String> tokens, String hanText) {
        for (int window = 2; window <= 4; window++) {
            if (hanText.length() < window) {
                break;
            }
            for (int start = 0; start <= hanText.length() - window; start++) {
                String token = hanText.substring(start, start + window);
                if (containsStopChar(token)) {
                    continue;
                }
                tokens.add(token);
            }
        }
    }

    /**
     * 判断 token 是否包含高频虚词字符。
     *
     * @param token 待判断 token
     * @return 是否需要过滤
     */
    private static boolean containsStopChar(String token) {
        for (int index = 0; index < token.length(); index++) {
            if (CJK_STOP_CHARS.indexOf(token.charAt(index)) >= 0) {
                return true;
            }
        }
        return false;
    }
}
