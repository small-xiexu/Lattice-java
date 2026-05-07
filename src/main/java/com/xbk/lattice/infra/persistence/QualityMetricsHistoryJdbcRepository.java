package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.QualityMetricsHistoryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 质量指标历史 JDBC 仓储
 *
 * 职责：提供质量快照历史的写入与按时间窗口查询能力
 *
 * @author xiexu
 */
@Repository
public class QualityMetricsHistoryJdbcRepository {

    private final QualityMetricsHistoryMapper qualityMetricsHistoryMapper;

    /**
     * 创建质量指标历史仓储。
     *
     * @param qualityMetricsHistoryMapper 质量指标历史 Mapper
     */
    public QualityMetricsHistoryJdbcRepository(QualityMetricsHistoryMapper qualityMetricsHistoryMapper) {
        this.qualityMetricsHistoryMapper = qualityMetricsHistoryMapper;
    }

    /**
     * 保存一条质量历史记录。
     *
     * @param record 历史记录
     */
    public void save(QualityMetricsHistoryRecord record) {
        qualityMetricsHistoryMapper.save(record);
    }

    /**
     * 查询最近 N 天的质量历史。
     *
     * @param days 天数
     * @return 历史记录列表
     */
    public List<QualityMetricsHistoryRecord> findSince(int days) {
        return qualityMetricsHistoryMapper.findSince(Math.max(days, 0));
    }
}
