package com.xbk.lattice.query.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感文本脱敏器
 *
 * 职责：在问答证据与答案进入展示或模型上下文前屏蔽常见密钥类赋值
 *
 * @author xiexu
 */
final class SensitiveTextMasker {

    private static final String MASKED_VALUE = "<masked>";

    private static final String SENSITIVE_KEY_FRAGMENT =
            "[A-Za-z0-9_.-]*(?:api[_-]?key|secret|token|password|credential|access[_-]?key|private[_-]?key)[A-Za-z0-9_.-]*";

    private static final Pattern QUOTED_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?i)(\\b"
                    + SENSITIVE_KEY_FRAGMENT
                    + "\\b\\s*[:=]\\s*[\"'])([^\"'\\s]{4,})([\"'])"
    );

    private static final Pattern JSON_QUOTED_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?i)([\"']"
                    + SENSITIVE_KEY_FRAGMENT
                    + "[\"']\\s*:\\s*[\"'])([^\"']{4,})([\"'])"
    );

    private static final Pattern UNQUOTED_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?i)(\\b"
                    + SENSITIVE_KEY_FRAGMENT
                    + "\\b\\s*[:=]\\s*)([^,;\\s\\]}]+)"
    );

    private static final Pattern AUTHORIZATION_BEARER_PATTERN = Pattern.compile(
            "(?i)(\\bauthorization\\b\\s*[:=]\\s*bearer\\s+)([A-Za-z0-9._=-]{8,})"
    );

    private static final Pattern COMMON_API_KEY_PATTERN = Pattern.compile(
            "(?<![A-Za-z0-9])sk-[A-Za-z0-9._-]{6,}"
    );

    /**
     * 工具类不允许实例化。
     */
    private SensitiveTextMasker() {
    }

    /**
     * 脱敏文本中的常见密钥类赋值。
     *
     * @param value 原始文本
     * @return 脱敏后文本
     */
    static String mask(String value) {
        if (value == null || value.isBlank()) {
            return value == null ? "" : value;
        }
        String maskedValue = maskWithGroups(value, JSON_QUOTED_ASSIGNMENT_PATTERN, true);
        maskedValue = maskWithGroups(maskedValue, QUOTED_ASSIGNMENT_PATTERN, true);
        maskedValue = maskWithGroups(maskedValue, UNQUOTED_ASSIGNMENT_PATTERN, false);
        maskedValue = maskWithGroups(maskedValue, AUTHORIZATION_BEARER_PATTERN, false);
        return COMMON_API_KEY_PATTERN.matcher(maskedValue).replaceAll(MASKED_VALUE);
    }

    /**
     * 基于捕获组执行脱敏。
     *
     * @param value 原始文本
     * @param pattern 匹配模式
     * @param keepClosingQuote 是否保留结束引号
     * @return 脱敏后文本
     */
    private static String maskWithGroups(String value, Pattern pattern, boolean keepClosingQuote) {
        Matcher matcher = pattern.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String replacement = matcher.group(1) + MASKED_VALUE;
            if (keepClosingQuote) {
                replacement += matcher.group(3);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
