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
