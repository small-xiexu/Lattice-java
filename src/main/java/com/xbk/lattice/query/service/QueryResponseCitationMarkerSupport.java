package com.xbk.lattice.query.service;

import com.xbk.lattice.api.query.QueryArticleResponse;
import com.xbk.lattice.api.query.QueryCitationMarkerResponse;
import com.xbk.lattice.api.query.QueryCitationSourceResponse;
import com.xbk.lattice.api.query.QuerySourceResponse;
import com.xbk.lattice.query.citation.Citation;
import com.xbk.lattice.query.citation.CitationCheckReport;
import com.xbk.lattice.query.citation.CitationSourceType;
import com.xbk.lattice.query.citation.CitationValidationResult;
import com.xbk.lattice.query.citation.ClaimSegment;
import com.xbk.lattice.query.evidence.domain.AnswerProjection;
import com.xbk.lattice.query.evidence.domain.AnswerProjectionBundle;
import com.xbk.lattice.query.evidence.domain.ProjectionCitationFormat;
import com.xbk.lattice.query.evidence.domain.ProjectionStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Query 响应引用 marker 装配支持。
 *
 * 职责：解析答案正文中的 citation literal 与连续引用组。
 *
 * @author xiexu
 */
abstract class QueryResponseCitationMarkerSupport extends QueryResponseCitationProjectionSupport {

    /**
     * 解析对外用于替换的 citation literal。
     *
     * @param citation 答案中的 citation
     * @param answerProjection projection
     * @return citation literal
     */
    protected static String resolveCitationLiteral(Citation citation, AnswerProjection answerProjection) {
        if (answerProjection != null && !isBlank(answerProjection.getCitationLiteral())) {
            return answerProjection.getCitationLiteral().trim();
        }
        return citation == null || citation.getLiteral() == null ? "" : citation.getLiteral().trim();
    }
    /**
     * 从原段落中解析连续引用组，保留引用之间的空格。
     *
     * @param paragraphText 原段落
     * @param citationLiterals citation literal 列表
     * @return 原段落中的引用组，无法定位时返回直接拼接值
     */
    protected static String resolveCitationGroupLiteral(String paragraphText, List<String> citationLiterals) {
        List<String> safeCitationLiterals = citationLiterals == null ? List.of() : citationLiterals;
        String fallbackLiteral = String.join("", safeCitationLiterals);
        String exactGroupLiteral = findExactCitationGroupLiteral(paragraphText, safeCitationLiterals);
        if (!isBlank(exactGroupLiteral)) {
            return exactGroupLiteral;
        }
        if (isBlank(paragraphText) || safeCitationLiterals.isEmpty()) {
            return fallbackLiteral;
        }
        int groupStart = -1;
        int searchFrom = 0;
        int groupEnd = -1;
        for (String citationLiteral : safeCitationLiterals) {
            if (isBlank(citationLiteral)) {
                continue;
            }
            CitationLiteralMatch literalMatch = findCitationLiteralMatch(paragraphText, citationLiteral, searchFrom);
            if (literalMatch == null) {
                return fallbackLiteral;
            }
            if (groupStart < 0) {
                groupStart = literalMatch.getStartIndex();
            }
            groupEnd = literalMatch.getEndIndex();
            searchFrom = groupEnd;
        }
        if (groupStart < 0 || groupEnd < groupStart) {
            return fallbackLiteral;
        }
        return paragraphText.substring(groupStart, groupEnd);
    }
    /**
     * 优先查找真实答案里连续出现的完整引用组。
     *
     * <p>模型有时会生成“规范化 literal + 更长源文件引用说明”的组合，例如
     * {@code [[article]][→ file.md, 1.1 业务背景]}。逐个 literal 查找会先命中较短的
     * {@code [→ file.md]}，导致后缀说明残留在正文中，因此这里先尝试一次整组匹配。</p>
     *
     * @param paragraphText 原段落
     * @param citationLiterals citation literal 列表
     * @return 完整引用组
     */
    protected static String findExactCitationGroupLiteral(String paragraphText, List<String> citationLiterals) {
        if (isBlank(paragraphText) || citationLiterals == null || citationLiterals.isEmpty()) {
            return "";
        }
        CitationLiteralMatch groupMatch = findCitationGroupMatch(paragraphText, citationLiterals, 0);
        if (groupMatch == null) {
            return "";
        }
        return paragraphText.substring(groupMatch.getStartIndex(), groupMatch.getEndIndex());
    }
    /**
     * 按 literal 顺序查找连续引用组，并保留中间空白。
     *
     * @param paragraphText 原段落
     * @param citationLiterals citation literal 列表
     * @param searchFrom 查找起点
     * @return 匹配范围
     */
    protected static CitationLiteralMatch findCitationGroupMatch(
            String paragraphText,
            List<String> citationLiterals,
            int searchFrom
    ) {
        int groupStart = -1;
        int groupEnd = -1;
        int cursor = Math.max(0, searchFrom);
        for (String citationLiteral : citationLiterals) {
            if (isBlank(citationLiteral)) {
                continue;
            }
            CitationLiteralMatch literalMatch = findCitationLiteralMatch(paragraphText, citationLiteral, cursor);
            if (literalMatch == null) {
                return null;
            }
            if (groupEnd >= 0) {
                String between = paragraphText.substring(groupEnd, literalMatch.getStartIndex());
                if (!between.trim().isEmpty()) {
                    return null;
                }
            }
            if (groupStart < 0) {
                groupStart = literalMatch.getStartIndex();
            }
            groupEnd = literalMatch.getEndIndex();
            cursor = groupEnd;
        }
        if (groupStart < 0 || groupEnd < groupStart) {
            return null;
        }
        return new CitationLiteralMatch(groupStart, groupEnd);
    }
    /**
     * 从段落中查找 citation literal，兼容 SOURCE_FILE 带行号或章节说明的原文。
     *
     * @param paragraphText 原段落
     * @param citationLiteral 规范化后的 citation literal
     * @param searchFrom 查找起点
     * @return citation literal 在原段落中的范围
     */
    protected static CitationLiteralMatch findCitationLiteralMatch(
            String paragraphText,
            String citationLiteral,
            int searchFrom
    ) {
        int exactIndex = paragraphText.indexOf(citationLiteral, searchFrom);
        CitationLiteralMatch exactMatch = exactIndex < 0
                ? null
                : new CitationLiteralMatch(exactIndex, exactIndex + citationLiteral.length());
        if (!isSourceCitationLiteral(citationLiteral)) {
            return exactMatch;
        }
        CitationLiteralMatch sourceMatch = findSourceCitationLiteralMatch(paragraphText, citationLiteral, searchFrom);
        if (exactMatch == null) {
            return sourceMatch;
        }
        if (sourceMatch == null) {
            return exactMatch;
        }
        return exactMatch.getStartIndex() <= sourceMatch.getStartIndex() ? exactMatch : sourceMatch;
    }
    /**
     * 判断 citation literal 是否为源文件引用。
     *
     * @param citationLiteral citation literal
     * @return 源文件引用返回 true
     */
    protected static boolean isSourceCitationLiteral(String citationLiteral) {
        if (isBlank(citationLiteral)) {
            return false;
        }
        String normalizedLiteral = citationLiteral.trim();
        return normalizedLiteral.startsWith("[→") || normalizedLiteral.startsWith("[[→");
    }
    /**
     * 匹配 SOURCE_FILE 引用在原段落中的完整写法。
     *
     * @param paragraphText 原段落
     * @param citationLiteral 规范化后的 SOURCE_FILE citation literal
     * @param searchFrom 查找起点
     * @return 原段落中的完整 SOURCE_FILE 引用范围
     */
    protected static CitationLiteralMatch findSourceCitationLiteralMatch(
            String paragraphText,
            String citationLiteral,
            int searchFrom
    ) {
        String targetKey = normalizeSourcePath(citationLiteral);
        if (isBlank(targetKey)) {
            return null;
        }
        String quotedTargetKey = Pattern.quote(targetKey);
        Pattern sourcePattern = Pattern.compile(
                "(?:\\[\\[→\\s*" + quotedTargetKey + "(?::(?:L)?\\d+(?:-(?:L)?\\d+)?)?\\s*(?:,[^\\]]+)?]]"
                        + "|\\[→\\s*" + quotedTargetKey + "(?::(?:L)?\\d+(?:-(?:L)?\\d+)?)?\\s*(?:,[^\\]]+)?])"
        );
        Matcher matcher = sourcePattern.matcher(paragraphText);
        if (!matcher.find(Math.max(0, searchFrom))) {
            return null;
        }
        return new CitationLiteralMatch(matcher.start(), matcher.end());
    }
}
