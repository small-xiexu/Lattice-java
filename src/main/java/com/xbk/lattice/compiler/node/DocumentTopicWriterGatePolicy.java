package com.xbk.lattice.compiler.node;

import com.xbk.lattice.compiler.domain.AnalyzedConcept;
import com.xbk.lattice.compiler.domain.ConceptSection;
import com.xbk.lattice.compiler.domain.RawSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 长文档专题 Writer gate 策略
 *
 * 职责：识别被拆成过多 topic 的长文档，并将其收敛为单个 overview concept，避免过度进入 Writer
 *
 * @author xiexu
 */
public class DocumentTopicWriterGatePolicy {

    private static final int TOPIC_COUNT_THRESHOLD = 8;

    private static final int MAX_REPRESENTATIVE_TOPICS = 12;

    /**
     * 收紧长文档 topic 路由。
     *
     * @param sortedSources 已排序源文件
     * @param topicAnalyzedConcepts 专题概念列表
     * @return 收紧后的概念列表
     */
    public List<AnalyzedConcept> rewrite(List<RawSource> sortedSources, List<AnalyzedConcept> topicAnalyzedConcepts) {
        if (sortedSources == null || sortedSources.isEmpty() || topicAnalyzedConcepts == null || topicAnalyzedConcepts.isEmpty()) {
            return topicAnalyzedConcepts == null ? List.of() : topicAnalyzedConcepts;
        }
        Map<String, RawSource> sourceByPath = new LinkedHashMap<String, RawSource>();
        for (RawSource rawSource : sortedSources) {
            sourceByPath.put(rawSource.getRelativePath(), rawSource);
        }
        Map<String, List<AnalyzedConcept>> conceptsBySourcePath = groupByPrimarySourcePath(topicAnalyzedConcepts);
        if (conceptsBySourcePath.isEmpty()) {
            return topicAnalyzedConcepts;
        }
        List<AnalyzedConcept> rewrittenConcepts = new ArrayList<AnalyzedConcept>();
        for (Map.Entry<String, List<AnalyzedConcept>> entry : conceptsBySourcePath.entrySet()) {
            String sourcePath = entry.getKey();
            List<AnalyzedConcept> concepts = entry.getValue();
            RawSource rawSource = sourceByPath.get(sourcePath);
            if (!shouldCollapse(rawSource, concepts)) {
                rewrittenConcepts.addAll(concepts);
                continue;
            }
            rewrittenConcepts.add(buildOverviewConcept(rawSource, concepts));
        }
        return rewrittenConcepts;
    }

    /**
     * 判断是否应收敛为 overview concept。
     *
     * @param rawSource 原始源文件
     * @param concepts 专题概念列表
     * @return 应收敛返回 true
     */
    private boolean shouldCollapse(RawSource rawSource, List<AnalyzedConcept> concepts) {
        if (rawSource == null || concepts == null) {
            return false;
        }
        return concepts.size() >= TOPIC_COUNT_THRESHOLD
                && rawSource.getContent() != null
                && !rawSource.getContent().isBlank();
    }

    /**
     * 构建文档 overview concept。
     *
     * @param rawSource 原始源文件
     * @param concepts 专题概念列表
     * @return overview concept
     */
    private AnalyzedConcept buildOverviewConcept(RawSource rawSource, List<AnalyzedConcept> concepts) {
        String conceptId = "document-overview-" + slugify(stripExtension(rawSource.getRelativePath()));
        String title = "Document Overview - " + extractFileNameWithoutExtension(rawSource.getRelativePath());
        String description = "Overview of long document "
                + rawSource.getRelativePath()
                + " with "
                + concepts.size()
                + " extracted topics.";
        List<String> sourcePaths = List.of(rawSource.getRelativePath());
        List<String> overviewLines = buildOverviewLines(rawSource, concepts);
        List<ConceptSection> sections = List.of(new ConceptSection(
                "Document Overview",
                overviewLines,
                List.of(rawSource.getRelativePath() + "#document-overview")
        ));
        return new AnalyzedConcept(conceptId, title, description, sourcePaths, overviewLines, sections);
    }

    /**
     * 构建 overview 内容行。
     *
     * @param rawSource 原始源文件
     * @param concepts 专题概念列表
     * @return overview 内容行
     */
    private List<String> buildOverviewLines(RawSource rawSource, List<AnalyzedConcept> concepts) {
        List<String> lines = new ArrayList<String>();
        lines.add("Source path: " + rawSource.getRelativePath());
        lines.add("Topic count: " + concepts.size());
        lines.add("Route: collapse over-fragmented document topics into one overview concept");
        List<String> titles = new ArrayList<String>();
        for (int index = 0; index < concepts.size() && index < MAX_REPRESENTATIVE_TOPICS; index++) {
            titles.add(concepts.get(index).getTitle());
        }
        lines.add("Representative topics: " + String.join(", ", titles));
        if (concepts.size() > MAX_REPRESENTATIVE_TOPICS) {
            lines.add("Additional topics: " + (concepts.size() - MAX_REPRESENTATIVE_TOPICS));
        }
        return lines;
    }

    /**
     * 按 primary source path 分组专题概念。
     *
     * @param topicAnalyzedConcepts 专题概念列表
     * @return 分组结果
     */
    private Map<String, List<AnalyzedConcept>> groupByPrimarySourcePath(List<AnalyzedConcept> topicAnalyzedConcepts) {
        Map<String, List<AnalyzedConcept>> conceptsBySourcePath = new LinkedHashMap<String, List<AnalyzedConcept>>();
        for (AnalyzedConcept analyzedConcept : topicAnalyzedConcepts) {
            List<String> sourcePaths = analyzedConcept.getSourcePaths();
            if (sourcePaths == null || sourcePaths.size() != 1) {
                conceptsBySourcePath.computeIfAbsent(
                        "__passthrough__" + conceptsBySourcePath.size(),
                        key -> new ArrayList<AnalyzedConcept>()
                ).add(analyzedConcept);
                continue;
            }
            conceptsBySourcePath.computeIfAbsent(sourcePaths.get(0), key -> new ArrayList<AnalyzedConcept>()).add(analyzedConcept);
        }
        return conceptsBySourcePath;
    }

    /**
     * 返回 topic 收敛阈值。
     *
     * @return topic 收敛阈值
     */
    public int getTopicCountThreshold() {
        return TOPIC_COUNT_THRESHOLD;
    }

    /**
     * 去掉文件扩展名。
     *
     * @param path 路径
     * @return 去扩展名后的路径
     */
    private String stripExtension(String path) {
        int slashIndex = path.lastIndexOf('/');
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex > slashIndex) {
            return path.substring(0, dotIndex);
        }
        return path;
    }

    /**
     * 提取无扩展名文件名。
     *
     * @param path 路径
     * @return 无扩展名文件名
     */
    private String extractFileNameWithoutExtension(String path) {
        String normalizedPath = path == null ? "" : path.trim().replace('\\', '/');
        int slashIndex = normalizedPath.lastIndexOf('/');
        String fileName = slashIndex >= 0 ? normalizedPath.substring(slashIndex + 1) : normalizedPath;
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex);
        }
        return fileName;
    }

    /**
     * 生成稳定 slug。
     *
     * @param value 原始值
     * @return slug
     */
    private String slugify(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isEmpty() ? "document" : normalized;
    }
}
