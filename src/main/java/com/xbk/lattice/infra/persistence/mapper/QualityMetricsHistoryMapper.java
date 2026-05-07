package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.QualityMetricsHistoryRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 质量指标历史 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 quality_metrics_history 表
 *
 * @author xiexu
 */
@Mapper
public interface QualityMetricsHistoryMapper {

    /**
     * 保存一条质量历史记录。
     *
     * @param record 历史记录
     * @return 影响行数
     */
    int save(@Param("record") QualityMetricsHistoryRecord record);

    /**
     * 查询最近 N 天的质量历史。
     *
     * @param days 天数
     * @return 历史记录列表
     */
    List<QualityMetricsHistoryRecord> findSince(@Param("days") int days);
}
