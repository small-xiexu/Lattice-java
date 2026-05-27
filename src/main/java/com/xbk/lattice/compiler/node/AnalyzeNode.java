package com.xbk.lattice.compiler.node;

import com.xbk.lattice.shared.json.JsonMappers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.compiler.domain.AnalyzePayload;
import com.xbk.lattice.compiler.domain.AnalyzedConcept;
import com.xbk.lattice.compiler.domain.ConceptSection;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.compiler.domain.SourceBatch;
import com.xbk.lattice.compiler.prompt.LatticePrompts;
import com.xbk.lattice.compiler.prompt.SchemaAwarePrompts;
import com.xbk.lattice.compiler.service.LlmGateway;
import com.xbk.lattice.llm.error.LlmRetryExhaustedException;
import com.xbk.lattice.llm.service.LlmRetrySupport;
import com.xbk.lattice.shared.text.DocumentTitleSupport;

import java.net.SocketTimeoutException;
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

    private static final ObjectMapper OBJECT_MAPPER = JsonMappers.defaultMapper();

    private static final String COMPILE_SCENE = "compile";

    private static final String WRITER_ROLE = "writer";

    private static final String ANALYSIS_MODE_STRUCTURED = "STRUCTURED";

    private static final String ANALYSIS_MODE_TABLE_OVERVIEW = "TABLE_OVERVIEW";

    private static final String ANALYSIS_MODE_LIGHTWEIGHT_SMALL_DOC = "LIGHTWEIGHT_SMALL_DOC";

    private static final String ANALYSIS_MODE_TOPIC = "TOPIC";

    private static final String ANALYSIS_MODE_LLM = "LLM";

    private static final String ANALYSIS_MODE_FALLBACK = "FALLBACK";

    private static final String FAILURE_REASON_EMPTY_RESULT = "EMPTY_RESULT";

    private static final String FAILURE_REASON_PARSE_FAILED = "PARSE_FAILED";

    private static final String FAILURE_REASON_TIMEOUT = "TIMEOUT";

    private static final String FAILURE_REASON_ROUTE_UNAVAILABLE = "ROUTE_UNAVAILABLE";

    private static final String FAILURE_REASON_LLM_CALL_FAILED = "LLM_CALL_FAILED";

    private static final String TITLE_SOURCE_DOCUMENT_TITLE = "DOCUMENT_TITLE";

    private static final String TITLE_SOURCE_FILE_STEM = "FILE_STEM";

    private static final String TITLE_SOURCE_GROUP_KEY = "GROUP_KEY";

    private static final String TITLE_SOURCE_SHARED_SOURCE_TITLE = "SHARED_SOURCE_TITLE";

    private static final String TITLE_SOURCE_NEUTRAL_MULTI_SOURCE = "NEUTRAL_MULTI_SOURCE";

    private final LlmGateway llmGateway;

    private final SchemaAwarePrompts schemaAwarePrompts;

    private final CompilerProperties.DocumentTopics documentTopics;

    private final DocumentTopicConceptExtractor documentTopicConceptExtractor;

    private final StructuredTableWriterGatePolicy structuredTableWriterGatePolicy;

    private final DocumentTopicWriterGatePolicy documentTopicWriterGatePolicy;

    /**
     * 创建分析节点。
     */
    public AnalyzeNode() {
        this(null, null, (CompilerProperties.DocumentTopics) null);
    }

    /**
     * 创建分析节点。
     *
     * @param llmGateway LLM 网关
     */
    public AnalyzeNode(LlmGateway llmGateway) {
        this(llmGateway, null, (CompilerProperties.DocumentTopics) null);
    }

    /**
     * 创建分析节点。
     *
     * @param llmGateway LLM 网关
     * @param schemaAwarePrompts SCHEMA 感知 Prompt 服务
     */
    public AnalyzeNode(LlmGateway llmGateway, SchemaAwarePrompts schemaAwarePrompts) {
        this(llmGateway, schemaAwarePrompts, (CompilerProperties.DocumentTopics) null);
    }

    /**
     * 创建分析节点。
     *
     * @param llmGateway LLM 网关
     * @param schemaAwarePrompts SCHEMA 感知 Prompt 服务
     * @param compilerProperties 编译配置
     */
    public AnalyzeNode(
            LlmGateway llmGateway,
            SchemaAwarePrompts schemaAwarePrompts,
            CompilerProperties compilerProperties
    ) {
        this(llmGateway, schemaAwarePrompts, getDocumentTopics(compilerProperties));
    }

    /**
     * 创建分析节点。
     *
     * @param llmGateway LLM 网关
     * @param schemaAwarePrompts SCHEMA 感知 Prompt 服务
     * @param documentTopics 长文档专题拆分配置
     */
    private AnalyzeNode(
            LlmGateway llmGateway,
            SchemaAwarePrompts schemaAwarePrompts,
            CompilerProperties.DocumentTopics documentTopics
    ) {
        this.llmGateway = llmGateway;
        this.schemaAwarePrompts = schemaAwarePrompts;
        this.documentTopics = documentTopics == null ? new CompilerProperties.DocumentTopics() : documentTopics;
        this.documentTopicConceptExtractor = new DocumentTopicConceptExtractor(this.documentTopics);
        this.structuredTableWriterGatePolicy = new StructuredTableWriterGatePolicy();
        this.documentTopicWriterGatePolicy = new DocumentTopicWriterGatePolicy();
    }

    /**
     * 从编译配置中读取长文档专题配置。
     *
     * @param compilerProperties 编译配置
     * @return 长文档专题配置
     */
    private static CompilerProperties.DocumentTopics getDocumentTopics(CompilerProperties compilerProperties) {
        if (compilerProperties == null) {
            return null;
        }
        return compilerProperties.getDocumentTopics();
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

        for (SourceBatch sourceBatch : sourceBatches) {
            List<RawSource> sortedSources = sortSources(sourceBatch.getSources());
            List<String> sourcePaths = collectSourcePaths(sortedSources);
            List<AnalyzedConcept> structuredConcepts = analyzeStructuredConcepts(sortedSources, sourcePaths);
            if (!structuredConcepts.isEmpty()) {
                analyzedConcepts.addAll(tagAnalysisMode(structuredConcepts, ANALYSIS_MODE_STRUCTURED, null));
                continue;
            }

            List<AnalyzedConcept> structuredTableOverviewConcepts =
                    structuredTableWriterGatePolicy.buildOverviewConcepts(sortedSources);
            if (!structuredTableOverviewConcepts.isEmpty()) {
                analyzedConcepts.addAll(tagAnalysisMode(structuredTableOverviewConcepts, ANALYSIS_MODE_TABLE_OVERVIEW, null));
                continue;
            }

            AnalyzeRouteResult analyzeRouteResult = analyzeLightweightAndTopicConcepts(groupKey, sortedSources);
            if (!analyzeRouteResult.getAnalyzedConcepts().isEmpty() && analyzeRouteResult.getRemainingSources().isEmpty()) {
                analyzedConcepts.addAll(analyzeRouteResult.getAnalyzedConcepts());
                continue;
            }

            List<RawSource> llmSources = analyzeRouteResult.getRemainingSources().isEmpty()
                    ? sortedSources
                    : analyzeRouteResult.getRemainingSources();
            List<String> llmSourcePaths = collectSourcePaths(llmSources);
            AnalyzeAttemptResult llmAnalyzeAttempt = analyzeWithLlm(llmSources, llmSourcePaths, sourceDir);
            if (!llmAnalyzeAttempt.getAnalyzedConcepts().isEmpty()) {
                analyzedConcepts.addAll(analyzeRouteResult.getAnalyzedConcepts());
                analyzedConcepts.addAll(tagAnalysisMode(
                        llmAnalyzeAttempt.getAnalyzedConcepts(),
                        ANALYSIS_MODE_LLM,
                        llmAnalyzeAttempt.getFailureReason()
                ));
                continue;
            }

            if (!analyzeRouteResult.getAnalyzedConcepts().isEmpty()) {
                analyzedConcepts.addAll(analyzeRouteResult.getAnalyzedConcepts());
                analyzedConcepts.add(buildFallbackConcept(
                        conceptId,
                        groupKey,
                        llmSourcePaths,
                        llmSources,
                        llmAnalyzeAttempt.getFailureReason()
                ));
                continue;
            }

            analyzedConcepts.add(buildFallbackConcept(
                    conceptId,
                    groupKey,
                    sourcePaths,
                    sortedSources,
                    llmAnalyzeAttempt.getFailureReason()
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
    private AnalyzeAttemptResult analyzeWithLlm(List<RawSource> sortedSources, List<String> sourcePaths, Path sourceDir) {
        if (llmGateway == null || sortedSources.isEmpty()) {
            return AnalyzeAttemptResult.empty(FAILURE_REASON_ROUTE_UNAVAILABLE);
        }
        try {
            String systemPrompt = schemaAwarePrompts == null
                    ? LatticePrompts.SYSTEM_ANALYZE
                    : schemaAwarePrompts.getAnalyzePrompt(sourceDir);
            String llmResponse = llmGateway.generateText(
                    COMPILE_SCENE,
                    WRITER_ROLE,
                    "analyze",
                    systemPrompt,
                    buildAnalyzeUserPrompt(sortedSources)
            );
            List<StructuredConceptCandidate> conceptCandidates = parseStructuredConceptCandidates(llmResponse);
            if (conceptCandidates.isEmpty()) {
                String failureReason = hasStructuredConceptSignal(llmResponse)
                        ? FAILURE_REASON_PARSE_FAILED
                        : FAILURE_REASON_EMPTY_RESULT;
                return AnalyzeAttemptResult.empty(failureReason);
            }
            return AnalyzeAttemptResult.success(toAnalyzedConcepts(conceptCandidates, sourcePaths));
        }
        catch (RuntimeException ex) {
            return AnalyzeAttemptResult.empty(resolveFailureReason(ex));
        }
    }

    /**
     * 在规则路径内组合小资料轻量概念与长文档 topic 概念。
     *
     * @param groupKey 分组键
     * @param sortedSources 已排序源文件
     * @return 路由结果
     */
    private AnalyzeRouteResult analyzeLightweightAndTopicConcepts(String groupKey, List<RawSource> sortedSources) {
        List<AnalyzedConcept> routeConcepts = new ArrayList<AnalyzedConcept>();
        List<RawSource> remainingSources = new ArrayList<RawSource>();
        for (RawSource rawSource : sortedSources) {
            if (documentTopicConceptExtractor.matchesTopicGate(rawSource)) {
                List<AnalyzedConcept> topicAnalyzedConcepts = documentTopicConceptExtractor.extract(groupKey, List.of(rawSource));
                if (!topicAnalyzedConcepts.isEmpty()) {
                    routeConcepts.addAll(tagAnalysisMode(
                            documentTopicWriterGatePolicy.rewrite(List.of(rawSource), topicAnalyzedConcepts),
                            ANALYSIS_MODE_TOPIC,
                            null
                    ));
                    continue;
                }
                remainingSources.add(rawSource);
                continue;
            }

            AnalyzedConcept lightweightConcept = buildLightweightSmallDocConcept(groupKey, rawSource);
            if (lightweightConcept != null) {
                routeConcepts.add(lightweightConcept.withAnalysisMetadata(ANALYSIS_MODE_LIGHTWEIGHT_SMALL_DOC, null));
                continue;
            }
            remainingSources.add(rawSource);
        }
        return new AnalyzeRouteResult(routeConcepts, remainingSources);
    }

    /**
     * 为单个小资料构建轻量概念。
     *
     * @param groupKey 分组键
     * @param rawSource 源文件
     * @return 轻量概念；信号不足时返回空
     */
    private AnalyzedConcept buildLightweightSmallDocConcept(String groupKey, RawSource rawSource) {
        if (rawSource == null || rawSource.getContent() == null || rawSource.getContent().isBlank()) {
            return null;
        }
        TitleResolution titleResolution = resolveLightweightTitle(rawSource);
        if (titleResolution == null || titleResolution.getTitle().isEmpty()) {
            return null;
        }
        String title = titleResolution.getTitle();
        List<String> contentLines = collectLightweightContentLines(rawSource, title);
        if (!hasLightweightSignal(contentLines)) {
            return null;
        }
        String conceptId = buildLightweightConceptId(groupKey, rawSource);
        String description = buildLightweightDescription(contentLines);
        List<String> sourcePaths = List.of(rawSource.getRelativePath());
        String sectionHeading = title;
        List<ConceptSection> sections = List.of(new ConceptSection(
                sectionHeading,
                contentLines,
                buildDefaultSourceRefs(sourcePaths, sectionHeading)
        ));
        return new AnalyzedConcept(
                conceptId,
                title,
                description,
                sourcePaths,
                buildLightweightSnippets(contentLines, description),
                sections,
                null,
                null,
                titleResolution.getTitleSource()
        );
    }

    /**
     * 解析小资料展示标题。
     *
     * @param rawSource 源文件
     * @return 轻量概念标题
     */
    private TitleResolution resolveLightweightTitle(RawSource rawSource) {
        return resolveSourceTitleCandidate(rawSource);
    }

    /**
     * 构建小资料概念标识。
     *
     * @param groupKey 分组键
     * @param rawSource 源文件
     * @return 轻量概念标识
     */
    private String buildLightweightConceptId(String groupKey, RawSource rawSource) {
        String normalizedGroupKey = normalizeGroupKey(groupKey);
        String sourceStem = normalizeGroupKey(DocumentTitleSupport.resolveFileNameTitle(rawSource.getRelativePath()));
        if ("default".equals(sourceStem)) {
            return normalizedGroupKey;
        }
        if (normalizedGroupKey.equals(sourceStem)) {
            return sourceStem;
        }
        return normalizeGroupKey(normalizedGroupKey + "-" + sourceStem);
    }

    /**
     * 收集小资料正文中的高信息量内容行。
     *
     * @param rawSource 源文件
     * @param title 概念标题
     * @return 内容行列表
     */
    private List<String> collectLightweightContentLines(RawSource rawSource, String title) {
        Set<String> contentLines = new LinkedHashSet<String>();
        String normalizedTitle = normalizeComparableText(title);
        String[] lines = rawSource.getContent().split("\\R", -1);
        int scannedLineCount = 0;
        for (String line : lines) {
            if (scannedLineCount >= documentTopics.getLightweightMaxContentScanLines()
                    || contentLines.size() >= documentTopics.getLightweightMaxContentLines()) {
                break;
            }
            String normalizedLine = normalizeLightweightLine(line);
            if (normalizedLine.isEmpty() || isLightweightMarkerLine(normalizedLine)) {
                continue;
            }
            if (!normalizedTitle.isEmpty() && normalizeComparableText(normalizedLine).equals(normalizedTitle)) {
                continue;
            }
            contentLines.add(truncate(normalizedLine, documentTopics.getLightweightMaxLineChars()));
            scannedLineCount++;
        }
        return new ArrayList<String>(contentLines);
    }

    /**
     * 判断当前内容是否具备轻量概念信号。
     *
     * @param contentLines 内容行
     * @return 信号足够返回 true
     */
    private boolean hasLightweightSignal(List<String> contentLines) {
        if (contentLines == null || contentLines.isEmpty()) {
            return false;
        }
        int totalChars = 0;
        for (String contentLine : contentLines) {
            totalChars += normalizeSnippet(contentLine).length();
        }
        return totalChars >= documentTopics.getLightweightMinTotalChars()
                || (contentLines.size() >= documentTopics.getLightweightMinLineCount()
                && totalChars >= documentTopics.getLightweightMinMultiLineChars());
    }

    /**
     * 构建轻量概念描述。
     *
     * @param contentLines 内容行
     * @return 描述文本
     */
    private String buildLightweightDescription(List<String> contentLines) {
        List<String> descriptionLines = new ArrayList<String>();
        int totalChars = 0;
        for (String contentLine : contentLines) {
            if (descriptionLines.size() >= 3) {
                break;
            }
            String normalizedLine = normalizeSnippet(contentLine);
            if (normalizedLine.isEmpty()) {
                continue;
            }
            descriptionLines.add(normalizedLine);
            totalChars += normalizedLine.length();
            if (totalChars >= documentTopics.getLightweightMaxDescriptionChars()) {
                break;
            }
        }
        return truncate(String.join(" ", descriptionLines), documentTopics.getLightweightMaxDescriptionChars());
    }

    /**
     * 构建轻量概念片段。
     *
     * @param contentLines 内容行
     * @param description 描述文本
     * @return 片段列表
     */
    private List<String> buildLightweightSnippets(List<String> contentLines, String description) {
        Set<String> snippets = new LinkedHashSet<String>();
        String normalizedDescription = normalizeSnippet(description);
        if (!normalizedDescription.isEmpty()) {
            snippets.add(normalizedDescription);
        }
        for (String contentLine : contentLines) {
            if (snippets.size() >= 3) {
                break;
            }
            String normalizedLine = normalizeSnippet(contentLine);
            if (!normalizedLine.isEmpty()) {
                snippets.add(normalizedLine);
            }
        }
        return new ArrayList<String>(snippets);
    }

    /**
     * 标准化小资料单行文本。
     *
     * @param line 原始单行文本
     * @return 标准化后的单行文本
     */
    private String normalizeLightweightLine(String line) {
        if (line == null) {
            return "";
        }
        String normalizedLine = line.trim();
        normalizedLine = normalizedLine.replaceAll("^#{1,6}\\s*", "");
        normalizedLine = normalizedLine.replaceAll("\\s*#+\\s*$", "");
        return normalizedLine.replaceAll("\\s+", " ").trim();
    }

    /**
     * 判断当前单行是否只是版面标记。
     *
     * @param line 标准化后的单行文本
     * @return 仅为标记返回 true
     */
    private boolean isLightweightMarkerLine(String line) {
        String normalizedLine = normalizeSnippet(line);
        if (normalizedLine.isEmpty()) {
            return true;
        }
        String lowerCaseLine = normalizedLine.toLowerCase(Locale.ROOT);
        return normalizedLine.equals("---")
                || normalizedLine.startsWith("```")
                || normalizedLine.startsWith("~~~")
                || lowerCaseLine.matches("^===\\s*page\\s*:\\s*\\d+\\s*===$")
                || lowerCaseLine.matches("^===\\s*sheet\\s*:\\s*.+===$")
                || normalizedLine.matches("^---\\s+.+\\s+---$");
    }

    /**
     * 归一化用于去重比较的文本。
     *
     * @param value 原始文本
     * @return 去除版式噪音后的比较文本
     */
    private String normalizeComparableText(String value) {
        return normalizeTitle(value)
                .replaceAll("[#=_:\\-\\s]+", "")
                .toLowerCase(Locale.ROOT);
    }

    /**
     * 截断文本，避免 section 行过长。
     *
     * @param value 原始文本
     * @param maxChars 最大字符数
     * @return 截断后的文本
     */
    private String truncate(String value, int maxChars) {
        String normalizedValue = normalizeSnippet(value);
        if (normalizedValue.length() <= maxChars) {
            return normalizedValue;
        }
        return normalizedValue.substring(0, maxChars).trim();
    }

    /**
     * 统一写入 Analyze 路由元数据。
     *
     * @param analyzedConcepts 分析结果
     * @param analysisMode Analyze 概念生成模式
     * @param failureReason Analyze 失败原因
     * @return 带元数据的分析结果
     */
    private List<AnalyzedConcept> tagAnalysisMode(
            List<AnalyzedConcept> analyzedConcepts,
            String analysisMode,
            String failureReason
    ) {
        List<AnalyzedConcept> taggedConcepts = new ArrayList<AnalyzedConcept>();
        for (AnalyzedConcept analyzedConcept : analyzedConcepts) {
            taggedConcepts.add(analyzedConcept.withAnalysisMetadata(analysisMode, failureReason));
        }
        return taggedConcepts;
    }

    /**
     * 构建 fallback 概念。
     *
     * @param conceptId 概念标识
     * @param title 标题
     * @param sourcePaths 来源路径
     * @param sortedSources 已排序源文件
     * @param failureReason Analyze 失败原因
     * @return fallback 概念
     */
    private AnalyzedConcept buildFallbackConcept(
            String conceptId,
            String groupKey,
            List<String> sourcePaths,
            List<RawSource> sortedSources,
            String failureReason
    ) {
        TitleResolution titleResolution = resolveFallbackTitle(groupKey, sortedSources);
        return new AnalyzedConcept(
                conceptId,
                titleResolution.getTitle(),
                "",
                sourcePaths,
                collectFallbackSnippets(sortedSources),
                new ArrayList<ConceptSection>(),
                ANALYSIS_MODE_FALLBACK,
                failureReason,
                titleResolution.getTitleSource()
        );
    }

    /**
     * 判断模型输出是否至少包含结构化概念信号。
     *
     * @param llmResponse 模型输出
     * @return 存在结构化概念信号返回 true
     */
    private boolean hasStructuredConceptSignal(String llmResponse) {
        if (llmResponse == null) {
            return false;
        }
        String normalizedResponse = llmResponse.trim();
        return normalizedResponse.contains("\"concepts\"")
                || normalizedResponse.contains("\"title\"")
                || normalizedResponse.contains("\"id\"");
    }

    /**
     * 解析 LLM 失败原因。
     *
     * @param exception 运行时异常
     * @return 失败原因
     */
    private String resolveFailureReason(RuntimeException exception) {
        Throwable rootCause = rootCause(exception);
        if (rootCause instanceof SocketTimeoutException) {
            return FAILURE_REASON_TIMEOUT;
        }
        String errorCode = LlmRetrySupport.resolveErrorCode(exception, false);
        if ("LLM_RETRY_EXHAUSTED".equals(errorCode) && exception instanceof LlmRetryExhaustedException) {
            return rootCause instanceof SocketTimeoutException
                    ? FAILURE_REASON_TIMEOUT
                    : FAILURE_REASON_LLM_CALL_FAILED;
        }
        if ("LLM_REQUEST_TIMEOUT".equals(errorCode)) {
            return FAILURE_REASON_TIMEOUT;
        }
        if (errorCode != null && errorCode.contains("ROUTE")) {
            return FAILURE_REASON_ROUTE_UNAVAILABLE;
        }
        return FAILURE_REASON_LLM_CALL_FAILED;
    }

    /**
     * 查找根因异常。
     *
     * @param throwable 异常
     * @return 根因
     */
    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
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
        String normalizedContent = unwrapJsonCodeFence(content);
        try {
            return readConceptCandidatesFromJson(normalizedContent);
        }
        catch (JsonProcessingException ex) {
            return salvageConceptCandidates(normalizedContent);
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
        AnalyzePayload analyzePayload = OBJECT_MAPPER.readValue(content, AnalyzePayload.class);
        return readConceptCandidates(analyzePayload);
    }

    /**
     * 从 Analyze 结构化载荷中提取概念候选。
     *
     * @param analyzePayload Analyze 结构化载荷
     * @return 候选概念列表
     */
    private List<StructuredConceptCandidate> readConceptCandidates(AnalyzePayload analyzePayload) {
        List<StructuredConceptCandidate> conceptCandidates = new ArrayList<StructuredConceptCandidate>();
        if (analyzePayload == null || analyzePayload.getConcepts().isEmpty()) {
            return conceptCandidates;
        }

        for (AnalyzePayload.AnalyzeConceptPayload conceptPayload : analyzePayload.getConcepts()) {
            StructuredConceptCandidate conceptCandidate = toStructuredConceptCandidate(conceptPayload);
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
                AnalyzePayload.AnalyzeConceptPayload conceptPayload = OBJECT_MAPPER.readValue(
                        objectJson,
                        AnalyzePayload.AnalyzeConceptPayload.class
                );
                StructuredConceptCandidate conceptCandidate = toStructuredConceptCandidate(conceptPayload);
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
     * @param conceptPayload 概念载荷
     * @return 候选概念，若字段不足则返回空
     */
    private StructuredConceptCandidate toStructuredConceptCandidate(AnalyzePayload.AnalyzeConceptPayload conceptPayload) {
        if (conceptPayload == null) {
            return null;
        }

        String conceptId = normalizeGroupKey(conceptPayload.getId());
        String title = normalizeTitle(conceptPayload.getTitle());
        if ("default".equals(conceptId) || title.isEmpty()) {
            return null;
        }
        String description = normalizeSnippet(conceptPayload.getDescription());
        return new StructuredConceptCandidate(
                conceptId,
                title,
                description,
                collectStructuredSnippets(conceptPayload),
                collectStructuredSections(conceptPayload)
        );
    }

    /**
     * 收集结构化概念的片段列表。
     *
     * @param conceptPayload 概念载荷
     * @return 标准化片段列表
     */
    private List<String> collectStructuredSnippets(AnalyzePayload.AnalyzeConceptPayload conceptPayload) {
        Set<String> snippets = new LinkedHashSet<String>();
        for (String rawSnippet : conceptPayload.getSnippets()) {
            String snippet = normalizeSnippet(rawSnippet);
            if (!snippet.isEmpty()) {
                snippets.add(snippet);
            }
        }

        if (!snippets.isEmpty()) {
            return new ArrayList<String>(snippets);
        }

        String description = normalizeSnippet(conceptPayload.getDescription());
        if (description.isEmpty()) {
            return new ArrayList<String>();
        }
        snippets.add(description);
        return new ArrayList<String>(snippets);
    }

    /**
     * 收集结构化概念的章节列表。
     *
     * @param conceptPayload 概念载荷
     * @return 标准化章节列表
     */
    private List<ConceptSection> collectStructuredSections(AnalyzePayload.AnalyzeConceptPayload conceptPayload) {
        List<ConceptSection> sections = new ArrayList<ConceptSection>();
        for (AnalyzePayload.AnalyzeSectionPayload sectionPayload : conceptPayload.getSections()) {
            ConceptSection section = toConceptSection(sectionPayload);
            if (section != null) {
                sections.add(section);
            }
        }
        return sections;
    }

    /**
     * 把结构化 section 节点转换为概念章节。
     *
     * @param sectionPayload section 载荷
     * @return 概念章节，若内容不足则返回空
     */
    private ConceptSection toConceptSection(AnalyzePayload.AnalyzeSectionPayload sectionPayload) {
        if (sectionPayload == null) {
            return null;
        }

        String heading = normalizeTitle(sectionPayload.getHeading());
        if (heading.isEmpty()) {
            return null;
        }

        Set<String> contentLines = new LinkedHashSet<String>();
        for (String rawContentLine : sectionPayload.getContent()) {
            String contentLine = normalizeSnippet(rawContentLine);
            if (!contentLine.isEmpty()) {
                contentLines.add(contentLine);
            }
        }

        if (contentLines.isEmpty()) {
            return null;
        }
        return new ConceptSection(
                heading,
                new ArrayList<String>(contentLines),
                collectSectionSourceRefs(sectionPayload)
        );
    }

    /**
     * 收集章节来源引用。
     *
     * @param sectionPayload section 载荷
     * @return 来源引用列表
     */
    private List<String> collectSectionSourceRefs(AnalyzePayload.AnalyzeSectionPayload sectionPayload) {
        Set<String> sourceRefs = new LinkedHashSet<String>();
        for (String rawSourceRef : sectionPayload.getSources()) {
            String sourceRef = normalizeSourceRef(rawSourceRef);
            if (!sourceRef.isEmpty()) {
                sourceRefs.add(sourceRef);
            }
        }
        return new ArrayList<String>(sourceRefs);
    }

    /**
     * 从 Markdown code fence 中提取 JSON 主体。
     *
     * @param content 原始文本
     * @return 归一化后的 JSON 文本
     */
    private String unwrapJsonCodeFence(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.trim();
        int fenceStartIndex = normalized.indexOf("```");
        if (fenceStartIndex < 0) {
            return normalized;
        }
        int firstLineBreakIndex = normalized.indexOf('\n', fenceStartIndex);
        if (firstLineBreakIndex < 0) {
            return normalized;
        }
        int closingFenceIndex = normalized.indexOf("```", firstLineBreakIndex + 1);
        if (closingFenceIndex < 0) {
            return normalized;
        }
        return normalized.substring(firstLineBreakIndex + 1, closingFenceIndex).trim();
    }

    /**
     * 标准化来源引用。
     *
     * @param sourceRef 原始来源引用
     * @return 标准化来源引用
     */
    private String normalizeSourceRef(String sourceRef) {
        if (sourceRef == null) {
            return "";
        }
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
     * 解析 fallback 标题及其来源。
     *
     * @param groupKey 分组键
     * @param sortedSources 已排序源文件
     * @return 标题解析结果
     */
    private TitleResolution resolveFallbackTitle(String groupKey, List<RawSource> sortedSources) {
        if (sortedSources.size() == 1) {
            return resolveSingleSourceTitle(sortedSources.get(0), groupKey);
        }
        TitleResolution sharedSourceTitle = resolveSharedSourceTitle(sortedSources);
        if (sharedSourceTitle != null) {
            return sharedSourceTitle;
        }
        return new TitleResolution(
                buildNeutralMultiSourceTitle(sortedSources.size()),
                TITLE_SOURCE_NEUTRAL_MULTI_SOURCE
        );
    }

    /**
     * 解析单来源 fallback 标题。
     *
     * @param rawSource 源文件
     * @param groupKey 分组键
     * @return 标题解析结果
     */
    private TitleResolution resolveSingleSourceTitle(RawSource rawSource, String groupKey) {
        TitleResolution sourceTitle = resolveSourceTitleCandidate(rawSource);
        if (sourceTitle != null) {
            return sourceTitle;
        }
        return resolveGroupKeyTitle(groupKey);
    }

    /**
     * 解析来源级标题候选。
     *
     * @param rawSource 源文件
     * @return 标题解析结果；无稳定候选时返回空
     */
    private TitleResolution resolveSourceTitleCandidate(RawSource rawSource) {
        if (rawSource == null) {
            return null;
        }
        String documentTitle = normalizeTitle(DocumentTitleSupport.resolveMetadataDocumentTitle(rawSource.getMetadataJson()));
        if (!documentTitle.isEmpty()) {
            return new TitleResolution(documentTitle, TITLE_SOURCE_DOCUMENT_TITLE);
        }
        String fileStemTitle = buildDisplayFileStemTitle(rawSource.getRelativePath());
        if (!fileStemTitle.isEmpty() && isSemanticTitleCandidate(fileStemTitle)) {
            return new TitleResolution(fileStemTitle, TITLE_SOURCE_FILE_STEM);
        }
        return null;
    }

    /**
     * 解析多来源共享标题。
     *
     * @param sortedSources 已排序源文件
     * @return 标题解析结果；无法收敛共享标题时返回空
     */
    private TitleResolution resolveSharedSourceTitle(List<RawSource> sortedSources) {
        String normalizedComparableTitle = null;
        String preferredTitle = "";
        for (RawSource rawSource : sortedSources) {
            TitleResolution sourceTitle = resolveSourceTitleCandidate(rawSource);
            if (sourceTitle == null || sourceTitle.getTitle().isEmpty()) {
                return null;
            }
            String comparableTitle = normalizeComparableTitle(sourceTitle.getTitle());
            if (comparableTitle.isEmpty()) {
                return null;
            }
            if (normalizedComparableTitle == null) {
                normalizedComparableTitle = comparableTitle;
            }
            else if (!normalizedComparableTitle.equals(comparableTitle)) {
                return null;
            }
            if (sourceTitle.getTitle().length() > preferredTitle.length()) {
                preferredTitle = sourceTitle.getTitle();
            }
        }
        if (preferredTitle.isEmpty()) {
            return null;
        }
        return new TitleResolution(preferredTitle, TITLE_SOURCE_SHARED_SOURCE_TITLE);
    }

    /**
     * 基于分组键构建回退标题。
     *
     * @param groupKey 分组键
     * @return 标题解析结果
     */
    private TitleResolution resolveGroupKeyTitle(String groupKey) {
        String normalizedConceptId = normalizeGroupKey(groupKey);
        return new TitleResolution(toTitle(normalizedConceptId, groupKey), TITLE_SOURCE_GROUP_KEY);
    }

    /**
     * 基于文件名 stem 构建可读标题。
     *
     * @param relativePath 相对路径
     * @return 展示标题
     */
    private String buildDisplayFileStemTitle(String relativePath) {
        String fileStem = normalizeTitle(DocumentTitleSupport.resolveFileNameTitle(relativePath));
        if (fileStem.isEmpty()) {
            return "";
        }
        String normalizedStem = fileStem.replaceAll("[-_]+", " ").replaceAll("\\s+", " ").trim();
        if (normalizedStem.isEmpty()) {
            return "";
        }
        String[] words = normalizedStem.split(" ");
        List<String> titledWords = new ArrayList<String>();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            titledWords.add(toTitleWord(word));
        }
        return String.join(" ", titledWords).trim();
    }

    /**
     * 判断标题是否具备基础语义信号。
     *
     * @param title 标题候选
     * @return 具备语义信号返回 true
     */
    private boolean isSemanticTitleCandidate(String title) {
        String normalizedTitle = normalizeTitle(title);
        if (normalizedTitle.isEmpty()) {
            return false;
        }
        if (normalizedTitle.length() >= 4) {
            return true;
        }
        for (int index = 0; index < normalizedTitle.length(); index++) {
            if (normalizedTitle.charAt(index) > 127) {
                return true;
            }
        }
        return normalizedTitle.contains(" ");
    }

    /**
     * 归一化标题比较文本。
     *
     * @param title 原始标题
     * @return 比较用标题
     */
    private String normalizeComparableTitle(String title) {
        return normalizeTitle(title)
                .replaceAll("[\\s\\-_:]+", "")
                .toLowerCase(Locale.ROOT);
    }

    /**
     * 构建多来源中性概括标题。
     *
     * @param sourceCount 来源数量
     * @return 中性标题
     */
    private String buildNeutralMultiSourceTitle(int sourceCount) {
        if (sourceCount <= 1) {
            return "资料概览";
        }
        return sourceCount + " 份资料概览";
    }

    /**
     * 归一化分组键为概念标识。
     *
     * @param groupKey 分组键
     * @return 概念标识
     */
    private String normalizeGroupKey(String groupKey) {
        if (groupKey == null) {
            return "default";
        }
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
            return groupKey == null ? "" : groupKey.trim();
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
        if (title == null) {
            return "";
        }
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
        if (snippet == null) {
            return "";
        }
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

    /**
     * Analyze 规则路由结果。
     *
     * 职责：承载小资料 / topic 路径已命中的概念与待继续处理的剩余源文件
     *
     * @author xiexu
     */
    private static final class AnalyzeRouteResult {

        private final List<AnalyzedConcept> analyzedConcepts;

        private final List<RawSource> remainingSources;

        /**
         * 创建规则路由结果。
         *
         * @param analyzedConcepts 已命中的概念
         * @param remainingSources 待继续处理的源文件
         */
        private AnalyzeRouteResult(List<AnalyzedConcept> analyzedConcepts, List<RawSource> remainingSources) {
            this.analyzedConcepts = analyzedConcepts;
            this.remainingSources = remainingSources;
        }

        /**
         * 获取已命中的概念。
         *
         * @return 概念列表
         */
        private List<AnalyzedConcept> getAnalyzedConcepts() {
            return analyzedConcepts;
        }

        /**
         * 获取待继续处理的源文件。
         *
         * @return 源文件列表
         */
        private List<RawSource> getRemainingSources() {
            return remainingSources;
        }
    }

    /**
     * 标题解析结果。
     *
     * 职责：承载概念标题与标题来源
     *
     * @author xiexu
     */
    private static final class TitleResolution {

        private final String title;

        private final String titleSource;

        /**
         * 创建标题解析结果。
         *
         * @param title 标题
         * @param titleSource 标题来源
         */
        private TitleResolution(String title, String titleSource) {
            this.title = title == null ? "" : title.trim();
            this.titleSource = titleSource == null ? "" : titleSource.trim();
        }

        /**
         * 获取标题。
         *
         * @return 标题
         */
        private String getTitle() {
            return title;
        }

        /**
         * 获取标题来源。
         *
         * @return 标题来源
         */
        private String getTitleSource() {
            return titleSource;
        }
    }

    /**
     * Analyze 尝试结果。
     *
     * 职责：承载单条 Analyze 路径的概念结果与失败原因
     *
     * @author xiexu
     */
    private static final class AnalyzeAttemptResult {

        private final List<AnalyzedConcept> analyzedConcepts;

        private final String failureReason;

        /**
         * 创建 Analyze 尝试结果。
         *
         * @param analyzedConcepts 概念结果
         * @param failureReason 失败原因
         */
        private AnalyzeAttemptResult(List<AnalyzedConcept> analyzedConcepts, String failureReason) {
            this.analyzedConcepts = analyzedConcepts;
            this.failureReason = failureReason;
        }

        /**
         * 创建成功结果。
         *
         * @param analyzedConcepts 概念结果
         * @return 尝试结果
         */
        private static AnalyzeAttemptResult success(List<AnalyzedConcept> analyzedConcepts) {
            return new AnalyzeAttemptResult(analyzedConcepts, null);
        }

        /**
         * 创建空结果。
         *
         * @param failureReason 失败原因
         * @return 尝试结果
         */
        private static AnalyzeAttemptResult empty(String failureReason) {
            return new AnalyzeAttemptResult(new ArrayList<AnalyzedConcept>(), failureReason);
        }

        /**
         * 获取概念结果。
         *
         * @return 概念结果
         */
        private List<AnalyzedConcept> getAnalyzedConcepts() {
            return analyzedConcepts;
        }

        /**
         * 获取失败原因。
         *
         * @return 失败原因
         */
        private String getFailureReason() {
            return failureReason;
        }
    }
}
