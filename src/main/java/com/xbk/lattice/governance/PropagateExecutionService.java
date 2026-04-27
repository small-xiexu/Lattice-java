package com.xbk.lattice.governance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.article.service.ArticleIdentityResolver;
import com.xbk.lattice.article.service.ArticleMarkdownSupport;
import com.xbk.lattice.compiler.service.LlmGateway;
import com.xbk.lattice.compiler.prompt.LatticePrompts;
import com.xbk.lattice.governance.domain.PropagationCheckPayload;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ArticleSnapshotJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleSnapshotRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 下游传播执行服务
 *
 * 职责：真正改写受上游纠错影响的下游文章，并清理 upstream 标记
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class PropagateExecutionService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String COMPILE_SCENE = "compile";

    private static final String WRITER_ROLE = "writer";

    private final ArticleJdbcRepository articleJdbcRepository;

    private final ArticleSnapshotJdbcRepository articleSnapshotJdbcRepository;

    private final ArticleIdentityResolver articleIdentityResolver;

    private final LlmGateway llmGateway;

    /**
     * 创建下游传播执行服务。
     *
     * @param articleJdbcRepository 文章仓储
     * @param articleSnapshotJdbcRepository 快照仓储
     * @param articleIdentityResolver 文章身份解析器
     * @param llmGateway LLM 网关
     */
    @Autowired
    public PropagateExecutionService(
            ArticleJdbcRepository articleJdbcRepository,
            ArticleSnapshotJdbcRepository articleSnapshotJdbcRepository,
            ArticleIdentityResolver articleIdentityResolver,
            LlmGateway llmGateway
    ) {
        this.articleJdbcRepository = articleJdbcRepository;
        this.articleSnapshotJdbcRepository = articleSnapshotJdbcRepository;
        this.articleIdentityResolver = articleIdentityResolver;
        this.llmGateway = llmGateway;
    }

    /**
     * 创建兼容旧构造方式的传播执行服务。
     *
     * @param articleJdbcRepository 文章仓储
     * @param articleSnapshotJdbcRepository 快照仓储
     * @param llmGateway LLM 网关
     */
    public PropagateExecutionService(
            ArticleJdbcRepository articleJdbcRepository,
            ArticleSnapshotJdbcRepository articleSnapshotJdbcRepository,
            LlmGateway llmGateway
    ) {
        this(
                articleJdbcRepository,
                articleSnapshotJdbcRepository,
                articleJdbcRepository == null ? null : new ArticleIdentityResolver(articleJdbcRepository),
                llmGateway
        );
    }

    /**
     * 执行指定根概念的下游传播。
     *
     * @param rootConceptId 根概念标识
     * @return 传播执行结果
     */
    @Transactional(rollbackFor = Exception.class)
    public PropagationExecutionResult executePropagation(String rootConceptId) {
        Optional<ArticleRecord> optionalRootArticle = articleIdentityResolver == null
                ? articleJdbcRepository.findByConceptId(rootConceptId)
                : articleIdentityResolver.resolve(rootConceptId);
        if (optionalRootArticle.isEmpty()) {
            return new PropagationExecutionResult(0, 0, 0);
        }

        ArticleRecord rootArticle = optionalRootArticle.orElseThrow();
        List<ArticleRecord> downstreamArticles = articleJdbcRepository.findWithUpstreamCorrections(rootArticle);
        int processed = 0;
        int updated = 0;
        int skipped = 0;

        for (ArticleRecord downstreamArticle : downstreamArticles) {
            String correctionSummary = extractCorrectionSummary(downstreamArticle.getMetadataJson(), rootConceptId);
            String checkJson = llmGateway.generateText(
                    COMPILE_SCENE,
                    WRITER_ROLE,
                    "check-propagation",
                    LatticePrompts.SYSTEM_CHECK_PROPAGATION_NEEDED,
                    buildCheckPrompt(rootArticle, downstreamArticle, correctionSummary)
            );
            PropagationCheckPayload checkPayload = parsePropagationCheckPayload(checkJson);
            if (checkPayload.isAffected()) {
                String updatedContent = llmGateway.generateText(
                        COMPILE_SCENE,
                        WRITER_ROLE,
                        "apply-propagation",
                        LatticePrompts.SYSTEM_APPLY_PROPAGATION,
                        buildApplyPrompt(rootArticle, downstreamArticle, correctionSummary)
                );
                String propagatedContent = appendPropagationMarker(updatedContent, rootConceptId);
                ArticleRecord updatedRecord = downstreamArticle.copy(
                        downstreamArticle.getTitle(),
                        propagatedContent,
                        downstreamArticle.getLifecycle(),
                        downstreamArticle.getCompiledAt(),
                        downstreamArticle.getSourcePaths(),
                        downstreamArticle.getMetadataJson(),
                        downstreamArticle.getSummary(),
                        downstreamArticle.getReferentialKeywords(),
                        downstreamArticle.getDependsOn(),
                        downstreamArticle.getRelated(),
                        downstreamArticle.getConfidence(),
                        "needs_review"
                );
                updatedRecord = ArticleMarkdownSupport.synchronizeArticleRecord(
                        updatedRecord,
                        propagatedContent,
                        "needs_review"
                );
                articleJdbcRepository.upsert(updatedRecord);
                articleSnapshotJdbcRepository.save(ArticleSnapshotRecord.fromArticle(
                        updatedRecord,
                        "propagation",
                        OffsetDateTime.now()
                ));
                updated++;
            }
            else {
                skipped++;
            }
            articleJdbcRepository.clearUpstreamCorrection(downstreamArticle, rootArticle);
            processed++;
        }
        return new PropagationExecutionResult(processed, updated, skipped);
    }

    private String extractCorrectionSummary(String metadataJson, String rootConceptId) {
        try {
            JsonNode correctionsNode = OBJECT_MAPPER.readTree(metadataJson).path("upstream_corrections");
            if (!correctionsNode.isArray()) {
                return "";
            }
            for (JsonNode correctionNode : correctionsNode) {
                if (rootConceptId.equals(correctionNode.path("from").asText())) {
                    return correctionNode.path("summary").asText("");
                }
            }
        }
        catch (Exception ex) {
            return "";
        }
        return "";
    }

    /**
     * 解析传播影响检查结构化载荷。
     *
     * @param json 原始 JSON
     * @return 传播影响检查载荷
     */
    private PropagationCheckPayload parsePropagationCheckPayload(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, PropagationCheckPayload.class);
        }
        catch (Exception ex) {
            return PropagationCheckPayload.unaffected();
        }
    }

    private String buildCheckPrompt(
            ArticleRecord rootArticle,
            ArticleRecord downstreamArticle,
            String correctionSummary
    ) {
        return "上游概念：" + rootArticle.getConceptId() + "\n"
                + "上游文章键：" + rootArticle.getArticleKey() + "\n"
                + "纠错摘要：" + correctionSummary + "\n"
                + "上游文章：\n" + rootArticle.getContent() + "\n\n"
                + "下游文章：\n" + downstreamArticle.getContent();
    }

    private String buildApplyPrompt(
            ArticleRecord rootArticle,
            ArticleRecord downstreamArticle,
            String correctionSummary
    ) {
        return "上游概念：" + rootArticle.getConceptId() + "\n"
                + "上游文章键：" + rootArticle.getArticleKey() + "\n"
                + "纠错摘要：" + correctionSummary + "\n"
                + "请基于上游纠错重写下游文章。\n\n"
                + "下游文章原文：\n" + downstreamArticle.getContent();
    }

    private String appendPropagationMarker(String content, String rootConceptId) {
        String marker = "<!-- propagated-fix: " + rootConceptId + ", " + LocalDate.now() + " -->";
        if (content == null || content.isBlank()) {
            return marker;
        }
        return content + "\n\n" + marker;
    }
}
