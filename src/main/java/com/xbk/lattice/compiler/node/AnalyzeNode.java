package com.xbk.lattice.compiler.node;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.domain.AnalyzedConcept;
import com.xbk.lattice.compiler.domain.ConceptSection;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.compiler.domain.SourceBatch;
import com.xbk.lattice.compiler.prompt.LatticePrompts;
import com.xbk.lattice.compiler.prompt.SchemaAwarePrompts;
import com.xbk.lattice.compiler.service.LlmGateway;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 最小分析节点
 *
 * 职责：把批次内容转换为可合并的概念对象
 *
 * @author xiexu
 */
public class AnalyzeNode {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final LlmGateway llmGateway;

    private final SchemaAwarePrompts schemaAwarePrompts;

    /**
     * 创建分析节点。
     */
    public AnalyzeNode() {
        this(null, null);
    }

    /**
     * 创建分析节点。
     *
     * @param llmGateway LLM 网关
     */
    public AnalyzeNode(LlmGateway llmGateway) {
        this(llmGateway, null);
    }

    /**
     * 创建分析节点。
     *
     * @param llmGateway LLM 网关
     * @param schemaAwarePrompts SCHEMA 感知 Prompt 服务
     */
    public AnalyzeNode(LlmGateway llmGateway, SchemaAwarePrompts schemaAwarePrompts) {
        this.llmGateway = llmGateway;
        this.schemaAwarePrompts = schemaAwarePrompts;
    }

    /**
     * 分析分组内的所有批次。
     *
     * @param groupKey 分组键
     * @param sourceBatches 批次列表
     * @return 分析后的概念列表
     */
    public List<AnalyzedConcept> analyze(String groupKey, List<SourceBatch> sourceBatches) {
        return analyze(groupKey, sourceBatches, null);
    }

    /**
     * 分析分组内的所有批次。
     *
     * @param groupKey 分组键
     * @param sourceBatches 批次列表
     * @param sourceDir 输入目录
     * @return 分析后的概念列表
     */
    public List<AnalyzedConcept> analyze(String groupKey, List<SourceBatch> sourceBatches, Path sourceDir) {
        List<AnalyzedConcept> analyzedConcepts = new ArrayList<AnalyzedConcept>();
        String conceptId = normalizeGroupKey(groupKey);
        String title = toTitle(conceptId, groupKey);

        for (SourceBatch sourceBatch : sourceBatches) {
            List<RawSource> sortedSources = sortSources(sourceBatch.getSources());
            List<String> sourcePaths = collectSourcePaths(sortedSources);
            List<AnalyzedConcept> structuredConcepts = analyzeStructuredConcepts(sortedSources, sourcePaths);
            if (!structuredConcepts.isEmpty()) {
                analyzedConcepts.addAll(structuredConcepts);
                continue;
            }

            List<AnalyzedConcept> llmAnalyzedConcepts = analyzeWithLlm(sortedSources, sourcePaths, sourceDir);
            if (!llmAnalyzedConcepts.isEmpty()) {
                analyzedConcepts.addAll(llmAnalyzedConcepts);
                continue;
            }

            analyzedConcepts.add(new AnalyzedConcept(
                    conceptId,
                    title,
                    "",
                    sourcePaths,
                    collectFallbackSnippets(sortedSources)
            ));
        }
        return analyzedConcepts;
    }

    /**
     * 使用 LLM 分析概念。
     *
     * @param sortedSources 已排序源文件
     * @param sourcePaths 来源路径
     * @return 分析结果
     */
    private List<AnalyzedConcept> analyzeWithLlm(List<RawSource> sortedSources, List<String> sourcePaths, Path sourceDir) {
        if (llmGateway == null || sortedSources.isEmpty()) {
            return new ArrayList<AnalyzedConcept>();
        }
        try {
            String systemPrompt = schemaAwarePrompts == null
                    ? LatticePrompts.SYSTEM_ANALYZE
                    : schemaAwarePrompts.getAnalyzePrompt(sourceDir);
            String llmResponse = llmGateway.compile(
                    "analyze",
                    systemPrompt,
                    buildAnalyzeUserPrompt(sortedSources)
            );
            List<StructuredConceptCandidate> conceptCandidates = parseStructuredConceptCandidates(llmResponse);
            if (conceptCandidates.isEmpty()) {
                return new ArrayList<AnalyzedConcept>();
            }
            return toAnalyzedConcepts(conceptCandidates, sourcePaths);
        }
        catch (RuntimeException ex) {
            return new ArrayList<AnalyzedConcept>();
        }
    }

    /**
     * 构建分析用户提示词。
     *
     * @param sortedSources 已排序源文件
     * @return 用户提示词
     */
    private String buildAnalyzeUserPrompt(List<RawSource> sortedSources) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Analyze these source materials and extract the knowledge structure:\n\n");
        for (RawSource rawSource : sortedSources) {
            promptBuilder.append("=== Source: ").append(rawSource.getRelativePath()).append(" ===").append("\n");
            promptBuilder.append(rawSource.getContent()).append("\n");
            promptBuilder.append("=== End: ").append(rawSource.getRelativePath()).append(" ===").append("\n\n");
        }
        return promptBuilder.toString().trim();
    }

    /**
     * 排序批次内源文件，确保分析结果稳定。
     *
     * @param rawSources 原始源文件
     * @return 排序后的源文件列表
     */
    private List<RawSource> sortSources(List<RawSource> rawSources) {
        List<RawSource> sortedSources = new ArrayList<RawSource>(rawSources);
        sortedSources.sort(Comparator.comparing(RawSource::getRelativePath));
        return sortedSources;
    }

    /**
     * 收集批次内所有来源路径。
     *
     * @param sortedSources 已排序源文件
     * @return 稳定来源路径列表
     */
    private List<String> collectSourcePaths(List<RawSource> sortedSources) {
        Set<String> sourcePaths = new LinkedHashSet<String>();
        for (RawSource rawSource : sortedSources) {
            sourcePaths.add(rawSource.getRelativePath());
        }
        return new ArrayList<String>(sourcePaths);
    }

    /**
     * 尝试从批次中提取结构化概念结果。
     *
     * @param sortedSources 已排序源文件
     * @param sourcePaths 批次来源路径
     * @return 结构化概念列表
     */
    private List<AnalyzedConcept> analyzeStructuredConcepts(List<RawSource> sortedSources, List<String> sourcePaths) {
        for (RawSource rawSource : sortedSources) {
            List<StructuredConceptCandidate> conceptCandidates = parseStructuredConceptCandidates(rawSource.getContent());
            if (!conceptCandidates.isEmpty()) {
                return toAnalyzedConcepts(conceptCandidates, sourcePaths);
            }
        }
        return new ArrayList<AnalyzedConcept>();
    }

    /**
     * 把结构化候选概念转换为分析结果。
     *
     * @param conceptCandidates 结构化候选概念
     * @param sourcePaths 批次来源路径
     * @return 分析结果
     */
    private List<AnalyzedConcept> toAnalyzedConcepts(
            List<StructuredConceptCandidate> conceptCandidates,
            List<String> sourcePaths
    ) {
        List<AnalyzedConcept> analyzedConcepts = new ArrayList<AnalyzedConcept>();
        for (StructuredConceptCandidate conceptCandidate : conceptCandidates) {
            List<ConceptSection> sections = applyDefaultSourceRefs(conceptCandidate.sections, sourcePaths);
            analyzedConcepts.add(new AnalyzedConcept(
                    conceptCandidate.conceptId,
                    conceptCandidate.title,
                    conceptCandidate.description,
                    new ArrayList<String>(sourcePaths),
                    new ArrayList<String>(conceptCandidate.snippets),
                    sections
            ));
        }
        return analyzedConcepts;
    }

    /**
     * 为未显式声明来源的章节补默认 sourceRef。
     *
     * @param sections 原始章节列表
     * @param sourcePaths 批次来源路径
     * @return 补齐来源后的章节列表
     */
    private List<ConceptSection> applyDefaultSourceRefs(List<ConceptSection> sections, List<String> sourcePaths) {
        List<ConceptSection> normalizedSections = new ArrayList<ConceptSection>();
        for (ConceptSection section : sections) {
            if (!section.getSourceRefs().isEmpty()) {
                normalizedSections.add(section);
                continue;
            }
            normalizedSections.add(new ConceptSection(
                    section.getHeading(),
                    section.getContentLines(),
                    buildDefaultSourceRefs(sourcePaths, section.getHeading())
            ));
        }
        return normalizedSections;
    }

    /**
     * 为章节构造默认来源引用。
     *
     * @param sourcePaths 批次来源路径
     * @param heading 章节标题
     * @return 默认来源引用列表
     */
    private List<String> buildDefaultSourceRefs(List<String> sourcePaths, String heading) {
        List<String> sourceRefs = new ArrayList<String>();
        for (String sourcePath : sourcePaths) {
            sourceRefs.add(sourcePath + "#" + heading.trim());
        }
        return sourceRefs;
    }

    /**
     * 解析结构化概念候选。
     *
     * @param content 原始文本
     * @return 候选概念列表
     */
    private List<StructuredConceptCandidate> parseStructuredConceptCandidates(String content) {
        try {
            return readConceptCandidatesFromJson(content);
        }
        catch (JsonProcessingException ex) {
            return salvageConceptCandidates(content);
        }
    }

    /**
     * 从完整 JSON 中读取概念候选。
     *
     * @param content 原始文本
     * @return 候选概念列表
     * @throws JsonProcessingException JSON 解析异常
     */
    private List<StructuredConceptCandidate> readConceptCandidatesFromJson(String content) throws JsonProcessingException {
        JsonNode rootNode = OBJECT_MAPPER.readTree(content);
        return readConceptCandidates(rootNode);
    }

    /**
     * 从 JSON 根节点中提取概念候选。
     *
     * @param rootNode JSON 根节点
     * @return 候选概念列表
     */
    private List<StructuredConceptCandidate> readConceptCandidates(JsonNode rootNode) {
        List<StructuredConceptCandidate> conceptCandidates = new ArrayList<StructuredConceptCandidate>();
        JsonNode conceptsNode = rootNode.get("concepts");
        if (conceptsNode == null || !conceptsNode.isArray()) {
            return conceptCandidates;
        }

        for (JsonNode conceptNode : conceptsNode) {
            StructuredConceptCandidate conceptCandidate = toStructuredConceptCandidate(conceptNode);
            if (conceptCandidate != null) {
                conceptCandidates.add(conceptCandidate);
            }
        }
        return conceptCandidates;
    }

    /**
     * 从截断 JSON 中抢救完整概念对象。
     *
     * @param content 原始文本
     * @return 候选概念列表
     */
    private List<StructuredConceptCandidate> salvageConceptCandidates(String content) {
        List<StructuredConceptCandidate> conceptCandidates = new ArrayList<StructuredConceptCandidate>();
        int conceptsIndex = content.indexOf("\"concepts\"");
        if (conceptsIndex < 0) {
            return conceptCandidates;
        }

        int arrayStartIndex = content.indexOf('[', conceptsIndex);
        if (arrayStartIndex < 0) {
            return conceptCandidates;
        }

        List<String> objectJsons = extractCompletedJsonObjects(content, arrayStartIndex + 1);
        for (String objectJson : objectJsons) {
            try {
                JsonNode conceptNode = OBJECT_MAPPER.readTree(objectJson);
                StructuredConceptCandidate conceptCandidate = toStructuredConceptCandidate(conceptNode);
                if (conceptCandidate != null) {
                    conceptCandidates.add(conceptCandidate);
                }
            }
            catch (JsonProcessingException ex) {
                // 抢救阶段允许跳过无法单独解析的碎片对象。
            }
        }
        return conceptCandidates;
    }

    /**
     * 从数组正文中提取已闭合的 JSON 对象。
     *
     * @param content 原始文本
     * @param startIndex 数组内容起始位置
     * @return 完整对象 JSON 列表
     */
    private List<String> extractCompletedJsonObjects(String content, int startIndex) {
        List<String> objectJsons = new ArrayList<String>();
        int objectStartIndex = -1;
        int braceDepth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int index = startIndex; index < content.length(); index++) {
            char currentChar = content.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (currentChar == '\\') {
                escaped = true;
                continue;
            }
            if (currentChar == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (currentChar == '{') {
                if (braceDepth == 0) {
                    objectStartIndex = index;
                }
                braceDepth++;
                continue;
            }
            if (currentChar == '}') {
                braceDepth--;
                if (braceDepth == 0 && objectStartIndex >= 0) {
                    objectJsons.add(content.substring(objectStartIndex, index + 1));
                    objectStartIndex = -1;
                }
                continue;
            }
            if (currentChar == ']' && braceDepth == 0) {
                break;
            }
        }
        return objectJsons;
    }

    /**
     * 把单个 JSON 概念节点转换为候选概念。
     *
     * @param conceptNode 概念节点
     * @return 候选概念，若字段不足则返回空
     */
    private StructuredConceptCandidate toStructuredConceptCandidate(JsonNode conceptNode) {
        if (conceptNode == null || !conceptNode.isObject()) {
            return null;
        }

        String conceptId = normalizeGroupKey(conceptNode.path("id").asText(""));
        String title = normalizeTitle(conceptNode.path("title").asText(""));
        if ("default".equals(conceptId) || title.isEmpty()) {
            return null;
        }
        String description = normalizeSnippet(conceptNode.path("description").asText(""));
        return new StructuredConceptCandidate(
                conceptId,
                title,
                description,
                collectStructuredSnippets(conceptNode),
                collectStructuredSections(conceptNode)
        );
    }

    /**
     * 收集结构化概念的片段列表。
     *
     * @param conceptNode 概念节点
     * @return 标准化片段列表
     */
    private List<String> collectStructuredSnippets(JsonNode conceptNode) {
        Set<String> snippets = new LinkedHashSet<String>();
        JsonNode snippetsNode = conceptNode.get("snippets");
        if (snippetsNode != null && snippetsNode.isArray()) {
            for (JsonNode snippetNode : snippetsNode) {
                if (!snippetNode.isTextual()) {
                    continue;
                }
                String snippet = normalizeSnippet(snippetNode.asText());
                if (!snippet.isEmpty()) {
                    snippets.add(snippet);
                }
            }
        }

        if (!snippets.isEmpty()) {
            return new ArrayList<String>(snippets);
        }

        String description = normalizeSnippet(conceptNode.path("description").asText(""));
        if (description.isEmpty()) {
            return new ArrayList<String>();
        }
        snippets.add(description);
        return new ArrayList<String>(snippets);
    }

    /**
     * 收集结构化概念的章节列表。
     *
     * @param conceptNode 概念节点
     * @return 标准化章节列表
     */
    private List<ConceptSection> collectStructuredSections(JsonNode conceptNode) {
        List<ConceptSection> sections = new ArrayList<ConceptSection>();
        JsonNode sectionsNode = conceptNode.get("sections");
        if (sectionsNode == null || !sectionsNode.isArray()) {
            return sections;
        }

        for (JsonNode sectionNode : sectionsNode) {
            ConceptSection section = toConceptSection(sectionNode);
            if (section != null) {
                sections.add(section);
            }
        }
        return sections;
    }

    /**
     * 把结构化 section 节点转换为概念章节。
     *
     * @param sectionNode section 节点
     * @return 概念章节，若内容不足则返回空
     */
    private ConceptSection toConceptSection(JsonNode sectionNode) {
        if (sectionNode == null || !sectionNode.isObject()) {
            return null;
        }

        String heading = normalizeTitle(sectionNode.path("heading").asText(""));
        if (heading.isEmpty()) {
            return null;
        }

        Set<String> contentLines = new LinkedHashSet<String>();
        JsonNode contentNode = sectionNode.get("content");
        if (contentNode != null && contentNode.isArray()) {
            for (JsonNode lineNode : contentNode) {
                if (!lineNode.isTextual()) {
                    continue;
                }
                String contentLine = normalizeSnippet(lineNode.asText());
                if (!contentLine.isEmpty()) {
                    contentLines.add(contentLine);
                }
            }
        }

        if (contentLines.isEmpty()) {
            return null;
        }
        return new ConceptSection(
                heading,
                new ArrayList<String>(contentLines),
                collectSectionSourceRefs(sectionNode)
        );
    }

    /**
     * 收集章节来源引用。
     *
     * @param sectionNode section 节点
     * @return 来源引用列表
     */
    private List<String> collectSectionSourceRefs(JsonNode sectionNode) {
        Set<String> sourceRefs = new LinkedHashSet<String>();
        JsonNode sourcesNode = sectionNode.get("sources");
        if (sourcesNode == null || !sourcesNode.isArray()) {
            return new ArrayList<String>();
        }

        for (JsonNode sourceNode : sourcesNode) {
            if (!sourceNode.isTextual()) {
                continue;
            }
            String sourceRef = normalizeSourceRef(sourceNode.asText());
            if (!sourceRef.isEmpty()) {
                sourceRefs.add(sourceRef);
            }
        }
        return new ArrayList<String>(sourceRefs);
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
     * 收集普通文本批次的回退片段。
     *
     * @param sortedSources 已排序源文件
     * @return 回退片段列表
     */
    private List<String> collectFallbackSnippets(List<RawSource> sortedSources) {
        Set<String> snippets = new LinkedHashSet<String>();
        for (RawSource rawSource : sortedSources) {
            String snippet = normalizeSnippet(rawSource.getContent());
            if (!snippet.isEmpty()) {
                snippets.add(snippet);
            }
        }
        return new ArrayList<String>(snippets);
    }

    /**
     * 归一化分组键为概念标识。
     *
     * @param groupKey 分组键
     * @return 概念标识
     */
    private String normalizeGroupKey(String groupKey) {
        String normalized = groupKey.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.isEmpty()) {
            return "default";
        }
        return normalized;
    }

    /**
     * 把分组键转换为标题。
     *
     * @param conceptId 归一化后的概念标识
     * @param groupKey 原始分组键
     * @return 标题
     */
    private String toTitle(String conceptId, String groupKey) {
        if (conceptId.isEmpty()) {
            return groupKey.trim();
        }
        String[] words = conceptId.split("-");
        List<String> titledWords = new ArrayList<String>();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            titledWords.add(toTitleWord(word));
        }
        return String.join(" ", titledWords);
    }

    /**
     * 标准化结构化标题。
     *
     * @param title 原始标题
     * @return 标准化标题
     */
    private String normalizeTitle(String title) {
        return title.trim().replaceAll("\\s+", " ");
    }

    /**
     * 把单个词转换为展示标题。
     *
     * @param word 单词
     * @return 展示标题
     */
    private String toTitleWord(String word) {
        char firstChar = word.charAt(0);
        if (firstChar <= 127 && Character.isLetter(firstChar)) {
            return Character.toUpperCase(firstChar) + word.substring(1);
        }
        return word;
    }

    /**
     * 标准化单个片段内容。
     *
     * @param snippet 原始片段
     * @return 标准化后的片段
     */
    private String normalizeSnippet(String snippet) {
        return snippet.trim();
    }

    /**
     * 结构化概念候选。
     *
     * 职责：承载 JSON 解析或截断抢救得到的最小概念信息
     *
     * @author xiexu
     */
    private static final class StructuredConceptCandidate {

        private final String conceptId;

        private final String title;

        private final String description;

        private final List<String> snippets;

        private final List<ConceptSection> sections;

        /**
         * 创建结构化概念候选。
         *
         * @param conceptId 概念标识
         * @param title 标题
         * @param description 描述
         * @param snippets 片段列表
         * @param sections 章节列表
         */
        private StructuredConceptCandidate(
                String conceptId,
                String title,
                String description,
                List<String> snippets,
                List<ConceptSection> sections
        ) {
            this.conceptId = conceptId;
            this.title = title;
            this.description = description;
            this.snippets = snippets;
            this.sections = sections;
        }
    }
}
