package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.AnswerFeedbackAuditMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 答案反馈审计 JDBC 仓储
 *
 * 职责：保存并查询答案反馈处理动作历史
 *
 * @author xiexu
 */
@Repository
public class AnswerFeedbackAuditJdbcRepository {

    private final AnswerFeedbackAuditMapper answerFeedbackAuditMapper;

    /**
     * 创建答案反馈审计 JDBC 仓储。
     *
     * @param answerFeedbackAuditMapper 答案反馈审计 Mapper
     */
    public AnswerFeedbackAuditJdbcRepository(AnswerFeedbackAuditMapper answerFeedbackAuditMapper) {
        this.answerFeedbackAuditMapper = answerFeedbackAuditMapper;
    }

    /**
     * 保存审计记录。
     *
     * @param answerFeedbackAuditRecord 审计记录
     * @return 保存后的审计记录
     */
    public AnswerFeedbackAuditRecord save(AnswerFeedbackAuditRecord answerFeedbackAuditRecord) {
        AnswerFeedbackAuditRecord normalizedRecord = new AnswerFeedbackAuditRecord(
                answerFeedbackAuditRecord.getId(),
                answerFeedbackAuditRecord.getFeedbackId(),
                answerFeedbackAuditRecord.getAction(),
                answerFeedbackAuditRecord.getPreviousStatus(),
                answerFeedbackAuditRecord.getNextStatus(),
                answerFeedbackAuditRecord.getComment(),
                answerFeedbackAuditRecord.getOperatedBy(),
                answerFeedbackAuditRecord.getOperatedAt(),
                safeJson(answerFeedbackAuditRecord.getMetadataJson())
        );
        Long generatedId = answerFeedbackAuditMapper.insert(normalizedRecord);
        long id = generatedId == null ? 0L : generatedId.longValue();
        return new AnswerFeedbackAuditRecord(
                id,
                answerFeedbackAuditRecord.getFeedbackId(),
                answerFeedbackAuditRecord.getAction(),
                answerFeedbackAuditRecord.getPreviousStatus(),
                answerFeedbackAuditRecord.getNextStatus(),
                answerFeedbackAuditRecord.getComment(),
                answerFeedbackAuditRecord.getOperatedBy(),
                answerFeedbackAuditRecord.getOperatedAt(),
                safeJson(answerFeedbackAuditRecord.getMetadataJson())
        );
    }

    /**
     * 按反馈主键查询审计历史。
     *
     * @param feedbackId 反馈主键
     * @return 审计记录列表
     */
    public List<AnswerFeedbackAuditRecord> findByFeedbackId(long feedbackId) {
        return answerFeedbackAuditMapper.findByFeedbackId(feedbackId);
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
