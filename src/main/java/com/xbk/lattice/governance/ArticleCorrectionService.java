package com.xbk.lattice.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xbk.lattice.article.service.ArticleMarkdownSupport;
import com.xbk.lattice.article.service.ArticleIdentityResolver;
import com.xbk.lattice.compiler.service.LlmGateway;
import com.xbk.lattice.compiler.prompt.LatticePrompts;
import com.xbk.lattice.governance.domain.CrossValidatePayload;
import com.xbk.lattice.governance.repo.RepoSnapshotService;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ArticleSnapshotJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleSnapshotRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import com.xbk.lattice.llm.service.LlmRouteResolution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

/**
 * 文章纠错服务
 *
 * 职责：基于源文件交叉验证执行知识文章纠错，并返回下游影响范围
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class ArticleCorrectionService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String COMPILE_SCENE = "compile";

    private static final String WRITER_ROLE = "writer";

    private static final String ADMIN_CORRECTION_SCOPE_PREFIX = "admin-correction";

    private static final String README_DEMO_ROUTE_LABEL = "readme-demo-openai";

    private static final String README_DEMO_BASE_URL = "http://127.0.0.1:19999";

    private static final String CORRECTION_REVIEW_STATUS = "needs_review";

    private final ArticleJdbcRepository articleJdbcRepository;

    private final SourceFileJdbcRepository sourceFileJdbcRepository;

    private final ArticleSnapshotJdbcRepository articleSnapshotJdbcRepository;

    private final ArticleIdentityResolver articleIdentityResolver;

    private final DependencyGraphService dependencyGraphService;

    private final LlmGateway llmGateway;

    private ExecutionLlmSnapshotService executionLlmSnapshotService;

    private RepoSnapshotService repoSnapshotService;

    /**
     * 创建文章纠错服务。
     *
     * @param articleJdbcRepository 文章仓储
     * @param sourceFileJdbcRepository 源文件仓储
     * @param articleSnapshotJdbcRepository 文章快照仓储
     * @param articleIdentityResolver 文章身份解析服务
     * @param dependencyGraphService 依赖图服务
     * @param llmGateway LLM 网关
     */
    @Autowired
    public ArticleCorrectionService(
            ArticleJdbcRepository articleJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository,
            ArticleSnapshotJdbcRepository articleSnapshotJdbcRepository,
            ArticleIdentityResolver articleIdentityResolver,
            DependencyGraphService dependencyGraphService,
            LlmGateway llmGateway
    ) {
        this.articleJdbcRepository = articleJdbcRepository;
        this.sourceFileJdbcRepository = sourceFileJdbcRepository;
        this.articleSnapshotJdbcRepository = articleSnapshotJdbcRepository;
        this.articleIdentityResolver = articleIdentityResolver;
        this.dependencyGraphService = dependencyGraphService;
        this.llmGateway = llmGateway;
    }

    /**
     * 创建兼容旧构造方式的文章纠错服务。
     *
     * @param articleJdbcRepository 文章仓储
     * @param sourceFileJdbcRepository 源文件仓储
     * @param articleSnapshotJdbcRepository 文章快照仓储
     * @param dependencyGraphService 依赖图服务
     * @param llmGateway LLM 网关
     */
    public ArticleCorrectionService(
            ArticleJdbcRepository articleJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository,
            ArticleSnapshotJdbcRepository articleSnapshotJdbcRepository,
            DependencyGraphService dependencyGraphService,
            LlmGateway llmGateway
    ) {
        this(
                articleJdbcRepository,
                sourceFileJdbcRepository,
                articleSnapshotJdbcRepository,
                articleJdbcRepository == null ? null : new ArticleIdentityResolver(articleJdbcRepository),
                dependencyGraphService,
                llmGateway
        );
    }

    /**
     * 注入整库快照服务。
     *
     * @param repoSnapshotService 整库快照服务
     */
    @Autowired(required = false)
    void setRepoSnapshotService(RepoSnapshotService repoSnapshotService) {
        this.repoSnapshotService = repoSnapshotService;
    }

    /**
     * 注入运行时快照服务。
     *
     * 职责：让 Admin 纠错在无 compile job 的场景下也优先复用当前绑定路由，而不是直接落回 bootstrap 默认路由。
     *
     * @param executionLlmSnapshotService 运行时快照服务
     */
    @Autowired(required = false)
    void setExecutionLlmSnapshotService(ExecutionLlmSnapshotService executionLlmSnapshotService) {
        this.executionLlmSnapshotService = executionLlmSnapshotService;
    }

    /**
     * 执行文章纠错。
     *
     * @param conceptId 概念标识
     * @param correctionSummary 纠错摘要
     * @return 纠错结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ArticleCorrectionResult correct(String conceptId, String correctionSummary) {
        ArticleRecord articleRecord = articleIdentityResolver == null
                ? null
                : articleIdentityResolver.require(conceptId, null);
        if (articleRecord == null) {
            throw new IllegalArgumentException("概念不存在: " + conceptId);
        }
        return doCorrect(articleRecord, correctionSummary);
    }

    /**
     * 执行文章纠错。
     *
     * @param articleId 文章唯一键或概念标识
     * @param sourceId 可选资料源主键
     * @param correctionSummary 纠错摘要
     * @return 纠错结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ArticleCorrectionResult correct(String articleId, Long sourceId, String correctionSummary) {
        if (sourceId == null) {
            return correct(articleId, correctionSummary);
        }
        ArticleRecord articleRecord = articleIdentityResolver.require(articleId, sourceId);
        return doCorrect(articleRecord, correctionSummary);
    }

    /**
     * 对已解析文章执行纠错主流程。
     *
     * @param articleRecord 目标文章
     * @param correctionSummary 纠错摘要
     * @return 纠错结果
     */
    private ArticleCorrectionResult doCorrect(ArticleRecord articleRecord, String correctionSummary) {
        List<SourceExcerpt> sourceExcerpts = collectSourceExcerpts(articleRecord);
        String validationJson = generateWriterText(
                articleRecord,
                "cross-validate",
                LatticePrompts.SYSTEM_CROSS_VALIDATE,
                buildCrossValidatePrompt(articleRecord, correctionSummary, sourceExcerpts)
        );
        CrossValidatePayload validationPayload = parseValidationResult(validationJson);
        String revisedContent = generateWriterText(
                articleRecord,
                "apply-correction",
                LatticePrompts.SYSTEM_APPLY_CORRECTION,
                buildApplyCorrectionPrompt(articleRecord, correctionSummary, validationPayload)
        );
        String normalizedContent = ArticleMarkdownSupport.normalizeReviewStatus(revisedContent, CORRECTION_REVIEW_STATUS);
        ArticleMarkdownSupport.ParsedFrontmatter parsedFrontmatter = ArticleMarkdownSupport.parse(normalizedContent);
        String normalizedTitle = parsedFrontmatter.getTitle().isBlank()
                ? articleRecord.getTitle()
                : parsedFrontmatter.getTitle();
        List<String> normalizedSourcePaths = parsedFrontmatter.getSourcePaths().isEmpty()
                ? articleRecord.getSourcePaths()
                : parsedFrontmatter.getSourcePaths();
        String normalizedSummary = parsedFrontmatter.getSummary().isBlank()
                ? articleRecord.getSummary()
                : parsedFrontmatter.getSummary();
        List<String> normalizedReferentialKeywords = parsedFrontmatter.getReferentialKeywords().isEmpty()
                ? articleRecord.getReferentialKeywords()
                : parsedFrontmatter.getReferentialKeywords();
        List<String> normalizedDependsOn = parsedFrontmatter.getDependsOn().isEmpty()
                ? articleRecord.getDependsOn()
                : parsedFrontmatter.getDependsOn();
        List<String> normalizedRelated = parsedFrontmatter.getRelated().isEmpty()
                ? articleRecord.getRelated()
                : parsedFrontmatter.getRelated();
        String normalizedConfidence = parsedFrontmatter.getConfidence().isBlank()
                ? articleRecord.getConfidence()
                : parsedFrontmatter.getConfidence();
        OffsetDateTime normalizedCompiledAt = parsedFrontmatter.getCompiledAt() == null
                ? articleRecord.getCompiledAt()
                : parsedFrontmatter.getCompiledAt();

        ArticleRecord updatedRecord = articleRecord.copy(
                normalizedTitle,
                normalizedContent,
                articleRecord.getLifecycle(),
                normalizedCompiledAt,
                normalizedSourcePaths,
                mergeCorrectionMetadataJson(articleRecord.getMetadataJson(), normalizedSummary, normalizedSourcePaths),
                normalizedSummary,
                normalizedReferentialKeywords,
                normalizedDependsOn,
                normalizedRelated,
                normalizedConfidence,
                CORRECTION_REVIEW_STATUS
        );
        articleJdbcRepository.upsert(updatedRecord);

        articleSnapshotJdbcRepository.save(ArticleSnapshotRecord.fromArticle(
                updatedRecord,
                "correction",
                OffsetDateTime.now()
        ));
        captureRepoSnapshot(updatedRecord);

        return new ArticleCorrectionResult(
                updatedRecord.getSourceId(),
                updatedRecord.getArticleKey(),
                updatedRecord.getConceptId(),
                normalizedContent,
                collectDownstreamIds(updatedRecord.getConceptId()),
                validationPayload.isSupported()
        );
    }

    private String mergeCorrectionMetadataJson(
            String metadataJson,
            String summary,
            List<String> sourcePaths
    ) {
        try {
            ObjectNode metadataNode = readMetadataNode(metadataJson);
            if (summary != null && !summary.isBlank()) {
                metadataNode.put("summary", summary);
                metadataNode.put("description", summary);
            }
            if (sourcePaths != null) {
                metadataNode.put("sourceCount", sourcePaths.size());
            }
            return OBJECT_MAPPER.writeValueAsString(metadataNode);
        }
        catch (Exception exception) {
            if (metadataJson == null || metadataJson.isBlank()) {
                return "{}";
            }
            return metadataJson;
        }
    }

    private ObjectNode readMetadataNode(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return OBJECT_MAPPER.createObjectNode();
        }
        try {
            return OBJECT_MAPPER.readTree(metadataJson) instanceof ObjectNode objectNode
                    ? objectNode.deepCopy()
                    : OBJECT_MAPPER.createObjectNode();
        }
        catch (Exception exception) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    private String generateWriterText(
            ArticleRecord articleRecord,
            String purpose,
            String systemPrompt,
            String userPrompt
    ) {
        String correctionScopeId = resolveCorrectionScopeId(articleRecord);
        ExecutionLlmSnapshotService snapshotService = this.executionLlmSnapshotService;
        if (snapshotService == null || correctionScopeId == null || correctionScopeId.isBlank()) {
            ensureCorrectionRouteIsUsable(llmGateway.routeResolution(COMPILE_SCENE, WRITER_ROLE));
            return llmGateway.generateText(COMPILE_SCENE, WRITER_ROLE, purpose, systemPrompt, userPrompt);
        }
        snapshotService.freezeSnapshots(
                ExecutionLlmSnapshotService.COMPILE_SCOPE_TYPE,
                correctionScopeId,
                COMPILE_SCENE
        );
        ensureCorrectionRouteIsUsable(llmGateway.routeResolutionFor(correctionScopeId, COMPILE_SCENE, WRITER_ROLE));
        return llmGateway.generateTextWithScope(
                correctionScopeId,
                COMPILE_SCENE,
                WRITER_ROLE,
                purpose,
                systemPrompt,
                userPrompt
        );
    }

    private String resolveCorrectionScopeId(ArticleRecord articleRecord) {
        if (articleRecord == null) {
            return null;
        }
        String sourcePart = articleRecord.getSourceId() == null
                ? "no-source"
                : String.valueOf(articleRecord.getSourceId());
        String conceptPart = articleRecord.getConceptId() == null || articleRecord.getConceptId().isBlank()
                ? "unknown-concept"
                : articleRecord.getConceptId().trim();
        return ADMIN_CORRECTION_SCOPE_PREFIX + ":" + sourcePart + ":" + conceptPart;
    }

    /**
     * 校验 Admin 纠错当前命中的 writer 路由是否仍为 README 演示连接。
     *
     * @param routeResolution 路由解析结果
     */
    private void ensureCorrectionRouteIsUsable(LlmRouteResolution routeResolution) {
        if (!isReadmeDemoRoute(routeResolution)) {
            return;
        }
        String routeLabel = routeResolution.getRouteLabel();
        String baseUrl = routeResolution.getBaseUrl();
        throw new IllegalStateException(
                "Admin 纠错当前命中 README 演示 LLM 连接"
                        + "（routeLabel=" + safeRouteValue(routeLabel)
                        + ", baseUrl=" + safeRouteValue(baseUrl)
                        + "），请先在 /admin/settings 为 compile.writer 切换到真实可用连接后重试"
        );
    }

    /**
     * 判断当前路由是否仍为 README 演示连接。
     *
     * @param routeResolution 路由解析结果
     * @return 是否命中 README 演示连接
     */
    private boolean isReadmeDemoRoute(LlmRouteResolution routeResolution) {
        if (routeResolution == null) {
            return false;
        }
        String routeLabel = routeResolution.getRouteLabel();
        if (README_DEMO_ROUTE_LABEL.equalsIgnoreCase(safeRouteValue(routeLabel))) {
            return true;
        }
        String baseUrl = routeResolution.getBaseUrl();
        return README_DEMO_BASE_URL.equalsIgnoreCase(safeRouteValue(baseUrl));
    }

    /**
     * 返回适合展示的路由字段值。
     *
     * @param value 原始值
     * @return 展示值
     */
    private String safeRouteValue(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim();
    }

    private void captureRepoSnapshot(ArticleRecord articleRecord) {
        if (repoSnapshotService == null) {
            return;
        }
        String articleIdentity = articleRecord.getArticleKey() == null || articleRecord.getArticleKey().isBlank()
                ? articleRecord.getConceptId()
                : articleRecord.getArticleKey();
        repoSnapshotService.snapshot("governance.correct", "articleId=" + articleIdentity, null);
    }

    private List<SourceExcerpt> collectSourceExcerpts(ArticleRecord articleRecord) {
        List<SourceExcerpt> excerpts = new ArrayList<SourceExcerpt>();
        List<String> sourcePaths = articleRecord.getSourcePaths();
        int limit = Math.min(sourcePaths.size(), 3);
        for (int index = 0; index < limit; index++) {
            String sourcePath = sourcePaths.get(index);
            Optional<SourceFileRecord> optionalSourceFileRecord = articleRecord.getSourceId() == null
                    ? sourceFileJdbcRepository.findByPath(sourcePath)
                    : sourceFileJdbcRepository.findBySourceIdAndRelativePath(articleRecord.getSourceId(), sourcePath);
            if (optionalSourceFileRecord.isEmpty()) {
                optionalSourceFileRecord = sourceFileJdbcRepository.findByPath(sourcePath);
            }
            if (optionalSourceFileRecord.isEmpty()) {
                continue;
            }
            SourceFileRecord sourceFileRecord = optionalSourceFileRecord.orElseThrow();
            String contentText = sourceFileRecord.getContentText();
            if (contentText == null || contentText.isBlank()) {
                continue;
            }
            excerpts.add(new SourceExcerpt(sourcePath, truncate(contentText, 3000)));
        }
        return excerpts;
    }

    private String buildCrossValidatePrompt(
            ArticleRecord articleRecord,
            String correctionSummary,
            List<SourceExcerpt> sourceExcerpts
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("概念ID：").append(articleRecord.getConceptId()).append("\n");
        builder.append("用户纠正摘要：").append(correctionSummary).append("\n\n");
        builder.append("当前文章：\n").append(articleRecord.getContent()).append("\n\n");
        builder.append("源文件摘录：\n");
        for (SourceExcerpt sourceExcerpt : sourceExcerpts) {
            builder.append("## ").append(sourceExcerpt.path).append("\n");
            builder.append(sourceExcerpt.content).append("\n\n");
        }
        return builder.toString();
    }

    private String buildApplyCorrectionPrompt(
            ArticleRecord articleRecord,
            String correctionSummary,
            CrossValidatePayload validationPayload
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("概念ID：").append(articleRecord.getConceptId()).append("\n");
        builder.append("用户纠正摘要：").append(correctionSummary).append("\n");
        builder.append("源文件是否支持：").append(validationPayload.isSupported()).append("\n");
        if (!validationPayload.getEvidence().isBlank()) {
            builder.append("证据摘要：").append(validationPayload.getEvidence()).append("\n");
        }
        builder.append("\n原始文章：\n").append(articleRecord.getContent());
        return builder.toString();
    }

    /**
     * 解析交叉验证结构化载荷。
     *
     * @param validationJson 原始 JSON
     * @return 交叉验证载荷
     */
    private CrossValidatePayload parseValidationResult(String validationJson) {
        try {
            return OBJECT_MAPPER.readValue(validationJson, CrossValidatePayload.class);
        }
        catch (Exception ex) {
            return CrossValidatePayload.unsupported();
        }
    }

    private List<String> collectDownstreamIds(String rootConceptId) {
        if (dependencyGraphService == null) {
            return List.of();
        }
        DependencyGraphSnapshot snapshot = dependencyGraphService.snapshot();
        Map<String, List<DependencyGraphEdge>> adjacency = buildAdjacency(snapshot.getEdges());
        Queue<PropagationNode> queue = new ArrayDeque<PropagationNode>();
        Set<String> visited = new LinkedHashSet<String>();
        List<String> downstreamIds = new ArrayList<String>();

        visited.add(rootConceptId);
        queue.add(new PropagationNode(rootConceptId, 0));
        while (!queue.isEmpty()) {
            PropagationNode current = queue.poll();
            if (current.depth >= 3) {
                continue;
            }
            for (DependencyGraphEdge edge : adjacency.getOrDefault(current.conceptId, List.of())) {
                if (!visited.add(edge.getDownstreamConceptId())) {
                    continue;
                }
                downstreamIds.add(edge.getDownstreamConceptId());
                queue.add(new PropagationNode(edge.getDownstreamConceptId(), current.depth + 1));
            }
        }
        return downstreamIds;
    }

    private Map<String, List<DependencyGraphEdge>> buildAdjacency(List<DependencyGraphEdge> edges) {
        Map<String, List<DependencyGraphEdge>> adjacency = new LinkedHashMap<String, List<DependencyGraphEdge>>();
        for (DependencyGraphEdge edge : edges) {
            adjacency.computeIfAbsent(edge.getUpstreamConceptId(), ignored -> new ArrayList<DependencyGraphEdge>())
                    .add(edge);
        }
        return adjacency;
    }

    private String truncate(String text, int limit) {
        if (text == null || text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit);
    }

    private static class SourceExcerpt {

        private final String path;

        private final String content;

        private SourceExcerpt(String path, String content) {
            this.path = path;
            this.content = content;
        }
    }

    private static class PropagationNode {

        private final String conceptId;

        private final int depth;

        private PropagationNode(String conceptId, int depth) {
            this.conceptId = conceptId;
            this.depth = depth;
        }
    }
}
