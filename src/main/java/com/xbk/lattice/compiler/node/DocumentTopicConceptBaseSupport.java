package com.xbk.lattice.compiler.node;

import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.compiler.domain.AnalyzedConcept;
import com.xbk.lattice.compiler.domain.ConceptSection;
import com.xbk.lattice.compiler.domain.RawSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档专题概念基础支持。
 *
 * 职责：承载专题拆分配置、标题规则编译与底层文本工具。
 *
 * @author xiexu
 */
abstract class DocumentTopicConceptBaseSupport {

    protected static final String LEVEL_STRATEGY_MARKDOWN_PREFIX_LENGTH = "markdown-prefix-length";

    protected static final String LEVEL_STRATEGY_NUMERIC_DEPTH = "numeric-depth";

    protected final CompilerProperties.DocumentTopics documentTopics;

    protected final Pattern pageMarkerPattern;

    protected final List<CompiledHeadingPattern> headingPatterns;

    /**
     * 创建文档专题概念提取器。
     *
     * @param documentTopics 长文档专题拆分配置
     */
    protected DocumentTopicConceptBaseSupport(CompilerProperties.DocumentTopics documentTopics) {
        this.documentTopics = documentTopics == null ? new CompilerProperties.DocumentTopics() : documentTopics;
        this.pageMarkerPattern = compileOptionalPattern(this.documentTopics.getPageMarkerPattern());
        this.headingPatterns = compileHeadingPatterns(this.documentTopics.getHeadingPatterns());
    }
    /**
     * 编译可选正则。
     *
     * @param pattern 正则表达式
     * @return 已编译正则；未配置时返回空
     */
    protected Pattern compileOptionalPattern(String pattern) {
        if (isBlank(pattern)) {
            return null;
        }
        return Pattern.compile(pattern);
    }
    /**
     * 编译配置化标题识别规则。
     *
     * @param headingPatternRules 标题识别规则配置
     * @return 已编译标题识别规则
     */
    protected List<CompiledHeadingPattern> compileHeadingPatterns(
            List<CompilerProperties.HeadingPatternRule> headingPatternRules
    ) {
        List<CompiledHeadingPattern> compiledHeadingPatterns = new ArrayList<CompiledHeadingPattern>();
        if (headingPatternRules == null) {
            return compiledHeadingPatterns;
        }
        for (CompilerProperties.HeadingPatternRule headingPatternRule : headingPatternRules) {
            if (headingPatternRule == null || isBlank(headingPatternRule.getPattern())) {
                continue;
            }
            compiledHeadingPatterns.add(new CompiledHeadingPattern(
                    Pattern.compile(headingPatternRule.getPattern()),
                    Math.max(headingPatternRule.getTitleGroup(), 1),
                    Math.max(headingPatternRule.getFixedLevel(), 1),
                    Math.max(headingPatternRule.getLevelGroup(), 1),
                    headingPatternRule.getLevelStrategy()
            ));
        }
        return compiledHeadingPatterns;
    }
    /**
     * 构建概念标识。
     *
     * @param groupKey 分组键
     * @param title 专题标题
     * @param topicIndex 专题序号
     * @return 概念标识
     */
    protected String buildConceptId(String groupKey, String title, int topicIndex) {
        String normalizedGroupKey = normalizeConceptId(groupKey);
        String normalizedTitle = normalizeConceptId(title);
        if (normalizedTitle.isEmpty() || "default".equals(normalizedTitle)) {
            normalizedTitle = "topic-" + topicIndex;
        }
        if ("default".equals(normalizedGroupKey)) {
            return normalizedTitle;
        }
        return normalizedGroupKey + "-" + normalizedTitle;
    }

    /**
     * 构建专题描述。
     *
     * @param rawSource 源文件
     * @param topicSegment 专题段落
     * @return 专题描述
     */
    protected String buildDescription(RawSource rawSource, TopicSegment topicSegment) {
        StringBuilder builder = new StringBuilder();
        builder.append("从长文档 `").append(rawSource.getRelativePath()).append("` 中识别出的专题：");
        builder.append(topicSegment.title);
        if (topicSegment.pageNumber > 0) {
            builder.append("，起始页 ").append(topicSegment.pageNumber);
        }
        return builder.toString();
    }

    /**
     * 构建来源引用。
     *
     * @param rawSource 源文件
     * @param topicSegment 专题段落
     * @return 来源引用列表
     */
    protected List<String> buildSourceRefs(RawSource rawSource, TopicSegment topicSegment) {
        List<String> sourceRefs = new ArrayList<String>();
        if (topicSegment.pageNumber > 0) {
            sourceRefs.add(rawSource.getRelativePath() + "#Page " + topicSegment.pageNumber);
            return sourceRefs;
        }
        sourceRefs.add(rawSource.getRelativePath() + "#" + topicSegment.title);
        return sourceRefs;
    }

    /**
     * 把专题正文压缩成章节内容行。
     *
     * @param body 专题正文
     * @return 内容行
     */
    protected List<String> toContentLines(String body) {
        Set<String> contentLines = new LinkedHashSet<String>();
        String[] lines = body.split("\\R", -1);
        for (String line : lines) {
            String normalizedLine = normalizeTextLine(line);
            if (normalizedLine.isEmpty() || matchesPageMarker(normalizedLine)) {
                continue;
            }
            addWrappedContentLine(contentLines, normalizedLine);
            if (contentLines.size() >= documentTopics.getMaxSectionLines()) {
                break;
            }
        }
        return new ArrayList<String>(contentLines);
    }

    /**
     * 添加裁剪后的内容行。
     *
     * @param contentLines 内容行集合
     * @param normalizedLine 规范化行
     */
    protected void addWrappedContentLine(Set<String> contentLines, String normalizedLine) {
        if (normalizedLine.length() <= documentTopics.getMaxLineChars()) {
            contentLines.add(normalizedLine);
            return;
        }
        int startIndex = 0;
        while (startIndex < normalizedLine.length() && contentLines.size() < documentTopics.getMaxSectionLines()) {
            int endIndex = Math.min(startIndex + documentTopics.getMaxLineChars(), normalizedLine.length());
            contentLines.add(normalizedLine.substring(startIndex, endIndex).trim());
            startIndex = endIndex;
        }
    }

    /**
     * 计算数字标题层级。
     *
     * @param numberPrefix 数字前缀
     * @return 标题层级
     */
    protected int calculateNumericLevel(String numberPrefix) {
        if (numberPrefix == null || numberPrefix.isBlank()) {
            return 2;
        }
        return numberPrefix.split("\\.").length;
    }

    /**
     * 根据配置规则计算标题层级。
     *
     * @param headingMatcher 标题匹配结果
     * @param headingPattern 标题规则
     * @return 标题层级
     */
    protected int calculateHeadingLevel(Matcher headingMatcher, CompiledHeadingPattern headingPattern) {
        String levelValue = readMatcherGroup(headingMatcher, headingPattern.levelGroup);
        if (LEVEL_STRATEGY_MARKDOWN_PREFIX_LENGTH.equals(headingPattern.levelStrategy)) {
            return Math.max(levelValue.length(), 1);
        }
        if (LEVEL_STRATEGY_NUMERIC_DEPTH.equals(headingPattern.levelStrategy)) {
            return calculateNumericLevel(levelValue);
        }
        return headingPattern.fixedLevel;
    }

    /**
     * 读取正则匹配分组。
     *
     * @param matcher 匹配结果
     * @param groupIndex 分组下标
     * @return 分组内容
     */
    protected String readMatcherGroup(Matcher matcher, int groupIndex) {
        if (groupIndex < 1 || groupIndex > matcher.groupCount()) {
            return "";
        }
        String groupValue = matcher.group(groupIndex);
        return groupValue == null ? "" : groupValue;
    }

    /**
     * 匹配页码标记。
     *
     * @param line 单行文本
     * @return 匹配结果；未命中或未配置页码规则时返回空
     */
    protected Matcher matchPageMarker(String line) {
        if (pageMarkerPattern == null) {
            return null;
        }
        Matcher matcher = pageMarkerPattern.matcher(line);
        return matcher.matches() ? matcher : null;
    }

    /**
     * 判断是否为页码标记。
     *
     * @param line 单行文本
     * @return 命中页码标记返回 true
     */
    protected boolean matchesPageMarker(String line) {
        return matchPageMarker(line) != null;
    }

    /**
     * 从页码匹配结果中解析页码。
     *
     * @param pageMatcher 页码匹配结果
     * @return 页码；无法解析时返回 0
     */
    protected int parsePageNumber(Matcher pageMatcher) {
        String pageValue = readMatcherGroup(pageMatcher, 1);
        if (pageValue.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(pageValue);
        }
        catch (NumberFormatException exception) {
            return 0;
        }
    }

    /**
     * 标准化标题文本。
     *
     * @param title 原始标题
     * @return 标准化标题
     */
    protected String normalizeHeadingTitle(String title) {
        String normalizedTitle = normalizeTextLine(title);
        if (!isBlank(documentTopics.getHeadingBoundaryPattern())) {
            normalizedTitle = normalizedTitle.replaceAll(documentTopics.getHeadingBoundaryPattern(), "");
        }
        return normalizedTitle;
    }

    /**
     * 标准化概念标识。
     *
     * @param value 原始值
     * @return 概念标识
     */
    protected String normalizeConceptId(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.isEmpty()) {
            return "default";
        }
        return normalized;
    }

    /**
     * 判断文本是否为空白。
     *
     * @param value 原始值
     * @return 空白返回 true
     */
    protected boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 判断文本是否以任意配置前缀开头。
     *
     * @param value 原始值
     * @param prefixes 前缀列表
     * @return 命中任意前缀返回 true
     */
    protected boolean startsWithAny(String value, List<String> prefixes) {
        if (prefixes == null || prefixes.isEmpty()) {
            return false;
        }
        for (String prefix : prefixes) {
            if (!isBlank(prefix) && value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断文本是否以任意配置后缀结尾。
     *
     * @param value 原始值
     * @param suffixes 后缀列表
     * @return 命中任意后缀返回 true
     */
    protected boolean endsWithAny(String value, List<String> suffixes) {
        if (suffixes == null || suffixes.isEmpty()) {
            return false;
        }
        for (String suffix : suffixes) {
            if (!isBlank(suffix) && value.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 标准化换行符。
     *
     * @param content 原始内容
     * @return 标准化内容
     */
    protected String normalizeLineBreaks(String content) {
        if (content == null) {
            return "";
        }
        return content.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    /**
     * 标准化单行文本。
     *
     * @param line 原始行
     * @return 标准化行
     */
    protected String normalizeTextLine(String line) {
        if (line == null) {
            return "";
        }
        return line.trim().replaceAll("\\s+", " ");
    }

    /**
     * 拼接指定范围的行。
     *
     * @param lines 文档行
     * @param startIndex 起始下标
     * @param endIndex 结束下标
     * @return 拼接结果
     */
    protected String joinLines(String[] lines, int startIndex, int endIndex) {
        StringBuilder builder = new StringBuilder();
        int safeEndIndex = Math.min(endIndex, lines.length);
        for (int index = Math.max(startIndex, 0); index < safeEndIndex; index++) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(lines[index]);
        }
        return builder.toString().trim();
    }

    /**
     * 裁剪文本到指定长度。
     *
     * @param value 原始值
     * @param maxChars 最大字符数
     * @return 裁剪后的值
     */
    protected String trimToMaxChars(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String normalizedValue = value.trim();
        if (normalizedValue.length() <= maxChars) {
            return normalizedValue;
        }
        return normalizedValue.substring(0, maxChars).trim();
    }

    /**
     * 统计字符出现次数。
     *
     * @param value 原始值
     * @param target 目标字符
     * @return 出现次数
     */
    protected int countChar(String value, char target) {
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == target) {
                count++;
            }
        }
        return count;
    }

    /**
     * 统计字母、数字和汉字数量。
     *
     * @param value 原始值
     * @return 有效字符数量
     */
    protected int countLetterOrDigit(String value) {
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            char currentChar = value.charAt(index);
            if (Character.isLetterOrDigit(currentChar)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 已编译标题识别规则。
     *
     * 职责：承载正则与标题层级解析参数
     *
     * @author xiexu
     */
    protected static final class CompiledHeadingPattern {

        protected final Pattern pattern;

        protected final int titleGroup;

        protected final int fixedLevel;

        protected final int levelGroup;

        protected final String levelStrategy;

        /**
         * 创建已编译标题识别规则。
         *
         * @param pattern 正则表达式
         * @param titleGroup 标题文本分组
         * @param fixedLevel 固定标题层级
         * @param levelGroup 层级计算分组
         * @param levelStrategy 层级计算策略
         */
        protected CompiledHeadingPattern(
                Pattern pattern,
                int titleGroup,
                int fixedLevel,
                int levelGroup,
                String levelStrategy
        ) {
            this.pattern = pattern;
            this.titleGroup = titleGroup;
            this.fixedLevel = fixedLevel;
            this.levelGroup = levelGroup;
            this.levelStrategy = levelStrategy;
        }
    }

    /**
     * 标题候选。
     *
     * 职责：承载标题文本、层级与来源位置
     *
     * @author xiexu
     */
    protected static final class HeadingCandidate {

        protected final String title;

        protected final int level;

        protected final int lineIndex;

        protected final int pageNumber;

        /**
         * 创建标题候选。
         *
         * @param title 标题
         * @param level 标题层级
         * @param lineIndex 行下标
         * @param pageNumber 页码
         */
        protected HeadingCandidate(String title, int level, int lineIndex, int pageNumber) {
            this.title = title;
            this.level = level;
            this.lineIndex = lineIndex;
            this.pageNumber = pageNumber;
        }
    }

    /**
     * 专题段落。
     *
     * 职责：承载单个专题的标题、正文与来源位置
     *
     * @author xiexu
     */
    protected static final class TopicSegment {

        protected final String title;

        protected final String body;

        protected final int startLineIndex;

        protected final int endLineIndex;

        protected final int pageNumber;

        /**
         * 创建专题段落。
         *
         * @param title 标题
         * @param body 正文
         * @param startLineIndex 起始行下标
         * @param endLineIndex 结束行下标
         * @param pageNumber 页码
         */
        protected TopicSegment(String title, String body, int startLineIndex, int endLineIndex, int pageNumber) {
            this.title = title;
            this.body = body;
            this.startLineIndex = startLineIndex;
            this.endLineIndex = endLineIndex;
            this.pageNumber = pageNumber;
        }

        /**
         * 合并相邻专题段落。
         *
         * @param nextSegment 后续专题
         * @return 合并后的专题
         */
        protected TopicSegment merge(TopicSegment nextSegment) {
            return new TopicSegment(
                    title,
                    body + "\n\n" + nextSegment.body,
                    startLineIndex,
                    nextSegment.endLineIndex,
                    pageNumber
            );
        }
    }
}
