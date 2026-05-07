package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.AnswerFeedbackAuditRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 答案反馈审计 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 answer_feedback_audits 表
 *
 * @author xiexu
 */
@Mapper
public interface AnswerFeedbackAuditMapper {

    /**
     * 保存审计记录。
     *
     * @param record 审计记录
     * @return 审计主键
     */
    Long insert(@Param("record") AnswerFeedbackAuditRecord record);

    /**
     * 按反馈主键查询审计历史。
     *
     * @param feedbackId 反馈主键
     * @return 审计记录列表
     */
    List<AnswerFeedbackAuditRecord> findByFeedbackId(@Param("feedbackId") long feedbackId);
}
