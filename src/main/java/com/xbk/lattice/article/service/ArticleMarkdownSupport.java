package com.xbk.lattice.article.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import org.yaml.snakeyaml.Yaml;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文章 Markdown 支撑工具。
 *
 * 职责：解析知识文章 frontmatter，并在治理写回场景下统一归一关键字段
 *
 * @author xiexu
 */
public final class ArticleMarkdownSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Yaml YAML = new Yaml();

    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile("\\A---\\R(.*?)\\R---\\R?(.*)\\z", Pattern.DOTALL);

    private static final Pattern REVIEW_STATUS_PATTERN = Pattern.compile("(?m)^review_status:\\s*.*$");

    private ArticleMarkdownSupport() {
    }

    /**
     * 解析文章 Markdown 中的 frontmatter。
     *
     * @param markdownContent Markdown 内容
     * @return frontmatter 解析结果
     */
    public static ParsedFrontmatter parse(String markdownContent) {
        if (markdownContent == null || markdownContent.isBlank()) {
            return ParsedFrontmatter.empty();
        }
        String normalizedContent = normalizeGeneratedMarkdown(markdownContent);
        Matcher matcher = FRONTMATTER_PATTERN.matcher(normalizedContent.trim());
        if (!matcher.find()) {
            return ParsedFrontmatter.empty();
        }
        Map<String, Object> values = parseFrontmatterValues(matcher.group(1));
        if (values.isEmpty()) {
            return ParsedFrontmatter.empty();
        }
        return new ParsedFrontmatter(
                true,
                values.containsKey("referential_keywords"),
                values.containsKey("sources") || values.containsKey("source_paths"),
                values.containsKey("depends_on"),
                values.containsKey("related"),
                readStringValue(values.get("title")),
                readStringValue(values.get("summary")),
                readStringListValue(values.get("referential_keywords")),
                resolveSourcePaths(values),
                readStringListValue(values.get("depends_on")),
                readStringListValue(values.get("related")),
                readStringValue(values.get("confidence")),
                readStringValue(values.get("review_status")),
                parseOffsetDateTime(readStringValue(values.get("compiled_at")))
        );
    }

    /**
     * 确保 Markdown frontmatter 中的 review_status 与给定值一致。
     *
     * @param markdownContent Markdown 内容
     * @param reviewStatus 目标状态
     * @return 归一后的 Markdown
     */
    public static String normalizeReviewStatus(String markdownContent, String reviewStatus) {
        if (markdownContent == null || markdownContent.isBlank()) {
            return markdownContent;
        }
        String normalizedContent = normalizeGeneratedMarkdown(markdownContent);
        Matcher matcher = FRONTMATTER_PATTERN.matcher(normalizedContent.trim());
        if (!matcher.matches()) {
            return normalizedContent;
        }
        String frontmatter = matcher.group(1);
        String body = matcher.group(2);
        String normalizedFrontmatter;
        if (REVIEW_STATUS_PATTERN.matcher(frontmatter).find()) {
            normalizedFrontmatter = REVIEW_STATUS_PATTERN.matcher(frontmatter)
                    .replaceFirst("review_status: " + reviewStatus);
        }
        else {
            normalizedFrontmatter = frontmatter + "\nreview_status: " + reviewStatus;
        }
        return """
                ---
                %s
                ---

                %s
                """.formatted(normalizedFrontmatter, body == null ? "" : body.strip()).trim();
    }

    /**
     * 确保 Markdown frontmatter 中的 sources/source_paths 与给定来源路径一致。
     *
     * @param markdownContent Markdown 内容
     * @param sourcePaths 目标来源路径
     * @return 归一后的 Markdown
     */
    public static String normalizeSourcePaths(String markdownContent, List<String> sourcePaths) {
        if (markdownContent == null || markdownContent.isBlank()) {
            return markdownContent;
        }
        String normalizedContent = normalizeGeneratedMarkdown(markdownContent);
        Matcher matcher = FRONTMATTER_PATTERN.matcher(normalizedContent.trim());
        if (!matcher.matches()) {
            return normalizedContent;
        }
        String frontmatter = matcher.group(1);
        String body = matcher.group(2);
        String normalizedFrontmatter = rewriteSourcePathsField(frontmatter, sourcePaths);
        return """
                ---
                %s
                ---

                %s
                """.formatted(normalizedFrontmatter, body == null ? "" : body.strip()).trim();
    }

    /**
     * 提取 Markdown frontmatter 之后的正文。
     *
     * @param markdownContent Markdown 内容
     * @return frontmatter 之后的正文；无 frontmatter 时返回原文
     */
    public static String extractBody(String markdownContent) {
        if (markdownContent == null || markdownContent.isBlank()) {
            return "";
        }
        String normalizedContent = normalizeGeneratedMarkdown(markdownContent);
        Matcher matcher = FRONTMATTER_PATTERN.matcher(normalizedContent.trim());
        if (!matcher.matches()) {
            return normalizedContent.trim();
        }
        String body = matcher.group(2);
        return body == null ? "" : body.strip();
    }

    /**
     * 归一模型生成的 Markdown 文章边界。
     *
     * @param markdownContent Markdown 内容
     * @return 去除外围说明后的 Markdown 内容
     */
    public static String normalizeGeneratedMarkdown(String markdownContent) {
        if (markdownContent == null || markdownContent.isBlank()) {
            return "";
        }
        String normalizedContent = normalizeLineEndings(markdownContent).trim();
        String unwrappedContent = unwrapCodeFence(normalizedContent);
        String extractedContent = extractFromFirstValidFrontmatter(unwrappedContent);
        if (extractedContent != null) {
            return extractedContent;
        }
        extractedContent = extractFromFirstValidFrontmatter(normalizedContent);
        if (extractedContent != null) {
            return extractedContent;
        }
        extractedContent = extractFromFirstTopLevelHeading(unwrappedContent);
        if (extractedContent != null) {
            return extractedContent;
        }
        extractedContent = extractFromFirstTopLevelHeading(normalizedContent);
        if (extractedContent != null) {
            return extractedContent;
        }
        return normalizedContent;
    }

    /**
     * 基于 Markdown frontmatter 回灌文章结构化字段。
     *
     * @param articleRecord 文章记录
     * @param markdownContent Markdown 内容
     * @param fallbackReviewStatus 兜底审查状态
     * @return 结构化字段同步后的文章记录
     */
    public static ArticleRecord synchronizeArticleRecord(
            ArticleRecord articleRecord,
            String markdownContent,
            String fallbackReviewStatus
    ) {
        if (articleRecord == null) {
            return null;
        }
        String normalizedMarkdownContent = normalizeGeneratedMarkdown(markdownContent);
        ParsedFrontmatter parsedFrontmatter = parse(normalizedMarkdownContent);
        if (!parsedFrontmatter.isPresent()) {
            String normalizedReviewStatus = fallbackReviewStatus;
            if (normalizedReviewStatus == null || normalizedReviewStatus.isBlank()) {
                normalizedReviewStatus = articleRecord.getReviewStatus();
            }
            return articleRecord.copy(
                    articleRecord.getTitle(),
                    normalizedMarkdownContent,
                    articleRecord.getLifecycle(),
                    articleRecord.getCompiledAt(),
                    articleRecord.getSourcePaths(),
                    articleRecord.getMetadataJson(),
                    articleRecord.getSummary(),
                    articleRecord.getReferentialKeywords(),
                    articleRecord.getDependsOn(),
                    articleRecord.getRelated(),
                    articleRecord.getConfidence(),
                    normalizedReviewStatus
            );
        }
        String normalizedTitle = parsedFrontmatter.getTitle().isBlank()
                ? articleRecord.getTitle()
                : parsedFrontmatter.getTitle();
        String normalizedSummary = parsedFrontmatter.getSummary().isBlank()
                ? articleRecord.getSummary()
                : parsedFrontmatter.getSummary();
        List<String> normalizedSourcePaths = parsedFrontmatter.getSourcePaths().isEmpty()
                ? (parsedFrontmatter.hasSourcePathsField() ? parsedFrontmatter.getSourcePaths() : articleRecord.getSourcePaths())
                : parsedFrontmatter.getSourcePaths();
        List<String> normalizedReferentialKeywords = parsedFrontmatter.getReferentialKeywords().isEmpty()
                ? (parsedFrontmatter.hasReferentialKeywordsField()
                        ? parsedFrontmatter.getReferentialKeywords()
                        : articleRecord.getReferentialKeywords())
                : parsedFrontmatter.getReferentialKeywords();
        List<String> normalizedDependsOn = parsedFrontmatter.getDependsOn().isEmpty()
                ? (parsedFrontmatter.hasDependsOnField()
                        ? parsedFrontmatter.getDependsOn()
                        : articleRecord.getDependsOn())
                : parsedFrontmatter.getDependsOn();
        List<String> normalizedRelated = parsedFrontmatter.getRelated().isEmpty()
                ? (parsedFrontmatter.hasRelatedField()
                        ? parsedFrontmatter.getRelated()
                        : articleRecord.getRelated())
                : parsedFrontmatter.getRelated();
        String normalizedConfidence = parsedFrontmatter.getConfidence().isBlank()
                ? articleRecord.getConfidence()
                : parsedFrontmatter.getConfidence();
        String normalizedReviewStatus = parsedFrontmatter.getReviewStatus().isBlank()
                ? fallbackReviewStatus
                : parsedFrontmatter.getReviewStatus();
        if (normalizedReviewStatus == null || normalizedReviewStatus.isBlank()) {
            normalizedReviewStatus = articleRecord.getReviewStatus();
        }
        OffsetDateTime normalizedCompiledAt = parsedFrontmatter.getCompiledAt() == null
                ? articleRecord.getCompiledAt()
                : parsedFrontmatter.getCompiledAt();
        return articleRecord.copy(
                normalizedTitle,
                normalizedMarkdownContent,
                articleRecord.getLifecycle(),
                normalizedCompiledAt,
                normalizedSourcePaths,
                articleRecord.getMetadataJson(),
                normalizedSummary,
                normalizedReferentialKeywords,
                normalizedDependsOn,
                normalizedRelated,
                normalizedConfidence,
                normalizedReviewStatus
        );
    }

    private static Map<String, Object> parseFrontmatterValues(String rawFrontmatter) {
        if (rawFrontmatter == null || rawFrontmatter.isBlank()) {
            return Map.of();
        }
        try {
            Object parsed = YAML.load(rawFrontmatter);
            if (!(parsed instanceof Map<?, ?>)) {
                return Map.of();
            }
            Map<?, ?> parsedMap = (Map<?, ?>) parsed;
            Map<String, Object> normalizedValues = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : parsedMap.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                normalizedValues.put(String.valueOf(entry.getKey()).trim().toLowerCase(), entry.getValue());
            }
            return normalizedValues;
        }
        catch (Exception ignored) {
            return Map.of();
        }
    }

    /**
     * 从文本中提取第一个合法 frontmatter 开始的文章。
     *
     * @param value 原始文本
     * @return 文章 Markdown；未找到时返回 null
     */
    private static String extractFromFirstValidFrontmatter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        List<String> lines = splitLines(value);
        for (int index = 0; index < lines.size(); index++) {
            if (!"---".equals(lines.get(index).trim())) {
                continue;
            }
            List<String> candidateLines = lines.subList(index, lines.size());
            String candidate = String.join("\n", candidateLines).trim();
            candidate = trimTrailingCodeFence(candidate);
            if (hasArticleFrontmatter(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 从第一个顶级标题开始提取 Markdown 正文。
     *
     * @param value 原始文本
     * @return Markdown 正文；未找到时返回 null
     */
    private static String extractFromFirstTopLevelHeading(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        List<String> lines = splitLines(value);
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).trim();
            if (!line.startsWith("# ") || line.startsWith("## ")) {
                continue;
            }
            if (!isPreambleLikelyModelMetadata(lines.subList(0, index))) {
                return null;
            }
            String candidate = String.join("\n", lines.subList(index, lines.size())).trim();
            candidate = trimTrailingCodeFence(candidate);
            return candidate.isBlank() ? null : candidate;
        }
        return null;
    }

    /**
     * 判断标题前内容是否更像模型元说明而不是文章正文。
     *
     * @param preambleLines 标题前文本行
     * @return 可安全剥离时返回 true
     */
    private static boolean isPreambleLikelyModelMetadata(List<String> preambleLines) {
        if (preambleLines == null || preambleLines.isEmpty()) {
            return false;
        }
        int nonBlankCount = 0;
        int characterCount = 0;
        for (String preambleLine : preambleLines) {
            String trimmedLine = preambleLine == null ? "" : preambleLine.trim();
            if (trimmedLine.isBlank() || "---".equals(trimmedLine) || trimmedLine.startsWith("```")) {
                continue;
            }
            if (trimmedLine.startsWith("#")) {
                return false;
            }
            nonBlankCount++;
            characterCount += trimmedLine.length();
        }
        return nonBlankCount > 0 && characterCount <= 1200;
    }

    /**
     * 判断文本是否以可识别文章 frontmatter 开始。
     *
     * @param value 候选 Markdown
     * @return 是文章 frontmatter 时返回 true
     */
    private static boolean hasArticleFrontmatter(String value) {
        Matcher matcher = FRONTMATTER_PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            return false;
        }
        String frontmatter = matcher.group(1);
        if (containsDelimiterLine(frontmatter)) {
            return false;
        }
        Map<String, Object> values = parseFrontmatterValues(frontmatter);
        return values.containsKey("title")
                || values.containsKey("summary")
                || values.containsKey("sources")
                || values.containsKey("source_paths")
                || values.containsKey("review_status");
    }

    /**
     * 判断 frontmatter 候选块内部是否含有分隔符行。
     *
     * @param frontmatter frontmatter 候选文本
     * @return 含有分隔符时返回 true
     */
    private static boolean containsDelimiterLine(String frontmatter) {
        for (String line : splitLines(frontmatter)) {
            if ("---".equals(line.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 去除包裹完整 Markdown 的代码围栏。
     *
     * @param value 原始文本
     * @return 去除外层围栏后的文本
     */
    private static String unwrapCodeFence(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        List<String> lines = splitLines(value.trim());
        if (lines.size() < 2) {
            return value.trim();
        }
        String firstLine = lines.get(0).trim();
        String lastLine = lines.get(lines.size() - 1).trim();
        if (!firstLine.startsWith("```") || !"```".equals(lastLine)) {
            return value.trim();
        }
        return String.join("\n", lines.subList(1, lines.size() - 1)).trim();
    }

    /**
     * 去除提取文章末尾残留的代码围栏结束符。
     *
     * @param value 原始文本
     * @return 去除末尾围栏后的文本
     */
    private static String trimTrailingCodeFence(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        List<String> lines = splitLines(value.trim());
        int lastIndex = lines.size() - 1;
        while (lastIndex >= 0 && lines.get(lastIndex).trim().isBlank()) {
            lastIndex--;
        }
        if (lastIndex >= 0 && "```".equals(lines.get(lastIndex).trim())) {
            return String.join("\n", lines.subList(0, lastIndex)).trim();
        }
        return value.trim();
    }

    /**
     * 归一换行符。
     *
     * @param value 原始文本
     * @return 使用 LF 的文本
     */
    private static String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static List<String> resolveSourcePaths(Map<String, Object> values) {
        List<String> sources = readStringListValue(values.get("sources"));
        if (!sources.isEmpty()) {
            return sources;
        }
        return readStringListValue(values.get("source_paths"));
    }

    private static List<String> readStringListValue(Object rawValue) {
        if (rawValue == null) {
            return List.of();
        }
        if (rawValue instanceof List<?>) {
            List<?> rawList = (List<?>) rawValue;
            List<String> values = new ArrayList<String>();
            for (Object item : rawList) {
                String normalized = readStringValue(item);
                if (!normalized.isBlank()) {
                    values.add(normalized);
                }
            }
            return values;
        }
        String trimmedValue = readStringValue(rawValue);
        if (trimmedValue.isBlank() || "[]".equals(trimmedValue)) {
            return List.of();
        }
        if (trimmedValue.startsWith("[") && trimmedValue.endsWith("]")) {
            try {
                return OBJECT_MAPPER.readValue(
                        trimmedValue,
                        OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, String.class)
                );
            }
            catch (Exception ignored) {
                // 退回到逗号分隔解析
            }
        }
        List<String> values = new ArrayList<String>();
        for (String token : trimmedValue.split(",")) {
            String normalized = readStringValue(token);
            if (!normalized.isBlank()) {
                values.add(normalized);
            }
        }
        return values;
    }

    private static String readStringValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String) {
            return stripQuotes((String) value);
        }
        return stripQuotes(String.valueOf(value));
    }

    private static OffsetDateTime parseOffsetDateTime(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(rawValue);
        }
        catch (Exception ignored) {
            return null;
        }
    }

    private static String stripQuotes(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() >= 2) {
            return normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * 重写 frontmatter 中的来源路径字段。
     *
     * @param frontmatter 原始 frontmatter
     * @param sourcePaths 目标来源路径
     * @return 重写后的 frontmatter
     */
    private static String rewriteSourcePathsField(String frontmatter, List<String> sourcePaths) {
        List<String> lines = splitLines(frontmatter);
        List<String> normalizedLines = new ArrayList<String>();
        boolean replaced = false;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            String trimmedLine = line.trim();
            if (trimmedLine.startsWith("sources:") || trimmedLine.startsWith("source_paths:")) {
                appendSourcePathsBlock(normalizedLines, sourcePaths);
                replaced = true;
                while (index + 1 < lines.size() && lines.get(index + 1).trim().startsWith("-")) {
                    index++;
                }
                continue;
            }
            normalizedLines.add(line);
        }
        if (!replaced) {
            appendSourcePathsBlock(normalizedLines, sourcePaths);
        }
        return String.join("\n", normalizedLines).trim();
    }

    /**
     * 追加来源路径 YAML 片段。
     *
     * @param normalizedLines 行集合
     * @param sourcePaths 来源路径
     */
    private static void appendSourcePathsBlock(List<String> normalizedLines, List<String> sourcePaths) {
        if (sourcePaths == null || sourcePaths.isEmpty()) {
            normalizedLines.add("sources: []");
            return;
        }
        normalizedLines.add("sources:");
        for (String sourcePath : sourcePaths) {
            if (sourcePath == null || sourcePath.isBlank()) {
                continue;
            }
            normalizedLines.add("  - \"" + sourcePath.trim() + "\"");
        }
    }

    /**
     * 按行拆分文本，兼容不同换行符。
     *
     * @param value 原始文本
     * @return 行列表
     */
    private static List<String> splitLines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String normalizedValue = normalizeLineEndings(value);
        List<String> lines = new ArrayList<String>();
        for (String line : normalizedValue.split("\n", -1)) {
            lines.add(line);
        }
        return lines;
    }

    /**
     * frontmatter 解析结果。
     */
    public static final class ParsedFrontmatter {

        private final boolean present;

        private final boolean hasReferentialKeywordsField;

        private final boolean hasSourcePathsField;

        private final boolean hasDependsOnField;

        private final boolean hasRelatedField;

        private final String title;

        private final String summary;

        private final List<String> referentialKeywords;

        private final List<String> sourcePaths;

        private final List<String> dependsOn;

        private final List<String> related;

        private final String confidence;

        private final String reviewStatus;

        private final OffsetDateTime compiledAt;

        private ParsedFrontmatter(
                boolean present,
                boolean hasReferentialKeywordsField,
                boolean hasSourcePathsField,
                boolean hasDependsOnField,
                boolean hasRelatedField,
                String title,
                String summary,
                List<String> referentialKeywords,
                List<String> sourcePaths,
                List<String> dependsOn,
                List<String> related,
                String confidence,
                String reviewStatus,
                OffsetDateTime compiledAt
        ) {
            this.present = present;
            this.hasReferentialKeywordsField = hasReferentialKeywordsField;
            this.hasSourcePathsField = hasSourcePathsField;
            this.hasDependsOnField = hasDependsOnField;
            this.hasRelatedField = hasRelatedField;
            this.title = title;
            this.summary = summary;
            this.referentialKeywords = referentialKeywords;
            this.sourcePaths = sourcePaths;
            this.dependsOn = dependsOn;
            this.related = related;
            this.confidence = confidence;
            this.reviewStatus = reviewStatus;
            this.compiledAt = compiledAt;
        }

        private static ParsedFrontmatter empty() {
            return new ParsedFrontmatter(
                    false,
                    false,
                    false,
                    false,
                    false,
                    "",
                    "",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    "",
                    "",
                    null
            );
        }

        public boolean isPresent() {
            return present;
        }

        public boolean hasReferentialKeywordsField() {
            return hasReferentialKeywordsField;
        }

        public boolean hasSourcePathsField() {
            return hasSourcePathsField;
        }

        public boolean hasDependsOnField() {
            return hasDependsOnField;
        }

        public boolean hasRelatedField() {
            return hasRelatedField;
        }

        public String getTitle() {
            return title;
        }

        public String getSummary() {
            return summary;
        }

        public List<String> getReferentialKeywords() {
            return referentialKeywords;
        }

        public List<String> getSourcePaths() {
            return sourcePaths;
        }

        public List<String> getDependsOn() {
            return dependsOn;
        }

        public List<String> getRelated() {
            return related;
        }

        public String getConfidence() {
            return confidence;
        }

        public String getReviewStatus() {
            return reviewStatus;
        }

        public OffsetDateTime getCompiledAt() {
            return compiledAt;
        }
    }
}
