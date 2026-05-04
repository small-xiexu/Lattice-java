package com.xbk.lattice.compiler.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档章节选择器
 *
 * 职责：根据概念关键词从长文本中选择更相关的内容片段
 *
 * @author xiexu
 */
public class DocumentSectionSelector {

    private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");

    private static final Pattern LEGACY_HEADING_PATTERN = Pattern.compile("^===\\s+(.+?)\\s*$");

    /**
     * 选择与概念相关的内容片段。
     *
     * @param content 原始内容
     * @param conceptTerms 概念关键词
     * @param maxChars 最大字符数
     * @return 选出的内容片段
     */
    public String select(String content, List<String> conceptTerms, int maxChars) {
        if (isBlank(content)) {
            return "";
        }
        if (content.length() <= maxChars) {
            return content;
        }
        List<DocumentHeading> headings = toc(content);
        List<String> matchedSections = collectMatchedMarkdownSections(content, conceptTerms, headings);
        if (matchedSections.isEmpty()) {
            matchedSections = collectMatchedLegacySections(content, conceptTerms);
        }
        String selected = matchedSections.isEmpty() ? content : String.join("\n\n", matchedSections);
        if (selected.length() <= maxChars) {
            return selected;
        }
        return selected.substring(0, maxChars);
    }

    /**
     * 收集命中概念关键词的 Markdown 章节。
     *
     * @param content 原始内容
     * @param conceptTerms 概念关键词
     * @param headings 章节目录
     * @return 命中的章节正文
     */
    private List<String> collectMatchedMarkdownSections(
            String content,
            List<String> conceptTerms,
            List<DocumentHeading> headings
    ) {
        List<String> matchedSections = new ArrayList<String>();
        for (DocumentHeading heading : headings) {
            DocumentSection documentSection = readSection(content, heading.getHeading());
            String normalizedContent = documentSection.getContent().trim();
            if (normalizedContent.isEmpty()) {
                continue;
            }
            String lowercaseSection = normalizedContent.toLowerCase(Locale.ROOT);
            if (matchesAny(lowercaseSection, conceptTerms)) {
                matchedSections.add(normalizedContent);
            }
        }
        return matchedSections;
    }

    /**
     * 收集命中概念关键词的旧版分隔章节。
     *
     * @param content 原始内容
     * @param conceptTerms 概念关键词
     * @return 命中的章节正文
     */
    private List<String> collectMatchedLegacySections(String content, List<String> conceptTerms) {
        String[] sections = content.split("(?m)^=== ");
        List<String> matchedSections = new ArrayList<String>();
        for (String section : sections) {
            String normalizedSection = section.trim();
            if (normalizedSection.isEmpty()) {
                continue;
            }
            String lowercaseSection = normalizedSection.toLowerCase(Locale.ROOT);
            if (matchesAny(lowercaseSection, conceptTerms)) {
                matchedSections.add("=== " + normalizedSection);
            }
        }
        return matchedSections;
    }

    /**
     * 判断文本是否命中任一概念关键词。
     *
     * @param lowercaseSection 小写文本
     * @param conceptTerms 概念关键词
     * @return 是否命中
     */
    private boolean matchesAny(String lowercaseSection, List<String> conceptTerms) {
        for (String conceptTerm : conceptTerms) {
            if (isBlank(conceptTerm)) {
                continue;
            }
            if (lowercaseSection.contains(conceptTerm.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析文档目录。
     *
     * @param content 文档正文
     * @return 章节目录
     */
    public List<DocumentHeading> toc(String content) {
        List<DocumentHeading> headings = new ArrayList<DocumentHeading>();
        if (isBlank(content)) {
            return headings;
        }
        String[] lines = content.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            DocumentHeading heading = parseHeading(lines[index], index + 1);
            if (heading != null) {
                headings.add(heading);
            }
        }
        return headings;
    }

    /**
     * 按标题读取章节。
     *
     * @param content 文档正文
     * @param heading 标题
     * @return 章节读取结果
     */
    public DocumentSection readSection(String content, String heading) {
        List<DocumentHeading> headings = toc(content);
        if (headings.isEmpty()) {
            return new DocumentSection(normalizeHeading(heading), "", 0);
        }
        DocumentHeading target = null;
        for (DocumentHeading item : headings) {
            if (item.getHeading().equalsIgnoreCase(normalizeHeading(heading))) {
                target = item;
                break;
            }
        }
        if (target == null) {
            return new DocumentSection(normalizeHeading(heading), "", 0);
        }
        String[] lines = content.split("\\R", -1);
        int startIndex = Math.max(target.getLine() - 1, 0);
        int endIndex = lines.length;
        for (DocumentHeading item : headings) {
            if (item.getLine() > target.getLine() && item.getLevel() <= target.getLevel()) {
                endIndex = item.getLine() - 1;
                break;
            }
        }
        StringBuilder builder = new StringBuilder();
        for (int index = startIndex; index < endIndex; index++) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(lines[index]);
        }
        return new DocumentSection(target.getHeading(), builder.toString(), target.getLine());
    }

    private DocumentHeading parseHeading(String line, int lineNumber) {
        Matcher markdownMatcher = MARKDOWN_HEADING_PATTERN.matcher(line);
        if (markdownMatcher.matches()) {
            return new DocumentHeading(
                    normalizeHeading(markdownMatcher.group(2)),
                    markdownMatcher.group(1).length(),
                    lineNumber
            );
        }
        Matcher legacyMatcher = LEGACY_HEADING_PATTERN.matcher(line);
        if (legacyMatcher.matches()) {
            return new DocumentHeading(normalizeHeading(legacyMatcher.group(1)), 1, lineNumber);
        }
        return null;
    }

    private String normalizeHeading(String heading) {
        if (heading == null) {
            return "";
        }
        return heading.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 文档标题项。
     *
     * 职责：承载标题文本、层级与行号
     *
     * @author xiexu
     */
    public static final class DocumentHeading {

        private final String heading;

        private final int level;

        private final int line;

        /**
         * 创建文档标题项。
         *
         * @param heading 标题
         * @param level 标题层级
         * @param line 行号
         */
        public DocumentHeading(String heading, int level, int line) {
            this.heading = heading;
            this.level = level;
            this.line = line;
        }

        /**
         * 返回标题文本。
         *
         * @return 标题文本
         */
        public String getHeading() {
            return heading;
        }

        /**
         * 返回标题层级。
         *
         * @return 标题层级
         */
        public int getLevel() {
            return level;
        }

        /**
         * 返回标题所在行号。
         *
         * @return 行号
         */
        public int getLine() {
            return line;
        }

        /**
         * 比较标题项是否相等。
         *
         * @param other 另一对象
         * @return 是否相等
         */
        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof DocumentHeading)) {
                return false;
            }
            DocumentHeading that = (DocumentHeading) other;
            return level == that.level
                    && line == that.line
                    && Objects.equals(heading, that.heading);
        }

        /**
         * 返回哈希值。
         *
         * @return 哈希值
         */
        @Override
        public int hashCode() {
            return Objects.hash(heading, level, line);
        }

        /**
         * 返回标题项描述。
         *
         * @return 标题项描述
         */
        @Override
        public String toString() {
            return "DocumentHeading{"
                    + "heading='" + heading + '\''
                    + ", level=" + level
                    + ", line=" + line
                    + '}';
        }
    }

    /**
     * 文档章节读取结果。
     *
     * 职责：承载章节标题、正文与起始行号
     *
     * @author xiexu
     */
    public static final class DocumentSection {

        private final String heading;

        private final String content;

        private final int line;

        /**
         * 创建文档章节读取结果。
         *
         * @param heading 标题
         * @param content 章节内容
         * @param line 标题起始行号
         */
        public DocumentSection(String heading, String content, int line) {
            this.heading = heading;
            this.content = content;
            this.line = line;
        }

        /**
         * 返回章节标题。
         *
         * @return 章节标题
         */
        public String getHeading() {
            return heading;
        }

        /**
         * 返回章节正文。
         *
         * @return 章节正文
         */
        public String getContent() {
            return content;
        }

        /**
         * 返回标题起始行号。
         *
         * @return 起始行号
         */
        public int getLine() {
            return line;
        }

        /**
         * 比较章节结果是否相等。
         *
         * @param other 另一对象
         * @return 是否相等
         */
        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof DocumentSection)) {
                return false;
            }
            DocumentSection that = (DocumentSection) other;
            return line == that.line
                    && Objects.equals(heading, that.heading)
                    && Objects.equals(content, that.content);
        }

        /**
         * 返回哈希值。
         *
         * @return 哈希值
         */
        @Override
        public int hashCode() {
            return Objects.hash(heading, content, line);
        }

        /**
         * 返回章节结果描述。
         *
         * @return 章节结果描述
         */
        @Override
        public String toString() {
            return "DocumentSection{"
                    + "heading='" + heading + '\''
                    + ", content='" + content + '\''
                    + ", line=" + line
                    + '}';
        }
    }
}
