package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.domain.ConceptSection;
import com.xbk.lattice.compiler.domain.MergedConcept;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import com.xbk.lattice.shared.json.JsonMappers;
import com.xbk.lattice.shared.text.DocumentTitleSupport;
import com.xbk.lattice.source.domain.KnowledgeSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * 文章标题画像支撑工具。
 *
 * 职责：集中计算 sourceTitle、anchorTitle、representativeTitle 与生成模式
 *
 * @author xiexu
 */
public final class ArticleTitleProfileSupport {

    private static final ObjectMapper OBJECT_MAPPER = JsonMappers.defaultMapper();

    private static final String TITLE_GENERATION_VERSION = "v1";

    private static final int ANCHOR_DIRECT_MIN_LENGTH = 8;

    private static final int MIN_REPRESENTATIVE_TITLE_LENGTH = 6;

    private static final int MAX_REPRESENTATIVE_TITLE_LENGTH = 28;

    private static final List<String> GENERIC_TITLES = List.of(
            "概述",
            "说明",
            "总结",
            "附录",
            "其他",
            "备注",
            "计划",
            "方案",
            "背景",
            "问题",
            "结论",
            "下一步",
            "下一步计划"
    );

    private static final List<String> SENTENCE_BOUNDARIES = List.of("。", "；", "，", ":", "：", "\n");

    private ArticleTitleProfileSupport() {
    }

    /**
     * 解析标题画像。
     *
     * @param mergedConcept 合并概念
     * @param sourceFileRecords 来源文件记录
     * @param knowledgeSource 资料源
     * @return 标题画像
     */
    public static TitleProfile resolve(
            MergedConcept mergedConcept,
            List<SourceFileRecord> sourceFileRecords,
            KnowledgeSource knowledgeSource
    ) {
        String anchorTitle = normalizeTitle(mergedConcept == null ? null : mergedConcept.getTitle());
        String sourceTitle = resolveSourceTitle(sourceFileRecords, knowledgeSource, mergedConcept);
        if (!StringUtils.hasText(anchorTitle)) {
            String fallbackTitle = StringUtils.hasText(sourceTitle)
                    ? sourceTitle
                    : normalizeTitle(mergedConcept == null ? null : mergedConcept.getConceptId());
            return new TitleProfile(
                    sourceTitle,
                    fallbackTitle,
                    fallbackTitle,
                    "LEGACY_UNSET",
                    "LOW",
                    TITLE_GENERATION_VERSION
            );
        }

        if (shouldUseAnchorDirect(anchorTitle, mergedConcept)) {
            return new TitleProfile(
                    sourceTitle,
                    anchorTitle,
                    anchorTitle,
                    "ANCHOR_DIRECT",
                    "HIGH",
                    TITLE_GENERATION_VERSION
            );
        }

        RuleBasedTitleResult ruleBasedTitleResult = buildRuleBasedTitle(mergedConcept, sourceTitle, anchorTitle);
        String representativeTitle = StringUtils.hasText(ruleBasedTitleResult.getRepresentativeTitle())
                ? ruleBasedTitleResult.getRepresentativeTitle()
                : anchorTitle;
        return new TitleProfile(
                sourceTitle,
                anchorTitle,
                representativeTitle,
                "RULE_BASED",
                ruleBasedTitleResult.getConfidence(),
                TITLE_GENERATION_VERSION
        );
    }

    /**
     * 判断标题画像是否需要进入 LLM 兜底。
     *
     * @param titleProfile 标题画像
     * @return 需要兜底返回 true
     */
    public static boolean shouldUseLlmFallback(TitleProfile titleProfile) {
        if (titleProfile == null) {
            return false;
        }
        return "RULE_BASED".equals(titleProfile.getTitleGenerationMode())
                && "LOW".equals(titleProfile.getTitleGenerationConfidence());
    }

    /**
     * 规范化 LLM 返回的标题候选。
     *
     * @param rawTitle 原始标题
     * @return 规范化后的标题；无效时返回空字符串
     */
    public static String normalizeGeneratedTitleCandidate(String rawTitle) {
        if (!StringUtils.hasText(rawTitle)) {
            return "";
        }
        String normalizedTitle = rawTitle.trim().replace('\r', '\n');
        if (normalizedTitle.startsWith("---")) {
            return "";
        }
        String[] lines = normalizedTitle.split("\\R");
        String firstLine = "";
        for (String line : lines) {
            String trimmedLine = normalizeTitle(line);
            if (!StringUtils.hasText(trimmedLine)) {
                continue;
            }
            firstLine = trimmedLine;
            break;
        }
        if (!StringUtils.hasText(firstLine)) {
            return "";
        }
        String candidate = stripGeneratedTitlePrefix(firstLine);
        candidate = candidate.replaceFirst("^[#>*\\-\\s]+", "");
        candidate = candidate.replaceFirst("^[\"'`“”‘’]+", "");
        candidate = candidate.replaceFirst("[\"'`“”‘’]+$", "");
        int boundaryIndex = findFirstBoundary(candidate);
        if (boundaryIndex > 0) {
            candidate = candidate.substring(0, boundaryIndex).trim();
        }
        candidate = trimTitle(candidate);
        if (!StringUtils.hasText(candidate)) {
            return "";
        }
        return isGenericAnchorTitle(candidate) ? "" : candidate;
    }

    /**
     * 判断是否直接采用锚点标题。
     *
     * @param anchorTitle 锚点标题
     * @param mergedConcept 合并概念
     * @return 直接采用返回 true
     */
    static boolean shouldUseAnchorDirect(String anchorTitle, MergedConcept mergedConcept) {
        String normalizedAnchorTitle = normalizeTitle(anchorTitle);
        if (!StringUtils.hasText(normalizedAnchorTitle)) {
            return false;
        }
        if (isGenericAnchorTitle(normalizedAnchorTitle)) {
            return false;
        }
        if (normalizedAnchorTitle.length() >= ANCHOR_DIRECT_MIN_LENGTH) {
            return true;
        }
        int overlappedSectionCount = countSectionHeadingOverlap(normalizedAnchorTitle, mergedConcept);
        return overlappedSectionCount > 0;
    }

    /**
     * 解析来源标题。
     *
     * @param sourceFileRecords 来源文件记录
     * @param knowledgeSource 资料源
     * @param mergedConcept 合并概念
     * @return 来源标题
     */
    static String resolveSourceTitle(
            List<SourceFileRecord> sourceFileRecords,
            KnowledgeSource knowledgeSource,
            MergedConcept mergedConcept
    ) {
        for (SourceFileRecord sourceFileRecord : safeSourceFiles(sourceFileRecords)) {
            String documentTitle = DocumentTitleSupport.resolveMetadataDocumentTitle(sourceFileRecord.getMetadataJson());
            if (StringUtils.hasText(documentTitle)) {
                return documentTitle.trim();
            }
        }
        String bundleDisplayName = readBundleDisplayName(knowledgeSource);
        if (StringUtils.hasText(bundleDisplayName)) {
            return bundleDisplayName.trim();
        }
        String bundlePrimaryTitleHint = readBundlePrimaryTitleHint(knowledgeSource);
        if (StringUtils.hasText(bundlePrimaryTitleHint)) {
            return bundlePrimaryTitleHint.trim();
        }
        for (SourceFileRecord sourceFileRecord : safeSourceFiles(sourceFileRecords)) {
            String fileNameTitle = DocumentTitleSupport.resolveFileNameTitle(sourceFileRecord.getRelativePath());
            if (StringUtils.hasText(fileNameTitle)) {
                return fileNameTitle.trim();
            }
        }
        if (mergedConcept != null && mergedConcept.getSourcePaths() != null) {
            for (String sourcePath : mergedConcept.getSourcePaths()) {
                String fileNameTitle = DocumentTitleSupport.resolveFileNameTitle(sourcePath);
                if (StringUtils.hasText(fileNameTitle)) {
                    return fileNameTitle.trim();
                }
            }
        }
        return "";
    }

    /**
     * 生成规则归纳标题。
     *
     * @param mergedConcept 合并概念
     * @param sourceTitle 来源标题
     * @param anchorTitle 锚点标题
     * @return 规则标题
     */
    static RuleBasedTitleResult buildRuleBasedTitle(MergedConcept mergedConcept, String sourceTitle, String anchorTitle) {
        List<String> sectionHeadingCandidates = readSectionHeadingCandidates(mergedConcept);
        List<String> contentLineCandidates = readContentLineCandidates(mergedConcept);
        List<String> snippetCandidates = readSnippetCandidates(mergedConcept);
        List<String> summaryCandidates = readSummaryCandidates(mergedConcept);
        String normalizedAnchorTitle = normalizeTitle(anchorTitle);
        String pairedHeadingTitle = findCompatiblePair(sectionHeadingCandidates, normalizedAnchorTitle);
        if (StringUtils.hasText(pairedHeadingTitle)) {
            return new RuleBasedTitleResult(trimTitle(pairedHeadingTitle), "HIGH");
        }

        String firstSectionHeading = findFirstUsableCandidate(sectionHeadingCandidates, normalizedAnchorTitle);
        if (StringUtils.hasText(firstSectionHeading)) {
            String confidence = firstSectionHeading.length() >= ANCHOR_DIRECT_MIN_LENGTH ? "MEDIUM" : "LOW";
            return new RuleBasedTitleResult(trimTitle(firstSectionHeading), confidence);
        }

        LinkedHashSet<String> secondaryCandidates = new LinkedHashSet<String>();
        secondaryCandidates.addAll(contentLineCandidates);
        secondaryCandidates.addAll(snippetCandidates);
        secondaryCandidates.addAll(summaryCandidates);
        String pairedSecondaryTitle = findCompatiblePair(new ArrayList<String>(secondaryCandidates), normalizedAnchorTitle);
        if (StringUtils.hasText(pairedSecondaryTitle)) {
            return new RuleBasedTitleResult(trimTitle(pairedSecondaryTitle), "MEDIUM");
        }

        String firstSecondaryCandidate = findFirstUsableCandidate(new ArrayList<String>(secondaryCandidates), normalizedAnchorTitle);
        if (StringUtils.hasText(firstSecondaryCandidate)) {
            String confidence = firstSecondaryCandidate.length() >= ANCHOR_DIRECT_MIN_LENGTH ? "MEDIUM" : "LOW";
            return new RuleBasedTitleResult(trimTitle(firstSecondaryCandidate), confidence);
        }
        String normalizedSourceTitle = normalizeTitle(sourceTitle);
        if (StringUtils.hasText(normalizedSourceTitle) && !normalizedSourceTitle.equals(normalizedAnchorTitle)) {
            return new RuleBasedTitleResult(trimTitle(normalizedSourceTitle), "LOW");
        }
        return new RuleBasedTitleResult("", "LOW");
    }

    /**
     * 查找首个可用标题候选。
     *
     * @param candidates 候选列表
     * @param anchorTitle 锚点标题
     * @return 首个可用候选
     */
    private static String findFirstUsableCandidate(List<String> candidates, String anchorTitle) {
        for (String candidate : candidates) {
            String normalizedCandidate = normalizeTitle(candidate);
            if (!StringUtils.hasText(normalizedCandidate)) {
                continue;
            }
            if (normalizedCandidate.equals(anchorTitle)) {
                continue;
            }
            if (isGenericAnchorTitle(normalizedCandidate)) {
                continue;
            }
            return normalizedCandidate;
        }
        return "";
    }

    /**
     * 查找可拼接的候选对。
     *
     * @param candidates 候选列表
     * @param anchorTitle 锚点标题
     * @return 可拼接标题；找不到返回空串
     */
    private static String findCompatiblePair(List<String> candidates, String anchorTitle) {
        String firstCandidate = "";
        for (String candidate : candidates) {
            String normalizedCandidate = normalizeTitle(candidate);
            if (!StringUtils.hasText(normalizedCandidate)) {
                continue;
            }
            if (normalizedCandidate.equals(anchorTitle)) {
                continue;
            }
            if (isGenericAnchorTitle(normalizedCandidate)) {
                continue;
            }
            if (!StringUtils.hasText(firstCandidate)) {
                firstCandidate = normalizedCandidate;
                continue;
            }
            if (isCompatiblePair(firstCandidate, normalizedCandidate)) {
                return firstCandidate + "与" + normalizedCandidate;
            }
        }
        return "";
    }

    /**
     * 读取 section heading 候选。
     *
     * @param mergedConcept 合并概念
     * @return heading 候选
     */
    private static List<String> readSectionHeadingCandidates(MergedConcept mergedConcept) {
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        if (mergedConcept == null || mergedConcept.getSections() == null) {
            return new ArrayList<String>(values);
        }
        for (ConceptSection section : mergedConcept.getSections()) {
            String heading = normalizeTitle(section == null ? null : section.getHeading());
            if (!StringUtils.hasText(heading)) {
                continue;
            }
            if (heading.length() <= 2) {
                continue;
            }
            values.add(heading);
        }
        return new ArrayList<String>(values);
    }

    /**
     * 读取内容行候选。
     *
     * @param mergedConcept 合并概念
     * @return 内容候选
     */
    private static List<String> readContentLineCandidates(MergedConcept mergedConcept) {
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        if (mergedConcept == null || mergedConcept.getSections() == null) {
            return new ArrayList<String>(values);
        }
        for (ConceptSection section : mergedConcept.getSections()) {
            for (String contentLine : safeList(section == null ? null : section.getContentLines())) {
                String title = toActionPhraseCandidate(contentLine);
                if (StringUtils.hasText(title)) {
                    values.add(title);
                }
            }
        }
        return new ArrayList<String>(values);
    }

    /**
     * 读取摘要片段候选。
     *
     * @param mergedConcept 合并概念
     * @return 摘要候选
     */
    private static List<String> readSnippetCandidates(MergedConcept mergedConcept) {
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        if (mergedConcept == null) {
            return new ArrayList<String>(values);
        }
        for (String snippet : safeList(mergedConcept.getSnippets())) {
            String candidate = toActionPhraseCandidate(snippet);
            if (StringUtils.hasText(candidate)) {
                values.add(candidate);
            }
        }
        return new ArrayList<String>(values);
    }

    /**
     * 读取描述候选。
     *
     * @param mergedConcept 合并概念
     * @return 描述候选
     */
    private static List<String> readSummaryCandidates(MergedConcept mergedConcept) {
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        if (mergedConcept == null) {
            return new ArrayList<String>(values);
        }
        String candidate = toActionPhraseCandidate(mergedConcept.getDescription());
        if (StringUtils.hasText(candidate)) {
            values.add(candidate);
        }
        return new ArrayList<String>(values);
    }

    /**
     * 将正文句子压缩为标题候选。
     *
     * @param value 原始句子
     * @return 标题候选
     */
    private static String toActionPhraseCandidate(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = normalizeTitle(value);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        normalized = stripLeadingConnectors(normalized);
        int punctuationIndex = findFirstBoundary(normalized);
        if (punctuationIndex > 0) {
            normalized = normalized.substring(0, punctuationIndex).trim();
        }
        normalized = stripTrailingConnectors(normalized);
        if (normalized.length() < MIN_REPRESENTATIVE_TITLE_LENGTH) {
            return "";
        }
        if (normalized.length() > MAX_REPRESENTATIVE_TITLE_LENGTH) {
            normalized = normalized.substring(0, MAX_REPRESENTATIVE_TITLE_LENGTH).trim();
        }
        if (isGenericAnchorTitle(normalized)) {
            return "";
        }
        return normalized;
    }

    /**
     * 判断锚点标题是否为通用弱语义标题。
     *
     * @param anchorTitle 锚点标题
     * @return 命中泛化标题返回 true
     */
    private static boolean isGenericAnchorTitle(String anchorTitle) {
        String normalized = normalizeTitle(anchorTitle);
        if (!StringUtils.hasText(normalized)) {
            return true;
        }
        return GENERIC_TITLES.contains(normalized);
    }

    /**
     * 去除句首通用连接词，保留主体语义。
     *
     * @param value 原始文本
     * @return 裁剪后的文本
     */
    private static String stripLeadingConnectors(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String prefix : List.of("当前", "目前", "本节", "本段", "本部分", "本阶段", "其中", "其中的", "需要", "需", "同时", "以及", "如果")) {
                if (normalized.startsWith(prefix) && normalized.length() > prefix.length() + 2) {
                    normalized = normalized.substring(prefix.length()).trim();
                    changed = true;
                }
            }
            for (String connector : List.of("要", "将", "把", "对", "做", "进行", "继续")) {
                if (normalized.startsWith(connector) && normalized.length() > connector.length() + 2) {
                    normalized = normalized.substring(connector.length()).trim();
                    changed = true;
                }
            }
        }
        return normalized;
    }

    /**
     * 去除句尾弱语义连接词，避免标题残留半句。
     *
     * @param value 原始文本
     * @return 裁剪后的文本
     */
    private static String stripTrailingConnectors(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String suffix : List.of("即可", "处理", "推进", "完成", "落地", "同步", "回写", "记录")) {
                if (normalized.endsWith(suffix) && normalized.length() > suffix.length() + 2) {
                    changed = false;
                    break;
                }
            }
            for (String suffix : List.of("的", "了", "后", "前", "中", "并", "与", "及", "等")) {
                if (normalized.endsWith(suffix) && normalized.length() > suffix.length() + 2) {
                    normalized = normalized.substring(0, normalized.length() - suffix.length()).trim();
                    changed = true;
                }
            }
        }
        return normalized;
    }

    /**
     * 去除模型常见的标题前缀。
     *
     * @param value 原始文本
     * @return 去前缀后的文本
     */
    private static String stripGeneratedTitlePrefix(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = normalizeTitle(value);
        for (String prefix : List.of("标题：", "标题:", "title:", "Title:")) {
            if (normalized.startsWith(prefix) && normalized.length() > prefix.length()) {
                return normalizeTitle(normalized.substring(prefix.length()));
            }
        }
        return normalized;
    }

    /**
     * 统计锚点标题与 section 的重合数量。
     *
     * @param anchorTitle 锚点标题
     * @param mergedConcept 合并概念
     * @return 重合数量
     */
    private static int countSectionHeadingOverlap(String anchorTitle, MergedConcept mergedConcept) {
        int count = 0;
        if (mergedConcept == null || mergedConcept.getSections() == null) {
            return count;
        }
        String normalizedAnchorTitle = normalizeTitle(anchorTitle);
        for (ConceptSection section : mergedConcept.getSections()) {
            String heading = normalizeTitle(section == null ? null : section.getHeading());
            if (!StringUtils.hasText(heading)) {
                continue;
            }
            if (heading.contains(normalizedAnchorTitle) || normalizedAnchorTitle.contains(heading)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 判断两个候选是否可以并列拼接。
     *
     * @param left 左候选
     * @param right 右候选
     * @return 可拼接返回 true
     */
    private static boolean isCompatiblePair(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        if (left.equals(right)) {
            return false;
        }
        if (left.contains(right) || right.contains(left)) {
            return false;
        }
        return (left.length() + right.length() + 1) <= MAX_REPRESENTATIVE_TITLE_LENGTH;
    }

    /**
     * 读取资料源 displayName。
     *
     * @param knowledgeSource 资料源
     * @return displayName
     */
    private static String readBundleDisplayName(KnowledgeSource knowledgeSource) {
        JsonNode bundleNode = readBundleNode(knowledgeSource);
        String displayName = bundleNode.path("displayName").asText("");
        if (StringUtils.hasText(displayName)) {
            return displayName.trim();
        }
        return "";
    }

    /**
     * 读取资料源首个标题提示。
     *
     * @param knowledgeSource 资料源
     * @return 标题提示
     */
    private static String readBundlePrimaryTitleHint(KnowledgeSource knowledgeSource) {
        JsonNode bundleNode = readBundleNode(knowledgeSource);
        JsonNode titleHintsNode = bundleNode.path("titleHints");
        if (!titleHintsNode.isArray()) {
            return "";
        }
        for (JsonNode itemNode : titleHintsNode) {
            String value = itemNode.asText("");
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    /**
     * 读取 bundleSummary 节点。
     *
     * @param knowledgeSource 资料源
     * @return bundleSummary 节点
     */
    private static JsonNode readBundleNode(KnowledgeSource knowledgeSource) {
        if (knowledgeSource == null || !StringUtils.hasText(knowledgeSource.getMetadataJson())) {
            return OBJECT_MAPPER.createObjectNode();
        }
        try {
            JsonNode metadataNode = OBJECT_MAPPER.readTree(knowledgeSource.getMetadataJson());
            return metadataNode.path("bundleSummary");
        }
        catch (Exception ignored) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    /**
     * 裁剪标题长度。
     *
     * @param title 原始标题
     * @return 裁剪后标题
     */
    private static String trimTitle(String title) {
        String normalized = normalizeTitle(title);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        if (normalized.length() <= MAX_REPRESENTATIVE_TITLE_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_REPRESENTATIVE_TITLE_LENGTH).trim();
    }

    /**
     * 查找正文边界。
     *
     * @param value 文本
     * @return 边界位置
     */
    private static int findFirstBoundary(String value) {
        int minIndex = -1;
        for (String token : SENTENCE_BOUNDARIES) {
            int index = value.indexOf(token);
            if (index < 0) {
                continue;
            }
            if (minIndex < 0 || index < minIndex) {
                minIndex = index;
            }
        }
        return minIndex;
    }

    /**
     * 标题标准化。
     *
     * @param value 原始文本
     * @return 归一文本
     */
    private static String normalizeTitle(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ");
    }

    /**
     * 返回非空来源文件列表。
     *
     * @param sourceFileRecords 原始列表
     * @return 非空列表
     */
    private static List<SourceFileRecord> safeSourceFiles(List<SourceFileRecord> sourceFileRecords) {
        return sourceFileRecords == null ? List.of() : sourceFileRecords;
    }

    /**
     * 返回非空文本列表。
     *
     * @param values 原始列表
     * @return 非空列表
     */
    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    /**
     * 标题画像。
     *
     * 职责：承载标题生成结果
     *
     * @author xiexu
     */
    public static final class TitleProfile {

        private final String sourceTitle;

        private final String anchorTitle;

        private final String representativeTitle;

        private final String titleGenerationMode;

        private final String titleGenerationConfidence;

        private final String titleGenerationVersion;

        /**
         * 创建标题画像。
         *
         * @param sourceTitle 来源标题
         * @param anchorTitle 锚点标题
         * @param representativeTitle 代表标题
         * @param titleGenerationMode 生成模式
         * @param titleGenerationConfidence 生成置信度
         * @param titleGenerationVersion 规则版本
         */
        public TitleProfile(
                String sourceTitle,
                String anchorTitle,
                String representativeTitle,
                String titleGenerationMode,
                String titleGenerationConfidence,
                String titleGenerationVersion
        ) {
            this.sourceTitle = sourceTitle;
            this.anchorTitle = anchorTitle;
            this.representativeTitle = representativeTitle;
            this.titleGenerationMode = titleGenerationMode;
            this.titleGenerationConfidence = titleGenerationConfidence;
            this.titleGenerationVersion = titleGenerationVersion;
        }

        public String getSourceTitle() {
            return sourceTitle;
        }

        public String getAnchorTitle() {
            return anchorTitle;
        }

        public String getRepresentativeTitle() {
            return representativeTitle;
        }

        public String getTitleGenerationMode() {
            return titleGenerationMode;
        }

        public String getTitleGenerationConfidence() {
            return titleGenerationConfidence;
        }

        public String getTitleGenerationVersion() {
            return titleGenerationVersion;
        }
    }

    /**
     * 规则标题计算结果。
     *
     * 职责：承载规则层产出的标题与置信度
     *
     * @author xiexu
     */
    static final class RuleBasedTitleResult {

        private final String representativeTitle;

        private final String confidence;

        /**
         * 创建规则标题结果。
         *
         * @param representativeTitle 代表标题
         * @param confidence 置信度
         */
        RuleBasedTitleResult(String representativeTitle, String confidence) {
            this.representativeTitle = representativeTitle;
            this.confidence = confidence;
        }

        String getRepresentativeTitle() {
            return representativeTitle;
        }

        String getConfidence() {
            return confidence;
        }
    }
}
