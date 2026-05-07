package com.xbk.lattice.query.structured;

import com.xbk.lattice.infra.persistence.StructuredTableJdbcRepository;
import com.xbk.lattice.infra.persistence.StructuredTableGroupCountRecord;
import com.xbk.lattice.infra.persistence.StructuredTableRowEvidence;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 结构化查询执行器
 *
 * 职责：基于结构化表格事实执行确定性过滤、投影与聚合
 *
 * @author xiexu
 */
@Service
public class StructuredQueryExecutor {

    private static final int DEFAULT_ROW_LIMIT = 20;

    private static final int GROUP_COUNT_LIMIT = 100;

    private final StructuredTableJdbcRepository structuredTableJdbcRepository;

    private final StructuredColumnNameResolver structuredColumnNameResolver;

    /**
     * 创建结构化查询执行器。
     *
     * @param structuredTableJdbcRepository 结构化表格仓储
     */
    public StructuredQueryExecutor(StructuredTableJdbcRepository structuredTableJdbcRepository) {
        this.structuredTableJdbcRepository = structuredTableJdbcRepository;
        this.structuredColumnNameResolver = new StructuredColumnNameResolver();
    }

    /**
     * 执行结构化查询计划。
     *
     * @param plan 查询计划
     * @return 查询结果
     */
    public StructuredQueryResult execute(StructuredQueryPlan plan) {
        if (plan == null) {
            return new StructuredQueryResult(plan, List.of(), 0L);
        }
        if (plan.getQueryType() == StructuredQueryType.COUNT) {
            if (plan.getFilters().isEmpty()) {
                return new StructuredQueryResult(plan, List.of(), 0L);
            }
            long count = structuredTableJdbcRepository.countRowsByFilters(plan.getFilters());
            return new StructuredQueryResult(plan, List.of(), count);
        }
        if (plan.getQueryType() == StructuredQueryType.GROUP_BY) {
            List<StructuredTableGroupCountRecord> groupCounts = structuredTableJdbcRepository.groupCountByField(
                    plan.getFilters(),
                    plan.getGroupByField(),
                    GROUP_COUNT_LIMIT
            );
            return new StructuredQueryResult(plan, List.of(), groupCounts, groupCounts.size());
        }
        if (plan.getQueryType() == StructuredQueryType.ROW_COMPARE) {
            List<StructuredTableRowEvidence> rowEvidences = new ArrayList<StructuredTableRowEvidence>();
            for (Map<String, String> compareFilter : plan.getCompareFilters()) {
                List<StructuredTableRowEvidence> matchedRows = structuredTableJdbcRepository.findRowsByFilters(
                        compareFilter,
                        DEFAULT_ROW_LIMIT
                );
                List<StructuredTableRowEvidence> projectedRows = filterByProjectionCoverage(matchedRows, plan);
                if (!projectedRows.isEmpty()) {
                    rowEvidences.add(projectedRows.get(0));
                }
                else if (!matchedRows.isEmpty()) {
                    rowEvidences.add(matchedRows.get(0));
                }
            }
            return new StructuredQueryResult(plan, rowEvidences, rowEvidences.size());
        }
        if (plan.getFilters().isEmpty()) {
            return new StructuredQueryResult(plan, List.of(), 0L);
        }
        List<StructuredTableRowEvidence> rowEvidences = structuredTableJdbcRepository.findRowsByFilters(
                plan.getFilters(),
                DEFAULT_ROW_LIMIT
        );
        List<StructuredTableRowEvidence> projectedRows = filterByProjectionCoverage(rowEvidences, plan);
        return new StructuredQueryResult(plan, projectedRows, projectedRows.size());
    }

    private List<StructuredTableRowEvidence> filterByProjectionCoverage(
            List<StructuredTableRowEvidence> rowEvidences,
            StructuredQueryPlan plan
    ) {
        if (rowEvidences == null || rowEvidences.isEmpty()) {
            return List.of();
        }
        if (plan == null || plan.getProjections().isEmpty()) {
            return rowEvidences;
        }
        List<StructuredTableRowEvidence> projectedRows = new ArrayList<StructuredTableRowEvidence>();
        for (StructuredTableRowEvidence rowEvidence : rowEvidences) {
            boolean hasProjectionCells = structuredColumnNameResolver.hasProjectionCells(
                    rowEvidence.getCellRecords(),
                    plan.getProjections()
            );
            if (hasProjectionCells) {
                projectedRows.add(rowEvidence);
            }
        }
        if (!projectedRows.isEmpty()) {
            return projectedRows;
        }
        return rowEvidences;
    }
}
