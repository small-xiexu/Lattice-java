package com.xbk.lattice.compiler.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        if (content == null || content.isBlank()) {
            return "";
        }
        if (content.length() <= maxChars) {
            return content;
        }
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
        String selected = matchedSections.isEmpty() ? content : String.join("\n\n", matchedSections);
        if (selected.length() <= maxChars) {
            return selected;
        }
        return selected.substring(0, maxChars);
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
            if (conceptTerm == null || conceptTerm.isBlank()) {
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
        if (content == null || content.isBlank()) {
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
            if (item.heading().equalsIgnoreCase(normalizeHeading(heading))) {
                target = item;
                break;
            }
        }
        if (target == null) {
            return new DocumentSection(normalizeHeading(heading), "", 0);
        }
        String[] lines = content.split("\\R", -1);
        int startIndex = Math.max(target.line() - 1, 0);
        int endIndex = lines.length;
        for (DocumentHeading item : headings) {
            if (item.line() > target.line()) {
                endIndex = item.line() - 1;
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
        return new DocumentSection(target.heading(), builder.toString(), target.line());
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

    /**
     * 文档标题项。
     *
     * @param heading 标题
     * @param level 标题层级
     * @param line 行号
     */
    public record DocumentHeading(String heading, int level, int line) {
    }

    /**
     * 文档章节读取结果。
     *
     * @param heading 标题
     * @param content 章节内容
     * @param line 标题起始行号
     */
    public record DocumentSection(String heading, String content, int line) {
    }
}
