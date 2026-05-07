package com.xbk.lattice.query.structured;

import com.xbk.lattice.infra.persistence.StructuredTableRowEvidence;
import com.xbk.lattice.infra.persistence.StructuredTableGroupCountRecord;

import java.util.List;

/**
 * 结构化查询结果
 *
 * 职责：承载确定性结构化查询执行后的行证据与聚合值
 *
 * @author xiexu
 */
public class StructuredQueryResult {

    private final StructuredQueryPlan plan;

    private final List<StructuredTableRowEvidence> rowEvidences;

    private final List<StructuredTableGroupCountRecord> groupCounts;

    private final long count;

    /**
     * 创建结构化查询结果。
     *
     * @param plan 查询计划
     * @param rowEvidences 行证据
     * @param count 聚合行数
     */
    public StructuredQueryResult(
            StructuredQueryPlan plan,
            List<StructuredTableRowEvidence> rowEvidences,
            long count
    ) {
        this(plan, rowEvidences, List.of(), count);
    }

    /**
     * 创建结构化查询结果。
     *
     * @param plan 查询计划
     * @param rowEvidences 行证据
     * @param groupCounts 分组计数结果
     * @param count 聚合行数
     */
    public StructuredQueryResult(
            StructuredQueryPlan plan,
            List<StructuredTableRowEvidence> rowEvidences,
            List<StructuredTableGroupCountRecord> groupCounts,
            long count
    ) {
        this.plan = plan;
        this.rowEvidences = rowEvidences == null ? List.of() : rowEvidences;
        this.groupCounts = groupCounts == null ? List.of() : groupCounts;
        this.count = count;
    }

    /**
     * 返回查询计划。
     *
     * @return 查询计划
     */
    public StructuredQueryPlan getPlan() {
        return plan;
    }

    /**
     * 返回行证据。
     *
     * @return 行证据
     */
    public List<StructuredTableRowEvidence> getRowEvidences() {
        return rowEvidences;
    }

    /**
     * 返回分组计数结果。
     *
     * @return 分组计数结果
     */
    public List<StructuredTableGroupCountRecord> getGroupCounts() {
        return groupCounts;
    }

    /**
     * 返回聚合行数。
     *
     * @return 聚合行数
     */
    public long getCount() {
        return count;
    }

    /**
     * 判断结果是否可回答。
     *
     * @return 是否可回答
     */
    public boolean hasResult() {
        if (plan != null && plan.getQueryType() == StructuredQueryType.COUNT) {
            return true;
        }
        return count > 0 || !rowEvidences.isEmpty() || !groupCounts.isEmpty();
    }
}
