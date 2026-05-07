package com.xbk.lattice.query.structured;

import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.infra.persistence.StructuredTableCellRecord;
import com.xbk.lattice.infra.persistence.StructuredTableGroupCountRecord;
import com.xbk.lattice.infra.persistence.StructuredTableRowEvidence;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.GenerationMode;
import com.xbk.lattice.query.domain.ModelExecutionStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 结构化查询服务
 *
 * 职责：为 Query 主入口提供通用结构化表格确定性问答短路
 *
 * @author xiexu
 */
@Service
public class StructuredQueryService {

    private final StructuredQueryPlanner structuredQueryPlanner;

    private final StructuredQueryExecutor structuredQueryExecutor;

    private final StructuredQueryAnswerRenderer structuredQueryAnswerRenderer;

    private final StructuredColumnNameResolver structuredColumnNameResolver;

    /**
     * 创建结构化查询服务。
     *
     * @param structuredQueryPlanner 查询计划生成器
     * @param structuredQueryExecutor 查询执行器
     */
    public StructuredQueryService(
            StructuredQueryPlanner structuredQueryPlanner,
            StructuredQueryExecutor structuredQueryExecutor
    ) {
        this.structuredQueryPlanner = structuredQueryPlanner;
        this.structuredQueryExecutor = structuredQueryExecutor;
        this.structuredColumnNameResolver = new StructuredColumnNameResolver();
        this.structuredQueryAnswerRenderer = new StructuredQueryAnswerRenderer();
    }

    /**
     * 尝试回答结构化表格问题。
     *
     * @param question 查询问题
     * @param queryId 查询标识
     * @return 查询响应；无法形成结构化计划或无结果时为空
     */
    public Optional<QueryResponse> tryAnswer(String question, String queryId) {
        Optional<StructuredQueryPlan> optionalPlan = structuredQueryPlanner.plan(question);
        if (optionalPlan.isEmpty()) {
            return Optional.empty();
        }
        StructuredQueryResult result = structuredQueryExecutor.execute(optionalPlan.orElseThrow());
        if (!result.hasResult() && result.getPlan().getQueryType() != StructuredQueryType.COUNT) {
            return Optional.empty();
        }
        if (!isEvidenceConsistent(result)) {
            return Optional.empty();
        }
        QueryResponse queryResponse = new QueryResponse(
                structuredQueryAnswerRenderer.renderAnswer(result),
                structuredQueryAnswerRenderer.renderSources(result),
                List.of(),
                queryId,
                result.hasResult() ? "PASSED" : "NEEDS_REVIEW",
                result.hasResult() ? AnswerOutcome.SUCCESS : AnswerOutcome.NO_RELEVANT_KNOWLEDGE,
                GenerationMode.RULE_BASED,
                ModelExecutionStatus.SKIPPED,
                null,
                null,
                "STRUCTURED_QUERY",
                List.of(),
                structuredQueryAnswerRenderer.renderStructuredEvidence(result)
        );
        return Optional.of(queryResponse);
    }

    private boolean isEvidenceConsistent(StructuredQueryResult result) {
        StructuredQueryPlan plan = result.getPlan();
        if (plan.getQueryType() == StructuredQueryType.COUNT) {
            return true;
        }
        if (plan.getQueryType() == StructuredQueryType.GROUP_BY) {
            return isGroupEvidenceConsistent(result);
        }
        for (StructuredTableRowEvidence rowEvidence : result.getRowEvidences()) {
            if (!rowMatchesFilters(rowEvidence, plan.getFilters())) {
                return false;
            }
            if (!hasProjectionCells(rowEvidence, plan.getProjections())) {
                return false;
            }
        }
        if (plan.getQueryType() == StructuredQueryType.ROW_COMPARE) {
            return result.getRowEvidences().size() >= 2;
        }
        return true;
    }

    private boolean isGroupEvidenceConsistent(StructuredQueryResult result) {
        StructuredQueryPlan plan = result.getPlan();
        if (plan.getGroupByField() == null || plan.getGroupByField().isBlank()) {
            return false;
        }
        if (result.getGroupCounts().isEmpty()) {
            return false;
        }
        for (StructuredTableGroupCountRecord groupCount : result.getGroupCounts()) {
            if (!normalize(plan.getGroupByField()).equals(normalize(groupCount.getColumnName()))) {
                return false;
            }
            if (groupCount.getCount() <= 0L) {
                return false;
            }
        }
        return true;
    }

    private boolean rowMatchesFilters(
            StructuredTableRowEvidence rowEvidence,
            Map<String, String> filters
    ) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, String> filter : filters.entrySet()) {
            StructuredTableCellRecord cellRecord = findCell(rowEvidence, filter.getKey());
            if (cellRecord == null || !normalize(filter.getValue()).equals(normalize(cellRecord.getNormalizedValue()))) {
                return false;
            }
        }
        return true;
    }

    private boolean hasProjectionCells(
            StructuredTableRowEvidence rowEvidence,
            List<String> projections
    ) {
        if (projections == null || projections.isEmpty()) {
            return true;
        }
        for (String projection : projections) {
            if (findCell(rowEvidence, projection) == null) {
                return false;
            }
        }
        return true;
    }

    private StructuredTableCellRecord findCell(StructuredTableRowEvidence rowEvidence, String columnName) {
        return structuredColumnNameResolver.findCell(rowEvidence.getCellRecords(), columnName);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
