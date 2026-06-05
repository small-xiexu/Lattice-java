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

    private static final Pattern NUMBER_TOKEN_PATTERN = Pattern.compile("(?<![A-Za-z0-9])\\d+(?![A-Za-z0-9])");

    private static final Pattern PATH_TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_./-]+\\.[A-Za-z0-9_./-]+");

    private static final Pattern URL_PATH_TOKEN_PATTERN = Pattern.compile("/[A-Za-z0-9._-]+(?:/[A-Za-z0-9._-]+)+");

    private static final Pattern CONFIG_KEY_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*(?:\\.[A-Za-z0-9_-]+)+");

    private static final Pattern CAMEL_TOKEN_PATTERN = Pattern.compile("[A-Z][A-Za-z0-9]+(?:[A-Z][A-Za-z0-9]+)+");

    private static final Pattern CAMEL_PART_PATTERN = Pattern.compile("[A-Z]?[a-z0-9]+|[A-Z]+(?=[A-Z]|$)");

    private static final Pattern HAN_TEXT_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}");

    private static final int MAX_ADJACENT_ASCII_SEGMENT_LENGTH = 4;

    private static final int MAX_ADJACENT_HAN_SEGMENT_LENGTH = 2;

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
        Matcher numberMatcher = NUMBER_TOKEN_PATTERN.matcher(normalizedQuestion);
        while (numberMatcher.find()) {
            tokens.add(numberMatcher.group());
        }
        Matcher hanMatcher = HAN_TEXT_PATTERN.matcher(question);
        while (hanMatcher.find()) {
            appendChineseTokens(tokens, hanMatcher.group());
        }
        appendMixedScriptTokens(tokens, question);
        return new ArrayList<String>(tokens);
    }

    /**
     * 提取带结构符号的精确标识 token。
     *
     * @param question 查询问题
     * @return 精确标识 token
     */
    public static List<String> extractExactIdentifierTokens(String question) {
        Set<String> exactIdentifierTokens = new LinkedHashSet<String>();
        List<String> rawTokens = extract(question);
        for (String rawToken : rawTokens) {
            if (rawToken == null || rawToken.isBlank()) {
                continue;
            }
            String normalizedToken = rawToken.toLowerCase(Locale.ROOT);
            if (containsExactIdentifierSignal(normalizedToken)) {
                exactIdentifierTokens.add(normalizedToken);
            }
        }
        return new ArrayList<String>(exactIdentifierTokens);
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
        Matcher urlPathMatcher = URL_PATH_TOKEN_PATTERN.matcher(question);
        while (urlPathMatcher.find()) {
            tokens.add(urlPathMatcher.group().toLowerCase(Locale.ROOT));
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
                tokens.add(token);
            }
        }
    }

    /**
     * 从原文中提取 Latin/数字 + Han 的短混合脚本片段。
     *
     * @param tokens token 集合
     * @param question 查询问题
     */
    private static void appendMixedScriptTokens(Set<String> tokens, String question) {
        StringBuilder segment = new StringBuilder();
        String previousSegment = "";
        boolean inDelimiterRun = false;
        boolean delimiterRunWhitespaceOnly = false;
        boolean segmentSeparatedByWhitespace = false;
        for (int offset = 0; offset < question.length(); ) {
            int codePoint = question.codePointAt(offset);
            if (isSegmentDelimiter(codePoint)) {
                if (segment.length() > 0) {
                    String currentSegment = segment.toString();
                    appendMixedScriptSegment(tokens, currentSegment);
                    appendAdjacentMixedScriptSegment(tokens, previousSegment, currentSegment,
                            segmentSeparatedByWhitespace);
                    previousSegment = currentSegment;
                    segment.setLength(0);
                }
                if (!inDelimiterRun) {
                    delimiterRunWhitespaceOnly = Character.isWhitespace(codePoint);
                    inDelimiterRun = true;
                } else {
                    delimiterRunWhitespaceOnly = delimiterRunWhitespaceOnly && Character.isWhitespace(codePoint);
                }
                offset += Character.charCount(codePoint);
                continue;
            }
            if (segment.length() == 0 && inDelimiterRun) {
                segmentSeparatedByWhitespace = delimiterRunWhitespaceOnly;
                inDelimiterRun = false;
            }
            segment.appendCodePoint(codePoint);
            offset += Character.charCount(codePoint);
        }
        if (segment.length() > 0) {
            String currentSegment = segment.toString();
            appendMixedScriptSegment(tokens, currentSegment);
            appendAdjacentMixedScriptSegment(tokens, previousSegment, currentSegment,
                    segmentSeparatedByWhitespace);
        }
    }

    /**
     * 将符合混合脚本条件的 segment 加入 token 集合。
     *
     * @param tokens token 集合
     * @param segment 原始 segment
     */
    private static void appendMixedScriptSegment(Set<String> tokens, CharSequence segment) {
        if (segment == null || segment.length() < 2) {
            return;
        }
        boolean hasLatinOrDigit = false;
        boolean hasHan = false;
        for (int offset = 0; offset < segment.length(); ) {
            int codePoint = Character.codePointAt(segment, offset);
            if (Character.isDigit(codePoint)
                    || Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN) {
                hasLatinOrDigit = true;
            }
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                hasHan = true;
            }
            offset += Character.charCount(codePoint);
        }
        if (hasLatinOrDigit && hasHan) {
            tokens.add(segment.toString().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * 将纯空白分隔的短 ASCII/数字 + 短 Han 相邻 segment 合并为混合脚本 token。
     *
     * @param tokens token 集合
     * @param leftSegment 左侧 segment
     * @param rightSegment 右侧 segment
     * @param separatedByWhitespace 两个 segment 是否由纯空白分隔
     */
    private static void appendAdjacentMixedScriptSegment(Set<String> tokens, String leftSegment,
            String rightSegment, boolean separatedByWhitespace) {
        if (!separatedByWhitespace
                || !isShortAsciiOrDigitSegment(leftSegment)
                || !isShortHanSegment(rightSegment)) {
            return;
        }
        String mergedToken = leftSegment + rightSegment;
        tokens.add(mergedToken.toLowerCase(Locale.ROOT));
    }

    /**
     * 判断左侧 segment 是否为保守短 ASCII/数字片段。
     *
     * @param segment segment
     * @return 是否短 ASCII/数字片段
     */
    private static boolean isShortAsciiOrDigitSegment(String segment) {
        if (segment == null || segment.isBlank()) {
            return false;
        }
        int codePointCount = segment.codePointCount(0, segment.length());
        if (codePointCount > MAX_ADJACENT_ASCII_SEGMENT_LENGTH) {
            return false;
        }
        boolean hasLatinOrDigit = false;
        for (int offset = 0; offset < segment.length(); ) {
            int codePoint = segment.codePointAt(offset);
            if (!Character.isDigit(codePoint)
                    && Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.LATIN) {
                return false;
            }
            hasLatinOrDigit = true;
            offset += Character.charCount(codePoint);
        }
        return hasLatinOrDigit;
    }

    /**
     * 判断右侧 segment 是否为短 Han 片段。
     *
     * @param segment segment
     * @return 是否短 Han 片段
     */
    private static boolean isShortHanSegment(String segment) {
        if (segment == null || segment.isBlank()) {
            return false;
        }
        int codePointCount = segment.codePointCount(0, segment.length());
        if (codePointCount > MAX_ADJACENT_HAN_SEGMENT_LENGTH) {
            return false;
        }
        boolean hasHan = false;
        for (int offset = 0; offset < segment.length(); ) {
            int codePoint = segment.codePointAt(offset);
            if (Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.HAN) {
                return false;
            }
            hasHan = true;
            offset += Character.charCount(codePoint);
        }
        return hasHan;
    }

    /**
     * 判断字符是否用于切分 query segment。
     *
     * @param codePoint Unicode code point
     * @return 是否切分
     */
    private static boolean isSegmentDelimiter(int codePoint) {
        if (Character.isWhitespace(codePoint)) {
            return true;
        }
        int type = Character.getType(codePoint);
        return type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION;
    }

    /**
     * 判断 token 是否包含精确标识符结构信号。
     *
     * @param token token
     * @return 包含返回 true
     */
    private static boolean containsExactIdentifierSignal(String token) {
        return token != null
                && (token.contains("_")
                || token.contains("-")
                || token.contains("=")
                || token.contains("/")
                || token.contains("."));
    }

}
