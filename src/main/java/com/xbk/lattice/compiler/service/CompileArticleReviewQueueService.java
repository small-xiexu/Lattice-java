package com.xbk.lattice.compiler.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.article.service.ArticleMarkdownSupport;
import com.xbk.lattice.compiler.graph.ArticleReviewEnvelope;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.CompileArticleReviewQueueJdbcRepository;
import com.xbk.lattice.infra.persistence.CompileArticleReviewQueueRecord;
import com.xbk.lattice.query.domain.ReviewIssue;
import com.xbk.lattice.shared.json.JsonMappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 编译人工确认队列服务
 *
 * 职责：把最终未通过自动审查的编译草稿持久化为人工确认队列
 *
 * @author xiexu
 */
@Service
public class CompileArticleReviewQueueService {

    private static final ObjectMapper OBJECT_MAPPER = JsonMappers.moduleAwareMapper();

    private final CompileArticleReviewQueueJdbcRepository compileArticleReviewQueueJdbcRepository;

    /**
     * 创建编译人工确认队列服务。
     *
     * @param compileArticleReviewQueueJdbcRepository 队列仓储
     */
    public CompileArticleReviewQueueService(
            CompileArticleReviewQueueJdbcRepository compileArticleReviewQueueJdbcRepository
    ) {
        this.compileArticleReviewQueueJdbcRepository = compileArticleReviewQueueJdbcRepository;
    }

    /**
     * 持久化待人工确认草稿集合。
     *
     * @param jobId 编译作业标识
     * @param sourceId 资料源主键
     * @param sourceCode 资料源编码
     * @param needsHumanReviewArticles 待人工确认文章集合
     * @param fixAttemptCount 已执行修复轮数
     * @param maxFixRounds 最大修复轮数
     */
    @Transactional(rollbackFor = Exception.class)
    public void enqueue(
            String jobId,
            Long sourceId,
            String sourceCode,
            List<ArticleReviewEnvelope> needsHumanReviewArticles,
            int fixAttemptCount,
            int maxFixRounds
    ) {
        if (jobId == null || jobId.isBlank() || needsHumanReviewArticles == null || needsHumanReviewArticles.isEmpty()) {
            return;
        }
        for (ArticleReviewEnvelope reviewEnvelope : needsHumanReviewArticles) {
            CompileArticleReviewQueueRecord queueRecord = toQueueRecord(
                    jobId,
                    sourceId,
                    sourceCode,
                    reviewEnvelope,
                    fixAttemptCount,
                    maxFixRounds
            );
            if (queueRecord != null) {
                compileArticleReviewQueueJdbcRepository.upsertPending(queueRecord);
            }
        }
    }

    private CompileArticleReviewQueueRecord toQueueRecord(
            String jobId,
            Long sourceId,
            String sourceCode,
            ArticleReviewEnvelope reviewEnvelope,
            int fixAttemptCount,
            int maxFixRounds
    ) {
        if (reviewEnvelope == null || reviewEnvelope.getArticle() == null) {
            return null;
        }
        ArticleRecord draftArticle = reviewEnvelope.getArticle();
        String articleKey = resolveArticleKey(draftArticle, sourceCode);
        String reviewIssuesJson = serializeReviewIssues(reviewEnvelope);
        ArticleRecord normalizedDraft = ArticleMarkdownSupport.synchronizeArticleRecord(
                draftArticle,
                ArticleMarkdownSupport.normalizeReviewStatus(draftArticle.getContent(), "needs_human_review"),
                "needs_human_review"
        );
        return new CompileArticleReviewQueueRecord(
                0L,
                jobId,
                normalizedDraft.getSourceId() == null ? sourceId : normalizedDraft.getSourceId(),
                sourceCode,
                normalizedDraft.getConceptId(),
                articleKey,
                normalizedDraft.getTitle(),
                normalizedDraft.getContent(),
                normalizedDraft.getLifecycle(),
                normalizedDraft.getCompiledAt() == null ? OffsetDateTime.now() : normalizedDraft.getCompiledAt(),
                safeList(normalizedDraft.getSourcePaths()),
                safeJson(normalizedDraft.getMetadataJson(), "{}"),
                "needs_human_review",
                reviewEnvelope.getReviewerRoute(),
                reviewEnvelope.getReviewerRoute(),
                reviewIssuesJson,
                Math.max(reviewEnvelope.getFixAttemptCount(), fixAttemptCount),
                maxFixRounds,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private String resolveArticleKey(ArticleRecord articleRecord, String sourceCode) {
        if (articleRecord.getArticleKey() != null && !articleRecord.getArticleKey().isBlank()) {
            return articleRecord.getArticleKey();
        }
        if (sourceCode == null || sourceCode.isBlank()) {
            return articleRecord.getConceptId();
        }
        return sourceCode + "--" + articleRecord.getConceptId();
    }

    private String serializeReviewIssues(ArticleReviewEnvelope reviewEnvelope) {
        if (reviewEnvelope.getReviewResult() == null || reviewEnvelope.getReviewResult().getIssues() == null) {
            return "[]";
        }
        List<ReviewIssue> reviewIssues = new ArrayList<ReviewIssue>(reviewEnvelope.getReviewResult().getIssues());
        try {
            return OBJECT_MAPPER.writeValueAsString(reviewIssues);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize compile review issues", ex);
        }
    }

    private List<String> safeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values;
    }

    private String safeJson(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
