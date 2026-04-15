package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.model.AnalyzedConcept;
import com.xbk.lattice.compiler.model.ConceptSection;
import com.xbk.lattice.compiler.model.MergedConcept;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 最小跨批次合并节点
 *
 * 职责：按 conceptId 合并同组批次输出
 *
 * @author xiexu
 */
public class CrossGroupMergeNode {

    /**
     * 合并分析结果。
     *
     * @param analyzedConcepts 分析结果
     * @return 合并后的概念列表
     */
    public List<MergedConcept> merge(List<AnalyzedConcept> analyzedConcepts) {
        Map<String, MergeBucket> buckets = new LinkedHashMap<String, MergeBucket>();
        for (AnalyzedConcept analyzedConcept : analyzedConcepts) {
            String normalizedConceptId = normalizeConceptId(analyzedConcept.getConceptId());
            MergeBucket mergeBucket = buckets.computeIfAbsent(
                    normalizedConceptId,
                    key -> new MergeBucket(
                            selectPreferredTitle("", analyzedConcept.getTitle(), normalizedConceptId),
                            selectPreferredDescription("", analyzedConcept.getDescription())
                    )
            );
            mergeBucket.title = selectPreferredTitle(mergeBucket.title, analyzedConcept.getTitle(), normalizedConceptId);
            mergeBucket.description = selectPreferredDescription(mergeBucket.description, analyzedConcept.getDescription());
            mergeSourcePaths(mergeBucket, analyzedConcept.getSourcePaths());
            mergeSnippets(mergeBucket, analyzedConcept.getSnippets());
            mergeSections(mergeBucket, analyzedConcept.getSections());
        }

        List<MergedConcept> mergedConcepts = new ArrayList<MergedConcept>();
        for (Map.Entry<String, MergeBucket> entry : buckets.entrySet()) {
            MergeBucket mergeBucket = entry.getValue();
            List<String> sourcePaths = new ArrayList<String>(mergeBucket.sourcePaths);
            Collections.sort(sourcePaths);
            mergedConcepts.add(new MergedConcept(
                    entry.getKey(),
                    mergeBucket.title,
                    mergeBucket.description,
                    sourcePaths,
                    new ArrayList<String>(mergeBucket.snippets),
                    toConceptSections(mergeBucket.sections)
            ));
        }
        return mergedConcepts;
    }

    /**
     * 输出章节列表。
     *
     * @param sectionBuckets 章节桶映射
     * @return 章节列表
     */
    private List<ConceptSection> toConceptSections(Map<String, SectionBucket> sectionBuckets) {
        List<ConceptSection> sections = new ArrayList<ConceptSection>();
        for (SectionBucket sectionBucket : sectionBuckets.values()) {
            sections.add(sectionBucket.toConceptSection());
        }
        return sections;
    }

    /**
     * 归一化概念标识。
     *
     * @param conceptId 原始概念标识
     * @return 归一化后的概念标识
     */
    private String normalizeConceptId(String conceptId) {
        String normalized = conceptId.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.isEmpty()) {
            return "default";
        }
        return normalized;
    }

    /**
     * 选择更合适的展示标题。
     *
     * @param currentTitle 当前标题
     * @param candidateTitle 候选标题
     * @param conceptId 概念标识
     * @return 选中的标题
     */
    private String selectPreferredTitle(String currentTitle, String candidateTitle, String conceptId) {
        String normalizedCurrentTitle = normalizeTitle(currentTitle);
        String normalizedCandidateTitle = normalizeTitle(candidateTitle);
        if (normalizedCurrentTitle.isEmpty() && normalizedCandidateTitle.isEmpty()) {
            return buildFallbackTitle(conceptId);
        }
        if (normalizedCurrentTitle.isEmpty()) {
            return normalizedCandidateTitle;
        }
        if (normalizedCandidateTitle.isEmpty()) {
            return normalizedCurrentTitle;
        }
        if (normalizedCandidateTitle.length() > normalizedCurrentTitle.length()) {
            return normalizedCandidateTitle;
        }
        return normalizedCurrentTitle;
    }

    /**
     * 选择更丰富的概念描述。
     *
     * @param currentDescription 当前描述
     * @param candidateDescription 候选描述
     * @return 选中的描述
     */
    private String selectPreferredDescription(String currentDescription, String candidateDescription) {
        String normalizedCurrentDescription = normalizeDescription(currentDescription);
        String normalizedCandidateDescription = normalizeDescription(candidateDescription);
        if (normalizedCurrentDescription.isEmpty()) {
            return normalizedCandidateDescription;
        }
        if (normalizedCandidateDescription.isEmpty()) {
            return normalizedCurrentDescription;
        }
        if (normalizedCandidateDescription.length() > normalizedCurrentDescription.length()) {
            return normalizedCandidateDescription;
        }
        return normalizedCurrentDescription;
    }

    /**
     * 合并来源路径集合。
     *
     * @param mergeBucket 合并桶
     * @param sourcePaths 来源路径
     */
    private void mergeSourcePaths(MergeBucket mergeBucket, List<String> sourcePaths) {
        for (String sourcePath : sourcePaths) {
            mergeBucket.sourcePaths.add(sourcePath.trim().replace('\\', '/'));
        }
    }

    /**
     * 合并片段集合。
     *
     * @param mergeBucket 合并桶
     * @param snippets 原始片段列表
     */
    private void mergeSnippets(MergeBucket mergeBucket, List<String> snippets) {
        for (String snippet : snippets) {
            String normalizedSnippet = snippet.trim();
            if (!normalizedSnippet.isEmpty()) {
                mergeBucket.snippets.add(normalizedSnippet);
            }
        }
    }

    /**
     * 合并章节集合。
     *
     * @param mergeBucket 合并桶
     * @param sections 原始章节列表
     */
    private void mergeSections(MergeBucket mergeBucket, List<ConceptSection> sections) {
        for (ConceptSection section : sections) {
            String normalizedHeading = normalizeTitle(section.getHeading());
            if (normalizedHeading.isEmpty()) {
                continue;
            }

            SectionBucket sectionBucket = mergeBucket.sections.computeIfAbsent(
                    normalizedHeading,
                    key -> new SectionBucket(normalizedHeading)
            );
            sectionBucket.heading = selectPreferredTitle(sectionBucket.heading, section.getHeading(), normalizedHeading);
            mergeSectionContentLines(sectionBucket, section.getContentLines());
            mergeSectionSourceRefs(sectionBucket, section.getSourceRefs());
        }
    }

    /**
     * 合并单个章节内容行。
     *
     * @param sectionBucket 章节桶
     * @param contentLines 内容行
     */
    private void mergeSectionContentLines(SectionBucket sectionBucket, List<String> contentLines) {
        for (String contentLine : contentLines) {
            String normalizedContentLine = normalizeDescription(contentLine);
            if (!normalizedContentLine.isEmpty()) {
                sectionBucket.contentLines.add(normalizedContentLine);
            }
        }
    }

    /**
     * 合并单个章节来源引用。
     *
     * @param sectionBucket 章节桶
     * @param sourceRefs 来源引用
     */
    private void mergeSectionSourceRefs(SectionBucket sectionBucket, List<String> sourceRefs) {
        for (String sourceRef : sourceRefs) {
            String normalizedSourceRef = normalizeSourceRef(sourceRef);
            if (!normalizedSourceRef.isEmpty()) {
                sectionBucket.sourceRefs.add(normalizedSourceRef);
            }
        }
    }

    /**
     * 标准化标题文本。
     *
     * @param title 原始标题
     * @return 标准化后的标题
     */
    private String normalizeTitle(String title) {
        return title.trim().replaceAll("[-_]+", " ").replaceAll("\\s+", " ");
    }

    /**
     * 标准化描述文本。
     *
     * @param description 原始描述
     * @return 标准化描述
     */
    private String normalizeDescription(String description) {
        return description.trim().replaceAll("\\s+", " ");
    }

    /**
     * 标准化来源引用。
     *
     * @param sourceRef 原始来源引用
     * @return 标准化来源引用
     */
    private String normalizeSourceRef(String sourceRef) {
        return sourceRef.trim().replace('\\', '/');
    }

    /**
     * 构建回退标题。
     *
     * @param conceptId 概念标识
     * @return 回退标题
     */
    private String buildFallbackTitle(String conceptId) {
        String[] words = conceptId.split("-");
        List<String> titledWords = new ArrayList<String>();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            char firstChar = word.charAt(0);
            if (firstChar <= 127 && Character.isLetter(firstChar)) {
                titledWords.add(Character.toUpperCase(firstChar) + word.substring(1));
            }
            else {
                titledWords.add(word);
            }
        }
        return String.join(" ", titledWords);
    }

    /**
     * 合并桶
     *
     * 职责：累积单个概念的来源与片段
     *
     * @author xiexu
     */
    private static class MergeBucket {

        private String title;

        private String description;

        private final Set<String> sourcePaths = new LinkedHashSet<String>();

        private final Set<String> snippets = new LinkedHashSet<String>();

        private final Map<String, SectionBucket> sections = new LinkedHashMap<String, SectionBucket>();

        /**
         * 创建合并桶。
         *
         * @param title 标题
         * @param description 描述
         */
        private MergeBucket(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }

    /**
     * 章节桶
     *
     * 职责：累积同标题 section 的内容行
     *
     * @author xiexu
     */
    private static class SectionBucket {

        private String heading;

        private final Set<String> contentLines = new LinkedHashSet<String>();

        private final Set<String> sourceRefs = new LinkedHashSet<String>();

        /**
         * 创建章节桶。
         *
         * @param heading 章节标题
         */
        private SectionBucket(String heading) {
            this.heading = heading;
        }

        /**
         * 输出概念章节。
         *
         * @return 概念章节
         */
        private ConceptSection toConceptSection() {
            return new ConceptSection(
                    heading,
                    new ArrayList<String>(contentLines),
                    new ArrayList<String>(sourceRefs)
            );
        }
    }
}
