package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.CompileArticleReviewQueueMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
     * 按编译作业标识汇总人工确认发布结果。
     *
     * @param jobId 编译作业标识
     * @return 发布结果汇总
     */
    public PublishOutcomeSummary summarizeByJobId(String jobId) {
        ensureTable();
        if (jobId == null || jobId.isBlank()) {
            return PublishOutcomeSummary.empty();
        }
        Map<String, Object> row = compileArticleReviewQueueMapper.summarizeByJobId(jobId.trim());
        if (row == null || row.isEmpty()) {
            return PublishOutcomeSummary.empty();
        }
        return new PublishOutcomeSummary(
                toInt(row.get("pending_human_review_count")),
                toInt(row.get("published_count")),
                toInt(row.get("rejected_count"))
        );
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

    /**
     * 将聚合结果中的数值转换为 int。
     *
     * @param value 原始值
     * @return 数值结果
     */
    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    /**
     * 编译作业人工确认发布结果汇总。
     *
     * 职责：承载按 compile jobId 聚合后的 pending / published / rejected 计数
     *
     * @author xiexu
     */
    public static final class PublishOutcomeSummary {

        private final int pendingHumanReviewCount;

        private final int publishedCount;

        private final int rejectedCount;

        /**
         * 创建发布结果汇总。
         *
         * @param pendingHumanReviewCount 待人工确认数量
         * @param publishedCount 已发布数量
         * @param rejectedCount 已驳回数量
         */
        public PublishOutcomeSummary(
                int pendingHumanReviewCount,
                int publishedCount,
                int rejectedCount
        ) {
            this.pendingHumanReviewCount = pendingHumanReviewCount;
            this.publishedCount = publishedCount;
            this.rejectedCount = rejectedCount;
        }

        /**
         * 创建空汇总。
         *
         * @return 空汇总
         */
        public static PublishOutcomeSummary empty() {
            return new PublishOutcomeSummary(0, 0, 0);
        }

        /**
         * 获取待人工确认数量。
         *
         * @return 待人工确认数量
         */
        public int getPendingHumanReviewCount() {
            return pendingHumanReviewCount;
        }

        /**
         * 获取已发布数量。
         *
         * @return 已发布数量
         */
        public int getPublishedCount() {
            return publishedCount;
        }

        /**
         * 获取已驳回数量。
         *
         * @return 已驳回数量
         */
        public int getRejectedCount() {
            return rejectedCount;
        }

        /**
         * 判断是否存在人工确认发布结果。
         *
         * @return 是否存在人工确认发布结果
         */
        public boolean hasAnyOutcome() {
            return pendingHumanReviewCount > 0 || publishedCount > 0 || rejectedCount > 0;
        }
    }
}
