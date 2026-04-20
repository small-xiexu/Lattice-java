package com.xbk.lattice.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.article.service.ArticleIdentityResolver;
import com.xbk.lattice.compiler.service.LlmGateway;
import com.xbk.lattice.compiler.prompt.LatticePrompts;
import com.xbk.lattice.governance.repo.RepoSnapshotService;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ArticleSnapshotJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleSnapshotRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
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

    private final ArticleJdbcRepository articleJdbcRepository;

    private final SourceFileJdbcRepository sourceFileJdbcRepository;

    private final ArticleSnapshotJdbcRepository articleSnapshotJdbcRepository;

    private final ArticleIdentityResolver articleIdentityResolver;

    private final DependencyGraphService dependencyGraphService;

    private final LlmGateway llmGateway;

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
        String validationJson = llmGateway.compile(
                "cross-validate",
                LatticePrompts.SYSTEM_CROSS_VALIDATE,
                buildCrossValidatePrompt(articleRecord, correctionSummary, sourceExcerpts)
        );
        ValidationResult validationResult = parseValidationResult(validationJson);
        String revisedContent = llmGateway.compile(
                "apply-correction",
                LatticePrompts.SYSTEM_APPLY_CORRECTION,
                buildApplyCorrectionPrompt(articleRecord, correctionSummary, validationResult)
        );

        ArticleRecord updatedRecord = articleRecord.copy(
                articleRecord.getTitle(),
                revisedContent,
                articleRecord.getLifecycle(),
                articleRecord.getCompiledAt(),
                articleRecord.getSourcePaths(),
                articleRecord.getMetadataJson(),
                articleRecord.getSummary(),
                articleRecord.getReferentialKeywords(),
                articleRecord.getDependsOn(),
                articleRecord.getRelated(),
                articleRecord.getConfidence(),
                "needs_review"
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
                revisedContent,
                collectDownstreamIds(updatedRecord.getConceptId()),
                validationResult.supported
        );
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
            ValidationResult validationResult
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("概念ID：").append(articleRecord.getConceptId()).append("\n");
        builder.append("用户纠正摘要：").append(correctionSummary).append("\n");
        builder.append("源文件是否支持：").append(validationResult.supported).append("\n");
        if (validationResult.evidence != null && !validationResult.evidence.isBlank()) {
            builder.append("证据摘要：").append(validationResult.evidence).append("\n");
        }
        builder.append("\n原始文章：\n").append(articleRecord.getContent());
        return builder.toString();
    }

    private ValidationResult parseValidationResult(String validationJson) {
        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(validationJson);
            boolean supported = rootNode.path("supported").asBoolean(false);
            String evidence = rootNode.path("evidence").asText(null);
            return new ValidationResult(supported, evidence);
        }
        catch (Exception ex) {
            return new ValidationResult(false, null);
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

    private static class ValidationResult {

        private final boolean supported;

        private final String evidence;

        private ValidationResult(boolean supported, String evidence) {
            this.supported = supported;
            this.evidence = evidence;
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
