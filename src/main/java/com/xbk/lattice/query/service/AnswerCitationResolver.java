package com.xbk.lattice.query.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 答案引用解析器
 *
 * 职责：解析、匹配与清理答案中的 article/source citation
 *
 * 不属于本类的事：不选择答案行引用、不修改 Markdown 正文结构
 *
 * @author xiexu
 */
final class AnswerCitationResolver {

    private static final Pattern ARTICLE_CITATION_PATTERN = Pattern.compile("\\[\\[[^\\]]+]]");

    private static final Pattern SOURCE_CITATION_PATTERN = Pattern.compile("\\[→\\s*[^\\]]+]");

    /**
     * 判断答案中是否仍保留至少一个可解析 citation。
     *
     * @param answerMarkdown 答案正文
     * @return 含 citation 返回 true
     */
    boolean containsCitationLiteral(String answerMarkdown) {
        if (answerMarkdown == null || answerMarkdown.isBlank()) {
            return false;
        }
        return ARTICLE_CITATION_PATTERN.matcher(answerMarkdown).find()
                || SOURCE_CITATION_PATTERN.matcher(answerMarkdown).find();
    }

    /**
     * 判断文本中是否包含源文件式 citation。
     *
     * @param markdown Markdown 文本
     * @return 包含 source citation 返回 true
     */
    boolean containsSourceCitationLiteral(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return false;
        }
        return SOURCE_CITATION_PATTERN.matcher(markdown).find();
    }

    /**
     * 去掉片段里原本夹带的 citation，避免 fallback 再追加标准引用时重复。
     *
     * @param snippet 原始片段
     * @return 去除内嵌 citation 后的片段
     */
    String stripEmbeddedCitationLiterals(String snippet) {
        if (snippet == null || snippet.isBlank()) {
            return "";
        }
        String normalizedSnippet = ARTICLE_CITATION_PATTERN.matcher(snippet).replaceAll("");
        normalizedSnippet = SOURCE_CITATION_PATTERN.matcher(normalizedSnippet).replaceAll("");
        normalizedSnippet = normalizedSnippet.replaceAll("\\s{2,}", " ").trim();
        normalizedSnippet = normalizedSnippet.replaceAll("\\s+([，。；：])", "$1");
        return normalizedSnippet;
    }

    /**
     * 解析单条证据对应的标准引用文本。
     *
     * @param queryArticleHit 证据命中
     * @return 引用文本
     */
    String resolveCitationLiteral(QueryArticleHit queryArticleHit) {
        String articleCitationLiteral = resolveArticleCitationLiteral(queryArticleHit);
        String sourceCitationLiteral = resolveSourceCitationLiteral(queryArticleHit);
        if (!articleCitationLiteral.isBlank() && !sourceCitationLiteral.isBlank()) {
            return articleCitationLiteral + sourceCitationLiteral;
        }
        if (!articleCitationLiteral.isBlank()) {
            return articleCitationLiteral;
        }
        return sourceCitationLiteral;
    }

    /**
     * 解析证据对应的引用文本，并让结构化卡片优先回落到同源 source chunk。
     *
     * @param queryArticleHit 证据命中
     * @param candidateHits 同批候选命中
     * @return 引用文本
     */
    String resolveCitationLiteral(QueryArticleHit queryArticleHit, List<QueryArticleHit> candidateHits) {
        if (queryArticleHit != null && queryArticleHit.getEvidenceType() == QueryEvidenceType.FACT_CARD) {
            String sourceCitationLiteral = resolveFactCardSourceCitationLiteral(queryArticleHit, candidateHits);
            if (!sourceCitationLiteral.isBlank()) {
                return sourceCitationLiteral;
            }
        }
        return resolveCitationLiteral(queryArticleHit);
    }

    /**
     * 为结论段挑选更稳定的 citation 形式。
     *
     * @param queryArticleHit 证据命中
     * @return citation 文本
     */
    String resolveConclusionCitationLiteral(QueryArticleHit queryArticleHit) {
        return resolveConclusionCitationLiteral(queryArticleHit, List.of());
    }

    /**
     * 为结论段挑选更稳定的 citation 形式，fact card 优先引用同源 source chunk。
     *
     * @param queryArticleHit 证据命中
     * @param candidateHits 同批候选命中
     * @return citation 文本
     */
    String resolveConclusionCitationLiteral(QueryArticleHit queryArticleHit, List<QueryArticleHit> candidateHits) {
        if (queryArticleHit == null) {
            return "";
        }
        if (queryArticleHit.getEvidenceType() == QueryEvidenceType.FACT_CARD) {
            String sourceCitationLiteral = resolveFactCardSourceCitationLiteral(queryArticleHit, candidateHits);
            if (!sourceCitationLiteral.isBlank()) {
                return sourceCitationLiteral;
            }
        }
        if (queryArticleHit.getEvidenceType() == QueryEvidenceType.ARTICLE) {
            String articleCitationLiteral = resolveArticleCitationLiteral(queryArticleHit);
            if (!articleCitationLiteral.isBlank()) {
                return articleCitationLiteral;
            }
        }
        String sourceCitationLiteral = resolveSourceCitationLiteral(queryArticleHit);
        if (!sourceCitationLiteral.isBlank()) {
            return sourceCitationLiteral;
        }
        return resolveArticleCitationLiteral(queryArticleHit);
    }

    /**
     * 判断 citation 是否指向指定证据。
     *
     * @param citationLiteral citation 文本
     * @param queryArticleHit 证据命中
     * @return 匹配返回 true
     */
    boolean citationLiteralMatchesHit(String citationLiteral, QueryArticleHit queryArticleHit) {
        if (citationLiteral == null || citationLiteral.isBlank() || queryArticleHit == null) {
            return false;
        }
        String articleTarget = extractArticleCitationTarget(citationLiteral);
        if (!articleTarget.isBlank()
                && (articleTarget.equals(queryArticleHit.getArticleKey())
                || articleTarget.equals(queryArticleHit.getConceptId()))) {
            return true;
        }
        String sourceTarget = extractSourceCitationTarget(citationLiteral);
        if (sourceTarget.isBlank() || queryArticleHit.getSourcePaths() == null) {
            return false;
        }
        for (String sourcePath : queryArticleHit.getSourcePaths()) {
            if (normalizeSourceCitationTarget(sourcePath).equals(sourceTarget)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 规范化 source citation 目标。
     *
     * @param sourcePath source 路径
     * @return 规范化路径
     */
    String normalizeSourceCitationTarget(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return "";
        }
        String normalizedSourcePath = sourcePath.trim();
        if (normalizedSourcePath.startsWith("[") && normalizedSourcePath.endsWith("]")) {
            normalizedSourcePath = normalizedSourcePath.substring(1, normalizedSourcePath.length() - 1).trim();
        }
        int commaIndex = normalizedSourcePath.indexOf(',');
        if (commaIndex > 0) {
            normalizedSourcePath = normalizedSourcePath.substring(0, commaIndex).trim();
        }
        return normalizedSourcePath;
    }

    private String resolveFactCardSourceCitationLiteral(
            QueryArticleHit factCardHit,
            List<QueryArticleHit> candidateHits
    ) {
        QueryArticleHit sourceHit = findFactCardSourceHit(factCardHit, candidateHits);
        String sourceCitationLiteral = resolveSourceCitationLiteral(sourceHit);
        if (!sourceCitationLiteral.isBlank()) {
            return sourceCitationLiteral;
        }
        return resolveSourceCitationLiteral(factCardHit);
    }

    private QueryArticleHit findFactCardSourceHit(QueryArticleHit factCardHit, List<QueryArticleHit> candidateHits) {
        if (factCardHit == null || candidateHits == null || candidateHits.isEmpty()) {
            return null;
        }
        QueryArticleHit bestHit = null;
        int bestScore = Integer.MIN_VALUE;
        for (QueryArticleHit candidateHit : candidateHits) {
            if (candidateHit == null || candidateHit.getEvidenceType() != QueryEvidenceType.SOURCE) {
                continue;
            }
            int score = scoreFactCardSourceHit(factCardHit, candidateHit);
            if (score > bestScore) {
                bestScore = score;
                bestHit = candidateHit;
            }
        }
        return bestScore > 0 ? bestHit : null;
    }

    private int scoreFactCardSourceHit(QueryArticleHit factCardHit, QueryArticleHit sourceHit) {
        int score = 0;
        if (hasSharedSourcePath(factCardHit, sourceHit)) {
            score += 24;
        }
        List<String> factTokens = QueryTokenExtractor.extract(joinHitText(factCardHit));
        String sourceHaystack = lowerCase(joinHitText(sourceHit));
        for (String factToken : factTokens) {
            String normalizedToken = lowerCase(factToken);
            if (normalizedToken.length() >= 2 && sourceHaystack.contains(normalizedToken)) {
                score += normalizedToken.matches(".*[0-9=_./-].*") ? 8 : 3;
            }
        }
        return score;
    }

    private String joinHitText(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return "";
        }
        return lowerCase(queryArticleHit.getTitle()) + " " + lowerCase(queryArticleHit.getContent());
    }

    private boolean hasSharedSourcePath(QueryArticleHit leftHit, QueryArticleHit rightHit) {
        if (leftHit == null
                || rightHit == null
                || leftHit.getSourcePaths() == null
                || rightHit.getSourcePaths() == null) {
            return false;
        }
        for (String leftPath : leftHit.getSourcePaths()) {
            String normalizedLeftPath = normalizeSourceCitationTarget(leftPath);
            if (normalizedLeftPath.isBlank()) {
                continue;
            }
            for (String rightPath : rightHit.getSourcePaths()) {
                if (normalizedLeftPath.equals(normalizeSourceCitationTarget(rightPath))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String resolveArticleCitationLiteral(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null || queryArticleHit.getEvidenceType() != QueryEvidenceType.ARTICLE) {
            return "";
        }
        String articleKey = queryArticleHit.getArticleKey();
        if (articleKey == null || articleKey.isBlank()) {
            articleKey = queryArticleHit.getConceptId();
        }
        if (articleKey == null || articleKey.isBlank()) {
            return "";
        }
        return "[[" + articleKey + "]]";
    }

    private String resolveSourceCitationLiteral(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null
                || queryArticleHit.getSourcePaths() == null
                || queryArticleHit.getSourcePaths().isEmpty()) {
            return "";
        }
        String sourcePath = queryArticleHit.getSourcePaths().get(0);
        if (sourcePath == null || sourcePath.isBlank()) {
            return "";
        }
        String normalizedSourcePath = sourcePath.trim();
        if (normalizedSourcePath.startsWith("[") && normalizedSourcePath.endsWith("]")) {
            normalizedSourcePath = normalizedSourcePath.substring(1, normalizedSourcePath.length() - 1).trim();
        }
        if (normalizedSourcePath.isBlank()) {
            return "";
        }
        return "[→ " + normalizedSourcePath + "]";
    }

    private String extractArticleCitationTarget(String citationLiteral) {
        if (citationLiteral == null || !citationLiteral.startsWith("[[") || citationLiteral.startsWith("[[→")) {
            return "";
        }
        int endIndex = citationLiteral.indexOf("]]");
        if (endIndex < 0) {
            return "";
        }
        String target = citationLiteral.substring(2, endIndex);
        int labelIndex = target.indexOf('|');
        if (labelIndex >= 0) {
            target = target.substring(0, labelIndex);
        }
        return target.trim();
    }

    private String extractSourceCitationTarget(String citationLiteral) {
        if (citationLiteral == null || citationLiteral.isBlank()) {
            return "";
        }
        String target = "";
        if (citationLiteral.startsWith("[→")) {
            target = citationLiteral.substring(2, citationLiteral.length() - 1);
        }
        else if (citationLiteral.startsWith("[[→")) {
            target = citationLiteral.substring(3, citationLiteral.length() - 2);
        }
        return normalizeSourceCitationTarget(target);
    }

    private String lowerCase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
