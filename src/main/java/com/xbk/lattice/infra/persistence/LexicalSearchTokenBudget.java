package com.xbk.lattice.infra.persistence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lexical 检索 token 预算工具
 *
 * 职责：为数据库 LIKE 扩展条件选择有限数量的高信号 token
 *
 * @author xiexu
 */
public final class LexicalSearchTokenBudget {

    private static final int MAX_LIKE_TOKENS = 8;

    private LexicalSearchTokenBudget() {
    }

    /**
     * 规范化查询 token。
     *
     * @param queryTokens 原始 token
     * @return 小写去重 token
     */
    public static List<String> normalize(List<String> queryTokens) {
        if (queryTokens == null || queryTokens.isEmpty()) {
            return List.of();
        }
        Set<String> normalizedTokens = new LinkedHashSet<String>();
        for (String queryToken : queryTokens) {
            if (queryToken != null && !queryToken.isBlank()) {
                normalizedTokens.add(queryToken.trim().toLowerCase(Locale.ROOT));
            }
        }
        return new ArrayList<String>(normalizedTokens);
    }

    /**
     * 为 LIKE 条件选择有限 token。
     *
     * @param normalizedTokens 已规范化 token
     * @return LIKE token
     */
    public static List<String> selectLikeTokens(List<String> normalizedTokens) {
        if (normalizedTokens == null || normalizedTokens.isEmpty()) {
            return List.of();
        }
        List<RankedToken> rankedTokens = new ArrayList<RankedToken>();
        for (int index = 0; index < normalizedTokens.size(); index++) {
            String token = normalizedTokens.get(index);
            if (hasText(token)) {
                rankedTokens.add(new RankedToken(token, index, score(token)));
            }
        }
        rankedTokens.sort(Comparator
                .comparingInt(RankedToken::score).reversed()
                .thenComparingInt(RankedToken::index));
        List<String> selectedTokens = new ArrayList<String>();
        for (RankedToken rankedToken : rankedTokens) {
            if (rankedToken.score() <= 0) {
                continue;
            }
            selectedTokens.add(rankedToken.token());
            if (selectedTokens.size() >= MAX_LIKE_TOKENS) {
                break;
            }
        }
        if (!selectedTokens.isEmpty()) {
            return selectedTokens;
        }
        for (int index = 0; index < Math.min(MAX_LIKE_TOKENS, normalizedTokens.size()); index++) {
            selectedTokens.add(normalizedTokens.get(index));
        }
        return selectedTokens;
    }

    /**
     * 计算 token 的通用形态分。
     *
     * @param token token
     * @return 分数
     */
    private static int score(String token) {
        if (!hasText(token)) {
            return 0;
        }
        if (containsStructuredSignal(token)) {
            return 500 + Math.min(token.length(), 80);
        }
        if (isNumeric(token)) {
            return token.length() >= 2 ? 420 + Math.min(token.length(), 40) : 80;
        }
        if (isAsciiToken(token)) {
            int baseScore = token.length() >= 4 ? 360 : 120;
            return baseScore + Math.min(token.length(), 40);
        }
        if (isCjkToken(token)) {
            return token.length() >= 2 ? 220 + Math.min(token.length(), 20) : 0;
        }
        return token.length() >= 3 ? 100 + Math.min(token.length(), 20) : 0;
    }

    /**
     * 判断 token 是否包含结构化标识符信号。
     *
     * @param token token
     * @return 是否包含
     */
    private static boolean containsStructuredSignal(String token) {
        return token.indexOf('/') >= 0
                || token.indexOf('.') >= 0
                || token.indexOf('_') >= 0
                || token.indexOf('-') >= 0
                || token.indexOf('=') >= 0;
    }

    /**
     * 判断 token 是否为数字。
     *
     * @param token token
     * @return 是否数字
     */
    private static boolean isNumeric(String token) {
        for (int index = 0; index < token.length(); index++) {
            if (!Character.isDigit(token.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断 token 是否为 ASCII 字母数字串。
     *
     * @param token token
     * @return 是否 ASCII 字母数字串
     */
    private static boolean isAsciiToken(String token) {
        for (int index = 0; index < token.length(); index++) {
            char ch = token.charAt(index);
            if (!(ch >= 'a' && ch <= 'z') && !(ch >= '0' && ch <= '9')) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断 token 是否全部为中文字符。
     *
     * @param token token
     * @return 是否中文 token
     */
    private static boolean isCjkToken(String token) {
        for (int index = 0; index < token.length(); index++) {
            if (!Character.UnicodeScript.HAN.equals(Character.UnicodeScript.of(token.charAt(index)))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断文本是否有内容。
     *
     * @param value 文本
     * @return 是否有内容
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 带排名信息的 token。
     *
     * @param token token
     * @param index 原始顺序
     * @param score 形态分
     */
    private static final class RankedToken {

        private final String token;

        private final int index;

        private final int score;

        /**
         * 创建排名 token。
         *
         * @param token token
         * @param index 原始顺序
         * @param score 形态分
         */
        private RankedToken(String token, int index, int score) {
            this.token = token;
            this.index = index;
            this.score = score;
        }

        /**
         * 返回 token。
         *
         * @return token
         */
        private String token() {
            return token;
        }

        /**
         * 返回原始顺序。
         *
         * @return 原始顺序
         */
        private int index() {
            return index;
        }

        /**
         * 返回形态分。
         *
         * @return 形态分
         */
        private int score() {
            return score;
        }
    }
}
