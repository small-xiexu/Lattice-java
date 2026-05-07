package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.AnswerFeedbackRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 答案反馈 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 pending_answer_feedback 表
 *
 * @author xiexu
 */
@Mapper
public interface AnswerFeedbackMapper {

    /**
     * 保存答案反馈。
     *
     * @param record 答案反馈记录
     * @return 反馈主键
     */
    Long insert(@Param("record") AnswerFeedbackRecord record);

    /**
     * 按主键查询答案反馈。
     *
     * @param id 反馈主键
     * @return 答案反馈
     */
    AnswerFeedbackRecord findById(@Param("id") long id);

    /**
     * 查询答案反馈列表。
     *
     * @param status 可选处理状态
     * @param limit 返回上限
     * @return 答案反馈列表
     */
    List<AnswerFeedbackRecord> findAll(@Param("status") String status, @Param("limit") int limit);

    /**
     * 更新处理状态。
     *
     * @param id 反馈主键
     * @param status 目标状态
     * @param resolutionComment 处理说明
     * @param handledBy 处理人
     * @param handledAt 处理时间
     * @return 影响行数
     */
    int updateStatus(
            @Param("id") long id,
            @Param("status") String status,
            @Param("resolutionComment") String resolutionComment,
            @Param("handledBy") String handledBy,
            @Param("handledAt") OffsetDateTime handledAt
    );

    /**
     * 统计指定状态的反馈数量。
     *
     * @param status 处理状态
     * @return 数量
     */
    int countByStatus(@Param("status") String status);
}
