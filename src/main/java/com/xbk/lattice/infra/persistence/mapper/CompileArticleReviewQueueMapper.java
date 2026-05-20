package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.CompileArticleReviewQueueRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 编译文章人工确认队列 Mapper
 *
 * 职责：通过 XML SQL 访问 compile_article_review_queue 表
 *
 * @author xiexu
 */
@Mapper
public interface CompileArticleReviewQueueMapper {

    /**
     * 确保编译文章人工确认队列表存在。
     *
     * @return 影响行数
     */
    int ensureTable();

    /**
     * 保存或更新待人工确认草稿。
     *
     * @param record 队列记录
     * @return 影响行数
     */
    int upsertPending(@Param("record") CompileArticleReviewQueueRecord record);

    /**
     * 按状态查询队列记录。
     *
     * @param status 队列状态
     * @param limit 返回上限
     * @return 队列记录列表
     */
    List<CompileArticleReviewQueueRecord> findByStatus(
            @Param("status") String status,
            @Param("limit") int limit
    );

    /**
     * 查询最近队列记录。
     *
     * @param limit 返回上限
     * @return 队列记录列表
     */
    List<CompileArticleReviewQueueRecord> findRecent(@Param("limit") int limit);

    /**
     * 按主键查询队列记录。
     *
     * @param id 队列主键
     * @return 队列记录
     */
    CompileArticleReviewQueueRecord findById(@Param("id") long id);

    /**
     * 标记队列记录已发布。
     *
     * @param id 队列主键
     * @param reviewedBy 复核人
     * @param reviewedAt 复核时间
     * @param reviewComment 复核意见
     * @param publishedArticleKey 发布后的文章唯一键
     * @return 影响行数
     */
    int markPublished(
            @Param("id") long id,
            @Param("reviewedBy") String reviewedBy,
            @Param("reviewedAt") OffsetDateTime reviewedAt,
            @Param("reviewComment") String reviewComment,
            @Param("publishedArticleKey") String publishedArticleKey
    );

    /**
     * 标记队列记录已驳回。
     *
     * @param id 队列主键
     * @param reviewedBy 复核人
     * @param reviewedAt 复核时间
     * @param reviewComment 复核意见
     * @return 影响行数
     */
    int markRejected(
            @Param("id") long id,
            @Param("reviewedBy") String reviewedBy,
            @Param("reviewedAt") OffsetDateTime reviewedAt,
            @Param("reviewComment") String reviewComment
    );
}
