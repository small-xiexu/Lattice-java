package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.AnswerFeedbackMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 答案反馈 JDBC 仓储
 *
 * 职责：保存、查询并更新答案反馈处理队列
 *
 * @author xiexu
 */
@Repository
public class AnswerFeedbackJdbcRepository {

    private final AnswerFeedbackMapper answerFeedbackMapper;

    /**
     * 创建答案反馈 JDBC 仓储。
     *
     * @param answerFeedbackMapper 答案反馈 Mapper
     */
    public AnswerFeedbackJdbcRepository(AnswerFeedbackMapper answerFeedbackMapper) {
        this.answerFeedbackMapper = answerFeedbackMapper;
    }

    /**
     * 保存答案反馈。
     *
     * @param answerFeedbackRecord 答案反馈记录
     * @return 保存后的答案反馈记录
     */
    public AnswerFeedbackRecord save(AnswerFeedbackRecord answerFeedbackRecord) {
        AnswerFeedbackRecord normalizedRecord = new AnswerFeedbackRecord(
                answerFeedbackRecord.getId(),
                answerFeedbackRecord.getQueryId(),
                answerFeedbackRecord.getQuestion(),
                answerFeedbackRecord.getAnswerSummary(),
                answerFeedbackRecord.getFeedbackType(),
                answerFeedbackRecord.getComment(),
                answerFeedbackRecord.getArticleKeys(),
                answerFeedbackRecord.getSourcePaths(),
                answerFeedbackRecord.getReportedBy(),
                answerFeedbackRecord.getStatus(),
                answerFeedbackRecord.getResolutionComment(),
                answerFeedbackRecord.getHandledBy(),
                answerFeedbackRecord.getHandledAt(),
                answerFeedbackRecord.getCreatedAt(),
                answerFeedbackRecord.getUpdatedAt(),
                safeJson(answerFeedbackRecord.getMetadataJson())
        );
        Long generatedId = answerFeedbackMapper.insert(normalizedRecord);
        long id = generatedId == null ? 0L : generatedId.longValue();
        return findById(id).orElseThrow();
    }

    /**
     * 按主键查询答案反馈。
     *
     * @param id 反馈主键
     * @return 答案反馈
     */
    public Optional<AnswerFeedbackRecord> findById(long id) {
        return Optional.ofNullable(answerFeedbackMapper.findById(id));
    }

    /**
     * 查询答案反馈列表。
     *
     * @param status 可选处理状态
     * @param limit 返回上限
     * @return 答案反馈列表
     */
    public List<AnswerFeedbackRecord> findAll(String status, int limit) {
        int safeLimit = limit <= 0 ? 50 : Math.min(limit, 200);
        String normalizedStatus = normalizeNullable(status);
        return answerFeedbackMapper.findAll(normalizedStatus, safeLimit);
    }

    /**
     * 更新处理状态。
     *
     * @param id 反馈主键
     * @param status 目标状态
     * @param resolutionComment 处理说明
     * @param handledBy 处理人
     * @param handledAt 处理时间
     * @return 更新后的答案反馈
     */
    public AnswerFeedbackRecord updateStatus(
            long id,
            String status,
            String resolutionComment,
            String handledBy,
            OffsetDateTime handledAt
    ) {
        answerFeedbackMapper.updateStatus(id, status, resolutionComment, handledBy, handledAt);
        return findById(id).orElseThrow();
    }

    /**
     * 统计指定状态的反馈数量。
     *
     * @param status 处理状态
     * @return 数量
     */
    public int countByStatus(String status) {
        return answerFeedbackMapper.countByStatus(status);
    }

    /**
     * 归一可空字符串。
     *
     * @param value 原始字符串
     * @return 归一后的字符串
     */
    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * 返回安全 JSON。
     *
     * @param metadataJson 原始 JSON
     * @return 安全 JSON
     */
    private String safeJson(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return "{}";
        }
        return metadataJson;
    }
}
