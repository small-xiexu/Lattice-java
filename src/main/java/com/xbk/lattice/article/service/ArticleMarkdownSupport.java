package com.xbk.lattice.article.service;

import com.fasterxml.jackson.databind.ObjectMapper;

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
        Matcher matcher = FRONTMATTER_PATTERN.matcher(markdownContent.trim());
        if (!matcher.find()) {
            return ParsedFrontmatter.empty();
        }
        Map<String, String> values = new LinkedHashMap<String, String>();
        String[] lines = matcher.group(1).split("\\R");
        for (String line : lines) {
            int separatorIndex = line.indexOf(':');
            if (separatorIndex < 0) {
                continue;
            }
            String key = line.substring(0, separatorIndex).trim().toLowerCase();
            String value = line.substring(separatorIndex + 1).trim();
            values.put(key, value);
        }
        return new ParsedFrontmatter(
                stripQuotes(values.get("title")),
                stripQuotes(values.get("summary")),
                parseYamlList(values.get("referential_keywords")),
                resolveSourcePaths(values),
                parseYamlList(values.get("depends_on")),
                parseYamlList(values.get("related")),
                stripQuotes(values.get("confidence")),
                stripQuotes(values.get("review_status")),
                parseOffsetDateTime(stripQuotes(values.get("compiled_at")))
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
        Matcher matcher = FRONTMATTER_PATTERN.matcher(markdownContent.trim());
        if (!matcher.matches()) {
            return markdownContent;
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

    private static List<String> resolveSourcePaths(Map<String, String> values) {
        List<String> sources = parseYamlList(values.get("sources"));
        if (!sources.isEmpty()) {
            return sources;
        }
        return parseYamlList(values.get("source_paths"));
    }

    private static List<String> parseYamlList(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }
        String trimmedValue = rawValue.trim();
        if ("[]".equals(trimmedValue)) {
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
            String normalized = stripQuotes(token.trim());
            if (!normalized.isBlank()) {
                values.add(normalized);
            }
        }
        return values;
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
     * frontmatter 解析结果。
     */
    public static final class ParsedFrontmatter {

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
            return new ParsedFrontmatter("", "", List.of(), List.of(), List.of(), List.of(), "", "", null);
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
