package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.CompileArticleReviewQueueMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 编译文章人工确认队列 JDBC 仓储
 *
 * 职责：持久化 needs_human_review 编译草稿并维护人工发布状态
 *
 * @author xiexu
 */
@Repository
public class CompileArticleReviewQueueJdbcRepository {

    private static final int DEFAULT_LIMIT = 50;

    private final CompileArticleReviewQueueMapper compileArticleReviewQueueMapper;

    private volatile boolean tableEnsured;

    /**
     * 创建编译文章人工确认队列 JDBC 仓储。
     *
     * @param compileArticleReviewQueueMapper 队列 Mapper
     */
    public CompileArticleReviewQueueJdbcRepository(
            CompileArticleReviewQueueMapper compileArticleReviewQueueMapper
    ) {
        this.compileArticleReviewQueueMapper = compileArticleReviewQueueMapper;
        this.tableEnsured = false;
    }

    /**
     * 保存或更新待人工确认草稿。
     *
     * @param record 队列记录
     */
    public void upsertPending(CompileArticleReviewQueueRecord record) {
        ensureTable();
        compileArticleReviewQueueMapper.upsertPending(normalize(record));
    }

    /**
     * 按状态查询队列记录。
     *
     * @param status 队列状态
     * @param limit 返回上限
     * @return 队列记录列表
     */
    public List<CompileArticleReviewQueueRecord> list(String status, int limit) {
        ensureTable();
        int safeLimit = safeLimit(limit);
        if (status == null || status.isBlank()) {
            return compileArticleReviewQueueMapper.findRecent(safeLimit);
        }
        return compileArticleReviewQueueMapper.findByStatus(status.trim().toLowerCase(), safeLimit);
    }

    /**
     * 按状态统计队列记录数量。
     *
     * @param status 队列状态
     * @return 队列记录数量
     */
    public int countByStatus(String status) {
        ensureTable();
        if (status == null || status.isBlank()) {
            return 0;
        }
        return compileArticleReviewQueueMapper.countByStatus(status.trim().toLowerCase());
    }

    /**
     * 按主键查询队列记录。
     *
     * @param id 队列主键
     * @return 队列记录
     */
    public Optional<CompileArticleReviewQueueRecord> findById(long id) {
        ensureTable();
        return Optional.ofNullable(compileArticleReviewQueueMapper.findById(id));
    }

    /**
     * 标记队列记录已发布。
     *
     * @param id 队列主键
     * @param reviewedBy 复核人
     * @param reviewedAt 复核时间
     * @param reviewComment 复核意见
     * @param publishedArticleKey 发布后的文章唯一键
     * @return 是否更新成功
     */
    public boolean markPublished(
            long id,
            String reviewedBy,
            OffsetDateTime reviewedAt,
            String reviewComment,
            String publishedArticleKey
    ) {
        ensureTable();
        int updatedCount = compileArticleReviewQueueMapper.markPublished(
                id,
                reviewedBy,
                reviewedAt,
                reviewComment,
                publishedArticleKey
        );
        return updatedCount > 0;
    }

    /**
     * 标记队列记录已驳回。
     *
     * @param id 队列主键
     * @param reviewedBy 复核人
     * @param reviewedAt 复核时间
     * @param reviewComment 复核意见
     * @return 是否更新成功
     */
    public boolean markRejected(
            long id,
            String reviewedBy,
            OffsetDateTime reviewedAt,
            String reviewComment
    ) {
        ensureTable();
        int updatedCount = compileArticleReviewQueueMapper.markRejected(
                id,
                reviewedBy,
                reviewedAt,
                reviewComment
        );
        return updatedCount > 0;
    }

    private void ensureTable() {
        if (tableEnsured) {
            return;
        }
        synchronized (this) {
            if (tableEnsured) {
                return;
            }
            compileArticleReviewQueueMapper.ensureTable();
            tableEnsured = true;
        }
    }

    private CompileArticleReviewQueueRecord normalize(CompileArticleReviewQueueRecord record) {
        return new CompileArticleReviewQueueRecord(
                record.getId(),
                record.getJobId(),
                record.getSourceId(),
                record.getSourceCode(),
                record.getConceptId(),
                record.getArticleKey(),
                record.getTitle(),
                record.getContent(),
                safeValue(record.getLifecycle(), "ACTIVE"),
                record.getCompiledAt(),
                record.getSourcePaths() == null ? List.of() : record.getSourcePaths(),
                safeJson(record.getMetadataJson(), "{}"),
                "needs_human_review",
                record.getReviewRoute(),
                record.getReviewerModel(),
                safeJson(record.getReviewIssuesJson(), "[]"),
                record.getFixAttemptCount(),
                record.getMaxFixRounds(),
                record.getCreatedAt(),
                record.getUpdatedAt(),
                record.getReviewedBy(),
                record.getReviewedAt(),
                record.getReviewComment(),
                record.getPublishedArticleKey()
        );
    }

    private int safeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, 200);
    }

    private String safeJson(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private String safeValue(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
