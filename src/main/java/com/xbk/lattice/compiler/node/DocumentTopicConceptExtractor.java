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
 * 文档专题概念提取器
 *
 * 职责：在缺少结构化分析结果时，把长文档按文档自身的标题层级拆成多个概念
 *
 * @author xiexu
 */
public class DocumentTopicConceptExtractor {

    private static final String LEVEL_STRATEGY_MARKDOWN_PREFIX_LENGTH = "markdown-prefix-length";

    private static final String LEVEL_STRATEGY_NUMERIC_DEPTH = "numeric-depth";

    private final CompilerProperties.DocumentTopics documentTopics;

    private final Pattern pageMarkerPattern;

    private final List<CompiledHeadingPattern> headingPatterns;

    /**
     * 创建文档专题概念提取器。
     */
    public DocumentTopicConceptExtractor() {
        this(new CompilerProperties.DocumentTopics());
    }

    /**
     * 创建文档专题概念提取器。
     *
     * @param documentTopics 长文档专题拆分配置
     */
    public DocumentTopicConceptExtractor(CompilerProperties.DocumentTopics documentTopics) {
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
    private Pattern compileOptionalPattern(String pattern) {
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
    private List<CompiledHeadingPattern> compileHeadingPatterns(
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
     * 从长文档中提取专题概念。
     *
     * @param groupKey 分组键
     * @param sortedSources 已排序源文件
     * @return 专题概念列表；无法稳定拆分时返回空列表
     */
    public List<AnalyzedConcept> extract(String groupKey, List<RawSource> sortedSources) {
        List<AnalyzedConcept> analyzedConcepts = new ArrayList<AnalyzedConcept>();
        if (!documentTopics.isEnabled()) {
            return analyzedConcepts;
        }
        int topicIndex = 0;
        for (RawSource rawSource : sortedSources) {
            if (!isLongDocument(rawSource)) {
                continue;
            }
            List<TopicSegment> topicSegments = splitTopics(rawSource);
            if (topicSegments.size() < 2) {
                continue;
            }
            for (TopicSegment topicSegment : topicSegments) {
                topicIndex++;
                analyzedConcepts.add(toAnalyzedConcept(groupKey, rawSource, topicSegment, topicIndex));
            }
        }
        return analyzedConcepts;
    }

    /**
     * 判断源文件是否达到长文档拆分条件。
     *
     * @param rawSource 源文件
     * @return 达到长文档条件返回 true
     */
    private boolean isLongDocument(RawSource rawSource) {
        if (rawSource == null || rawSource.getContent() == null) {
            return false;
        }
        String content = rawSource.getContent().trim();
        if (content.length() >= documentTopics.getLongDocumentMinChars()) {
            return true;
        }
        return content.length() >= documentTopics.getMediumDocumentMinChars()
                && collectHeadings(content).size() >= documentTopics.getMinHeadingsForMediumDocument();
    }

    /**
     * 按候选标题切出专题段落。
     *
     * @param rawSource 源文件
     * @return 专题段落列表
     */
    private List<TopicSegment> splitTopics(RawSource rawSource) {
        String content = normalizeLineBreaks(rawSource.getContent());
        String[] lines = content.split("\\R", -1);
        List<HeadingCandidate> headings = collectHeadings(lines);
        if (headings.size() < 2) {
            return new ArrayList<TopicSegment>();
        }

        List<HeadingCandidate> splitHeadings = selectSplitHeadings(headings);
        if (splitHeadings.size() < 2) {
            return new ArrayList<TopicSegment>();
        }

        List<TopicSegment> topicSegments = buildSegments(lines, splitHeadings);
        List<TopicSegment> mergedSegments = mergeSmallSegments(topicSegments);
        return refineLargeSegments(lines, mergedSegments, headings);
    }

    /**
     * 从文本中收集标题候选。
     *
     * @param content 文档正文
     * @return 标题候选列表
     */
    private List<HeadingCandidate> collectHeadings(String content) {
        String[] lines = normalizeLineBreaks(content).split("\\R", -1);
        return collectHeadings(lines);
    }

    /**
     * 从行列表中收集标题候选。
     *
     * @param lines 文档行
     * @return 标题候选列表
     */
    private List<HeadingCandidate> collectHeadings(String[] lines) {
        List<HeadingCandidate> headings = new ArrayList<HeadingCandidate>();
        int currentPage = 0;
        boolean insideFencedCodeBlock = false;
        for (int index = 0; index < lines.length; index++) {
            String line = normalizeTextLine(lines[index]);
            if (isFenceLine(line)) {
                insideFencedCodeBlock = !insideFencedCodeBlock;
                continue;
            }
            if (insideFencedCodeBlock) {
                continue;
            }
            Matcher pageMatcher = matchPageMarker(line);
            if (pageMatcher != null) {
                currentPage = parsePageNumber(pageMatcher);
                continue;
            }
            HeadingCandidate heading = parseHeading(line, index, currentPage, lines);
            if (heading != null) {
                headings.add(heading);
            }
        }
        return headings;
    }

    /**
     * 判断当前行是否为 Markdown fenced code block 边界。
     *
     * @param line 归一化后的单行文本
     * @return 命中返回 true
     */
    private boolean isFenceLine(String line) {
        return line != null
                && !line.isBlank()
                && (line.startsWith("```") || line.startsWith("~~~"));
    }

    /**
     * 解析单行标题候选。
     *
     * @param line 单行文本
     * @param lineIndex 行下标
     * @param pageNumber 当前页码
     * @param lines 文档行
     * @return 标题候选；不是标题时返回空
     */
    private HeadingCandidate parseHeading(String line, int lineIndex, int pageNumber, String[] lines) {
        if (!isHeadingLikeLine(line)) {
            return null;
        }

        for (CompiledHeadingPattern headingPattern : headingPatterns) {
            Matcher headingMatcher = headingPattern.pattern.matcher(line);
            if (!headingMatcher.matches()) {
                continue;
            }
            String title = normalizeHeadingTitle(readMatcherGroup(headingMatcher, headingPattern.titleGroup));
            if (title.isEmpty()) {
                continue;
            }
            return new HeadingCandidate(
                    title,
                    calculateHeadingLevel(headingMatcher, headingPattern),
                    lineIndex,
                    pageNumber
            );
        }

        if (isLayoutHeading(line, lineIndex, lines)) {
            return new HeadingCandidate(normalizeHeadingTitle(line), 2, lineIndex, pageNumber);
        }
        return null;
    }

    /**
     * 判断行文本是否具备标题形态。
     *
     * @param line 单行文本
     * @return 具备标题形态返回 true
     */
    private boolean isHeadingLikeLine(String line) {
        if (line.isEmpty()
                || line.length() > documentTopics.getMaxHeadingChars()
                || line.length() < documentTopics.getMinHeadingChars()) {
            return false;
        }
        String lowerCaseLine = line.toLowerCase(Locale.ROOT);
        if (startsWithAny(line, documentTopics.getIgnoredLinePrefixes())) {
            return false;
        }
        if (line.contains("->")
                || line.contains("-->")
                || lowerCaseLine.startsWith("actor ")
                || lowerCaseLine.startsWith("participant ")
                || lowerCaseLine.startsWith("queue ")
                || lowerCaseLine.startsWith("alt ")
                || lowerCaseLine.startsWith("note ")
                || lowerCaseLine.startsWith("activate ")
                || lowerCaseLine.startsWith("deactivate ")
                || lowerCaseLine.startsWith("@startuml")
                || lowerCaseLine.startsWith("@enduml")) {
            return false;
        }
        if (countChar(line, '|') >= 2) {
            return false;
        }
        if (endsWithAny(line, documentTopics.getHeadingTerminalPunctuations())) {
            return false;
        }
        return true;
    }

    /**
     * 判断无编号短行是否像版式标题。
     *
     * @param line 当前行
     * @param lineIndex 当前行下标
     * @param lines 文档行
     * @return 像版式标题返回 true
     */
    private boolean isLayoutHeading(String line, int lineIndex, String[] lines) {
        if (line.length() > documentTopics.getMaxLayoutHeadingChars()
                || countLetterOrDigit(line) < documentTopics.getMinLayoutHeadingLetters()) {
            return false;
        }
        String previousLine = lineIndex == 0 ? "" : normalizeTextLine(lines[lineIndex - 1]);
        String nextLine = lineIndex + 1 >= lines.length ? "" : normalizeTextLine(lines[lineIndex + 1]);
        if (nextLine.isEmpty()) {
            return false;
        }
        boolean separatedFromPrevious = previousLine.isEmpty() || matchesPageMarker(previousLine);
        boolean nextLooksLikeBody = nextLine.length() > line.length()
                || endsWithAny(nextLine, documentTopics.getBodyTerminalPunctuations());
        return separatedFromPrevious && nextLooksLikeBody;
    }

    /**
     * 选择用于切分的标题集合。
     *
     * @param headings 所有标题候选
     * @return 切分标题集合
     */
    private List<HeadingCandidate> selectSplitHeadings(List<HeadingCandidate> headings) {
        int baseLevel = findBaseHeadingLevel(headings);
        int baseLevelCount = countByExactLevel(headings, baseLevel);
        int maxLevel = baseLevelCount >= 2 ? baseLevel : baseLevel + 1;

        List<HeadingCandidate> splitHeadings = new ArrayList<HeadingCandidate>();
        for (HeadingCandidate heading : headings) {
            if (heading.level <= maxLevel) {
                splitHeadings.add(heading);
            }
        }
        splitHeadings = removeSingleWrapperHeading(splitHeadings, baseLevel);
        return deduplicateNearbyHeadings(splitHeadings);
    }

    /**
     * 当文档只有一个总标题且后续存在多个同层子标题时，移除总标题，避免生成 catch-all 大专题。
     *
     * @param splitHeadings 初始切分标题
     * @param baseLevel 最顶层标题层级
     * @return 清理后的标题集合
     */
    private List<HeadingCandidate> removeSingleWrapperHeading(List<HeadingCandidate> splitHeadings, int baseLevel) {
        if (splitHeadings == null || splitHeadings.isEmpty() || countByExactLevel(splitHeadings, baseLevel) != 1) {
            return splitHeadings;
        }
        HeadingCandidate wrapperHeading = null;
        for (HeadingCandidate heading : splitHeadings) {
            if (heading.level == baseLevel) {
                wrapperHeading = heading;
                break;
            }
        }
        if (wrapperHeading == null || wrapperHeading.lineIndex > 5) {
            return splitHeadings;
        }
        int siblingHeadingCount = 0;
        for (HeadingCandidate heading : splitHeadings) {
            if (heading.lineIndex > wrapperHeading.lineIndex && heading.level == baseLevel + 1) {
                siblingHeadingCount++;
            }
        }
        if (siblingHeadingCount < 2) {
            return splitHeadings;
        }
        List<HeadingCandidate> cleanedHeadings = new ArrayList<HeadingCandidate>();
        for (HeadingCandidate heading : splitHeadings) {
            if (heading != wrapperHeading) {
                cleanedHeadings.add(heading);
            }
        }
        return cleanedHeadings;
    }

    /**
     * 查找文档中最顶层标题层级。
     *
     * @param headings 标题候选
     * @return 最顶层标题层级
     */
    private int findBaseHeadingLevel(List<HeadingCandidate> headings) {
        int baseLevel = Integer.MAX_VALUE;
        for (HeadingCandidate heading : headings) {
            baseLevel = Math.min(baseLevel, heading.level);
        }
        return baseLevel == Integer.MAX_VALUE ? 1 : baseLevel;
    }

    /**
     * 统计指定层级标题数量。
     *
     * @param headings 标题候选
     * @param level 标题层级
     * @return 标题数量
     */
    private int countByExactLevel(List<HeadingCandidate> headings, int level) {
        int count = 0;
        for (HeadingCandidate heading : headings) {
            if (heading.level == level) {
                count++;
            }
        }
        return count;
    }

    /**
     * 去掉相邻重复或过近的标题候选。
     *
     * @param headings 原始标题候选
     * @return 去重后的标题候选
     */
    private List<HeadingCandidate> deduplicateNearbyHeadings(List<HeadingCandidate> headings) {
        List<HeadingCandidate> deduplicated = new ArrayList<HeadingCandidate>();
        HeadingCandidate previous = null;
        for (HeadingCandidate heading : headings) {
            if (previous != null
                    && heading.lineIndex - previous.lineIndex <= documentTopics.getNearbyHeadingLineDistance()
                    && normalizeConceptId(heading.title).equals(normalizeConceptId(previous.title))) {
                continue;
            }
            deduplicated.add(heading);
            previous = heading;
        }
        return deduplicated;
    }

    /**
     * 根据切分标题构建专题段落。
     *
     * @param lines 文档行
     * @param splitHeadings 切分标题
     * @return 专题段落列表
     */
    private List<TopicSegment> buildSegments(String[] lines, List<HeadingCandidate> splitHeadings) {
        List<TopicSegment> topicSegments = new ArrayList<TopicSegment>();
        for (int index = 0; index < splitHeadings.size(); index++) {
            HeadingCandidate currentHeading = splitHeadings.get(index);
            int endLineIndex = index + 1 >= splitHeadings.size()
                    ? lines.length
                    : splitHeadings.get(index + 1).lineIndex;
            String body = joinLines(lines, currentHeading.lineIndex, endLineIndex);
            TopicSegment topicSegment = new TopicSegment(
                    currentHeading.title,
                    body,
                    currentHeading.lineIndex,
                    endLineIndex,
                    currentHeading.pageNumber
            );
            if (!topicSegment.body.isBlank() && hasSubstantiveTopicBody(topicSegment, lines)) {
                topicSegments.add(topicSegment);
            }
        }
        return topicSegments;
    }

    /**
     * 判断专题正文是否包含足够的实质内容，避免仅凭标题和目录生成空壳专题。
     *
     * @param topicSegment 专题段落
     * @param lines 文档行
     * @return 含实质内容返回 true
     */
    private boolean hasSubstantiveTopicBody(TopicSegment topicSegment, String[] lines) {
        if (topicSegment == null || topicSegment.body == null || topicSegment.body.isBlank()) {
            return false;
        }
        String normalizedTitle = normalizeTextLine(topicSegment.title);
        int substantiveLineCount = 0;
        int substantiveCharCount = 0;
        for (int index = Math.max(topicSegment.startLineIndex, 0); index < Math.min(topicSegment.endLineIndex, lines.length); index++) {
            String normalizedLine = normalizeTextLine(lines[index]);
            if (normalizedLine.isEmpty()
                    || matchesPageMarker(normalizedLine)
                    || isFenceLine(normalizedLine)
                    || normalizedLine.equals(normalizedTitle)) {
                continue;
            }
            HeadingCandidate nestedHeading = parseHeading(normalizedLine, index, topicSegment.pageNumber, lines);
            if (nestedHeading != null && normalizeTextLine(nestedHeading.title).equals(normalizedLine)) {
                continue;
            }
            substantiveLineCount++;
            substantiveCharCount += normalizedLine.length();
            if (substantiveLineCount >= 2 || substantiveCharCount >= 80) {
                return true;
            }
        }
        return false;
    }

    /**
     * 合并过短专题，避免生成碎片文章。
     *
     * @param topicSegments 原始专题段落
     * @return 合并后的专题段落
     */
    private List<TopicSegment> mergeSmallSegments(List<TopicSegment> topicSegments) {
        List<TopicSegment> mergedSegments = new ArrayList<TopicSegment>();
        for (TopicSegment topicSegment : topicSegments) {
            if (topicSegment.body.length() >= documentTopics.getMinTopicChars() || mergedSegments.isEmpty()) {
                mergedSegments.add(topicSegment);
                continue;
            }
            TopicSegment previousSegment = mergedSegments.remove(mergedSegments.size() - 1);
            mergedSegments.add(previousSegment.merge(topicSegment));
        }
        if (mergedSegments.size() > 1) {
            return mergedSegments;
        }
        return topicSegments;
    }

    /**
     * 对超大专题再做一次下钻切分。
     *
     * @param lines 文档行
     * @param topicSegments 初始专题段落
     * @param headings 全量标题候选
     * @return 细化后的专题段落
     */
    private List<TopicSegment> refineLargeSegments(
            String[] lines,
            List<TopicSegment> topicSegments,
            List<HeadingCandidate> headings
    ) {
        List<TopicSegment> refinedSegments = new ArrayList<TopicSegment>();
        for (TopicSegment topicSegment : topicSegments) {
            if (topicSegment.body.length() <= documentTopics.getMaxTopicChars()) {
                refinedSegments.add(topicSegment);
                continue;
            }
            List<HeadingCandidate> childHeadings = collectChildHeadings(topicSegment, headings);
            if (childHeadings.size() < 2) {
                refinedSegments.add(topicSegment);
                continue;
            }
            List<TopicSegment> childSegments = buildSegments(lines, childHeadings);
            refinedSegments.addAll(mergeSmallSegments(childSegments));
        }
        return refinedSegments;
    }

    /**
     * 收集专题范围内的下级标题。
     *
     * @param topicSegment 专题段落
     * @param headings 全量标题候选
     * @return 下级标题列表
     */
    private List<HeadingCandidate> collectChildHeadings(TopicSegment topicSegment, List<HeadingCandidate> headings) {
        List<HeadingCandidate> childHeadings = new ArrayList<HeadingCandidate>();
        for (HeadingCandidate heading : headings) {
            if (heading.lineIndex <= topicSegment.startLineIndex || heading.lineIndex >= topicSegment.endLineIndex) {
                continue;
            }
            if (heading.level <= documentTopics.getChildHeadingMaxLevel()) {
                childHeadings.add(heading);
            }
        }
        return childHeadings;
    }

    /**
     * 把专题段落转换为分析概念。
     *
     * @param groupKey 分组键
     * @param rawSource 源文件
     * @param topicSegment 专题段落
     * @param topicIndex 专题序号
     * @return 分析概念
     */
    private AnalyzedConcept toAnalyzedConcept(
            String groupKey,
            RawSource rawSource,
            TopicSegment topicSegment,
            int topicIndex
    ) {
        String conceptId = buildConceptId(groupKey, topicSegment.title, topicIndex);
        List<String> sourcePaths = new ArrayList<String>();
        sourcePaths.add(rawSource.getRelativePath());
        List<String> snippets = new ArrayList<String>();
        snippets.add(trimToMaxChars(topicSegment.body, documentTopics.getMaxSnippetChars()));
        List<ConceptSection> sections = buildConceptSections(rawSource, topicSegment);
        return new AnalyzedConcept(
                conceptId,
                topicSegment.title,
                buildDescription(rawSource, topicSegment),
                sourcePaths,
                snippets,
                sections
        );
    }

    /**
     * 为专题构建结构化章节，优先保留标题下的子块与列表事实。
     *
     * @param rawSource 源文件
     * @param topicSegment 专题段落
     * @return 章节列表
     */
    private List<ConceptSection> buildConceptSections(RawSource rawSource, TopicSegment topicSegment) {
        List<ConceptSection> sections = splitBodyIntoSections(topicSegment.body, rawSource, topicSegment);
        if (!sections.isEmpty()) {
            return sections;
        }
        List<ConceptSection> fallbackSections = new ArrayList<ConceptSection>();
        fallbackSections.add(new ConceptSection(
                topicSegment.title,
                toContentLines(topicSegment.body),
                buildSourceRefs(rawSource, topicSegment)
        ));
        return fallbackSections;
    }

    /**
     * 按专题正文中的子标题与列表块拆分章节。
     *
     * @param body 专题正文
     * @param rawSource 源文件
     * @param topicSegment 专题段落
     * @return 拆分后的章节列表
     */
    private List<ConceptSection> splitBodyIntoSections(String body, RawSource rawSource, TopicSegment topicSegment) {
        List<ConceptSection> sections = new ArrayList<ConceptSection>();
        if (body == null || body.isBlank()) {
            return sections;
        }
        String[] lines = body.split("\\R", -1);
        String rootHeading = normalizeTextLine(topicSegment.title);
        String currentHeading = rootHeading;
        List<String> currentContentLines = new ArrayList<String>();
        for (String line : lines) {
            String normalizedLine = normalizeTextLine(line);
            if (normalizedLine.isEmpty()
                    || matchesPageMarker(normalizedLine)
                    || isFenceLine(normalizedLine)) {
                continue;
            }
            if (normalizedLine.equals(rootHeading)) {
                continue;
            }
            if (looksLikeSubSectionHeading(normalizedLine)) {
                flushSection(sections, currentHeading, currentContentLines, rawSource, topicSegment);
                currentHeading = normalizedLine;
                currentContentLines = new ArrayList<String>();
                continue;
            }
            addWrappedSectionContentLine(currentContentLines, normalizedLine);
            if (currentContentLines.size() >= documentTopics.getMaxSectionLines()) {
                flushSection(sections, currentHeading, currentContentLines, rawSource, topicSegment);
                currentHeading = rootHeading;
                currentContentLines = new ArrayList<String>();
            }
        }
        flushSection(sections, currentHeading, currentContentLines, rawSource, topicSegment);
        return sections;
    }

    /**
     * 判断行是否像专题内部的小节标题。
     *
     * @param normalizedLine 归一化后的单行文本
     * @return 命中返回 true
     */
    private boolean looksLikeSubSectionHeading(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        if (normalizedLine.length() > documentTopics.getMaxLayoutHeadingChars()) {
            return false;
        }
        if (normalizedLine.startsWith("####")
                || normalizedLine.startsWith("###")
                || normalizedLine.startsWith("##")) {
            return true;
        }
        return normalizedLine.endsWith("：")
                || normalizedLine.endsWith(":")
                || normalizedLine.startsWith("- **")
                || normalizedLine.startsWith("+ **")
                || normalizedLine.matches("^\\d+\\.\\s*\\*\\*.+\\*\\*$");
    }

    /**
     * 将当前累积内容刷成章节。
     *
     * @param sections 输出章节列表
     * @param heading 章节标题
     * @param contentLines 内容行
     * @param rawSource 源文件
     * @param topicSegment 专题段落
     */
    private void flushSection(
            List<ConceptSection> sections,
            String heading,
            List<String> contentLines,
            RawSource rawSource,
            TopicSegment topicSegment
    ) {
        if (contentLines == null || contentLines.isEmpty()) {
            return;
        }
        sections.add(new ConceptSection(
                heading,
                new ArrayList<String>(contentLines),
                buildSourceRefs(rawSource, topicSegment)
        ));
    }

    /**
     * 向章节内容列表添加裁剪后的文本行。
     *
     * @param contentLines 内容行列表
     * @param normalizedLine 归一化文本行
     */
    private void addWrappedSectionContentLine(List<String> contentLines, String normalizedLine) {
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
     * 构建概念标识。
     *
     * @param groupKey 分组键
     * @param title 专题标题
     * @param topicIndex 专题序号
     * @return 概念标识
     */
    private String buildConceptId(String groupKey, String title, int topicIndex) {
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
    private String buildDescription(RawSource rawSource, TopicSegment topicSegment) {
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
    private List<String> buildSourceRefs(RawSource rawSource, TopicSegment topicSegment) {
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
    private List<String> toContentLines(String body) {
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
    private void addWrappedContentLine(Set<String> contentLines, String normalizedLine) {
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
    private int calculateNumericLevel(String numberPrefix) {
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
    private int calculateHeadingLevel(Matcher headingMatcher, CompiledHeadingPattern headingPattern) {
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
    private String readMatcherGroup(Matcher matcher, int groupIndex) {
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
    private Matcher matchPageMarker(String line) {
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
    private boolean matchesPageMarker(String line) {
        return matchPageMarker(line) != null;
    }

    /**
     * 从页码匹配结果中解析页码。
     *
     * @param pageMatcher 页码匹配结果
     * @return 页码；无法解析时返回 0
     */
    private int parsePageNumber(Matcher pageMatcher) {
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
    private String normalizeHeadingTitle(String title) {
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
    private String normalizeConceptId(String value) {
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
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 判断文本是否以任意配置前缀开头。
     *
     * @param value 原始值
     * @param prefixes 前缀列表
     * @return 命中任意前缀返回 true
     */
    private boolean startsWithAny(String value, List<String> prefixes) {
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
    private boolean endsWithAny(String value, List<String> suffixes) {
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
    private String normalizeLineBreaks(String content) {
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
    private String normalizeTextLine(String line) {
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
    private String joinLines(String[] lines, int startIndex, int endIndex) {
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
    private String trimToMaxChars(String value, int maxChars) {
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
    private int countChar(String value, char target) {
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
    private int countLetterOrDigit(String value) {
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
    private static final class CompiledHeadingPattern {

        private final Pattern pattern;

        private final int titleGroup;

        private final int fixedLevel;

        private final int levelGroup;

        private final String levelStrategy;

        /**
         * 创建已编译标题识别规则。
         *
         * @param pattern 正则表达式
         * @param titleGroup 标题文本分组
         * @param fixedLevel 固定标题层级
         * @param levelGroup 层级计算分组
         * @param levelStrategy 层级计算策略
         */
        private CompiledHeadingPattern(
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
    private static final class HeadingCandidate {

        private final String title;

        private final int level;

        private final int lineIndex;

        private final int pageNumber;

        /**
         * 创建标题候选。
         *
         * @param title 标题
         * @param level 标题层级
         * @param lineIndex 行下标
         * @param pageNumber 页码
         */
        private HeadingCandidate(String title, int level, int lineIndex, int pageNumber) {
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
    private static final class TopicSegment {

        private final String title;

        private final String body;

        private final int startLineIndex;

        private final int endLineIndex;

        private final int pageNumber;

        /**
         * 创建专题段落。
         *
         * @param title 标题
         * @param body 正文
         * @param startLineIndex 起始行下标
         * @param endLineIndex 结束行下标
         * @param pageNumber 页码
         */
        private TopicSegment(String title, String body, int startLineIndex, int endLineIndex, int pageNumber) {
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
        private TopicSegment merge(TopicSegment nextSegment) {
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
