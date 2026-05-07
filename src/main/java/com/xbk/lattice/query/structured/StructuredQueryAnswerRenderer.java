package com.xbk.lattice.query.structured;

import com.xbk.lattice.api.query.QuerySourceResponse;
import com.xbk.lattice.api.query.QueryStructuredCellEvidenceResponse;
import com.xbk.lattice.api.query.QueryStructuredEvidenceResponse;
import com.xbk.lattice.api.query.QueryStructuredGroupEvidenceResponse;
import com.xbk.lattice.api.query.QueryStructuredRowEvidenceResponse;
import com.xbk.lattice.infra.persistence.StructuredTableCellRecord;
import com.xbk.lattice.infra.persistence.StructuredTableGroupCountRecord;
import com.xbk.lattice.infra.persistence.StructuredTableRecord;
import com.xbk.lattice.infra.persistence.StructuredTableRowEvidence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 结构化查询答案渲染器
 *
 * 职责：把确定性结构化查询结果渲染为用户可读答案与来源
 *
 * @author xiexu
 */
public class StructuredQueryAnswerRenderer {

    private final StructuredColumnNameResolver structuredColumnNameResolver = new StructuredColumnNameResolver();

    /**
     * 渲染答案正文。
     *
     * @param result 结构化查询结果
     * @return 答案正文
     */
    public String renderAnswer(StructuredQueryResult result) {
        if (result == null || result.getPlan() == null || !result.hasResult()) {
            return "未找到满足结构化条件的记录。";
        }
        if (result.getPlan().getQueryType() == StructuredQueryType.COUNT) {
            return "满足条件的记录数为 " + result.getCount() + "。";
        }
        if (result.getPlan().getQueryType() == StructuredQueryType.GROUP_BY) {
            return renderGroupCounts(result);
        }
        if (result.getPlan().getQueryType() == StructuredQueryType.ROW_COMPARE) {
            return renderComparison(result);
        }
        List<String> lines = new ArrayList<String>();
        for (StructuredTableRowEvidence rowEvidence : result.getRowEvidences()) {
            lines.add(renderRow(result.getPlan(), rowEvidence));
        }
        return String.join("\n", lines);
    }

    /**
     * 渲染来源列表。
     *
     * @param result 结构化查询结果
     * @return 来源列表
     */
    public List<QuerySourceResponse> renderSources(StructuredQueryResult result) {
        if (result == null) {
            return List.of();
        }
        Map<String, QuerySourceResponse> sourcesByKey = new LinkedHashMap<String, QuerySourceResponse>();
        for (StructuredTableRowEvidence rowEvidence : result.getRowEvidences()) {
            StructuredTableRecord tableRecord = rowEvidence.getTableRecord();
            String sourcePath = tableRecord.getSourcePath();
            String key = sourcePath + "#" + tableRecord.getTableName();
            sourcesByKey.putIfAbsent(
                    key,
                    buildSourceResponse(
                            tableRecord.getSourceId(),
                            tableRecord.getId(),
                            sourcePath,
                            tableRecord.getTableName(),
                            tableRecord.getSheetName()
                    )
            );
        }
        for (StructuredTableGroupCountRecord groupCount : result.getGroupCounts()) {
            String sourcePath = groupCount.getSourcePath();
            String key = sourcePath + "#" + groupCount.getTableName();
            sourcesByKey.putIfAbsent(
                    key,
                    buildSourceResponse(
                            groupCount.getSourceId(),
                            groupCount.getTableId(),
                            sourcePath,
                            groupCount.getTableName(),
                            groupCount.getSheetName()
                    )
            );
        }
        return new ArrayList<QuerySourceResponse>(sourcesByKey.values());
    }

    /**
     * 渲染结构化证据。
     *
     * @param result 结构化查询结果
     * @return 结构化证据
     */
    public QueryStructuredEvidenceResponse renderStructuredEvidence(StructuredQueryResult result) {
        if (result == null || result.getPlan() == null) {
            return null;
        }
        List<QueryStructuredRowEvidenceResponse> rows = new ArrayList<QueryStructuredRowEvidenceResponse>();
        for (StructuredTableRowEvidence rowEvidence : result.getRowEvidences()) {
            rows.add(renderRowEvidence(result.getPlan(), rowEvidence));
        }
        List<QueryStructuredGroupEvidenceResponse> groups = new ArrayList<QueryStructuredGroupEvidenceResponse>();
        for (StructuredTableGroupCountRecord groupCount : result.getGroupCounts()) {
            groups.add(new QueryStructuredGroupEvidenceResponse(
                    result.getPlan().getGroupByField(),
                    groupCount.getCellValue(),
                    groupCount.getNormalizedValue(),
                    groupCount.getCount(),
                    result.getPlan().getFilters()
            ));
        }
        return new QueryStructuredEvidenceResponse(result.getPlan().getQueryType().name(), rows, groups);
    }

    private QueryStructuredRowEvidenceResponse renderRowEvidence(
            StructuredQueryPlan plan,
            StructuredTableRowEvidence rowEvidence
    ) {
        StructuredTableRecord tableRecord = rowEvidence.getTableRecord();
        List<QueryStructuredCellEvidenceResponse> cells = new ArrayList<QueryStructuredCellEvidenceResponse>();
        for (StructuredTableCellRecord cellRecord : rowEvidence.getCellRecords()) {
            String role = resolveCellRole(plan, cellRecord);
            if (role == null) {
                continue;
            }
            cells.add(new QueryStructuredCellEvidenceResponse(
                    cellRecord.getColumnName(),
                    cellRecord.getColumnIndex(),
                    cellRecord.getCellValue(),
                    cellRecord.getNormalizedValue(),
                    role
            ));
        }
        return new QueryStructuredRowEvidenceResponse(
                tableRecord.getSourcePath(),
                tableRecord.getTableName(),
                tableRecord.getSheetName(),
                rowEvidence.getRowRecord().getRowNumber(),
                cells
        );
    }

    private String renderGroupCounts(StructuredQueryResult result) {
        List<String> lines = new ArrayList<String>();
        String groupByField = result.getPlan().getGroupByField();
        lines.add("按 " + groupByField + " 统计数量：");
        long totalCount = 0L;
        for (StructuredTableGroupCountRecord groupCount : result.getGroupCounts()) {
            totalCount += groupCount.getCount();
            lines.add("- " + groupCount.getCellValue() + "=" + groupCount.getCount());
        }
        if (totalCount > 0L) {
            lines.add("总行数=" + totalCount);
        }
        return String.join("\n", lines);
    }

    private String renderComparison(StructuredQueryResult result) {
        List<StructuredTableRowEvidence> rowEvidences = result.getRowEvidences();
        if (rowEvidences.size() < 2) {
            return "未找到可对比的两条结构化记录。";
        }
        List<String> lines = new ArrayList<String>();
        lines.add("对比结果：");
        List<String> projections = result.getPlan().getProjections();
        if (projections.isEmpty()) {
            lines.add(renderRow(result.getPlan(), rowEvidences.get(0)));
            lines.add(renderRow(result.getPlan(), rowEvidences.get(1)));
            return String.join("\n", lines);
        }
        StructuredTableRowEvidence leftRow = rowEvidences.get(0);
        StructuredTableRowEvidence rightRow = rowEvidences.get(1);
        lines.add("- 左侧：" + renderCompareIdentity(result.getPlan(), leftRow, 0));
        lines.add("- 右侧：" + renderCompareIdentity(result.getPlan(), rightRow, 1));
        for (String projection : projections) {
            StructuredTableCellRecord leftCell = findCell(leftRow.getCellRecords(), projection);
            StructuredTableCellRecord rightCell = findCell(rightRow.getCellRecords(), projection);
            if (leftCell == null && rightCell == null) {
                continue;
            }
            String leftValue = leftCell == null ? "" : leftCell.getCellValue();
            String rightValue = rightCell == null ? "" : rightCell.getCellValue();
            lines.add("- " + projection + ": " + leftValue + " / " + rightValue);
        }
        lines.add("来源：" + renderLocation(leftRow) + "；" + renderLocation(rightRow));
        return String.join("\n", lines);
    }

    private String renderCompareIdentity(
            StructuredQueryPlan plan,
            StructuredTableRowEvidence rowEvidence,
            int compareIndex
    ) {
        if (compareIndex >= plan.getCompareFilters().size()) {
            return renderLocation(rowEvidence);
        }
        List<String> parts = new ArrayList<String>();
        Map<String, String> filters = plan.getCompareFilters().get(compareIndex);
        for (String filterColumnName : filters.keySet()) {
            StructuredTableCellRecord cellRecord = findCell(rowEvidence.getCellRecords(), filterColumnName);
            if (cellRecord != null) {
                parts.add(cellRecord.getColumnName() + "=" + cellRecord.getCellValue());
            }
        }
        if (parts.isEmpty()) {
            return renderLocation(rowEvidence);
        }
        return String.join("; ", parts) + " [→ " + renderLocation(rowEvidence) + "]";
    }

    private String renderRow(StructuredQueryPlan plan, StructuredTableRowEvidence rowEvidence) {
        String location = renderLocation(rowEvidence);
        List<String> projections = plan.getProjections();
        if (projections.isEmpty()) {
            return "- " + rowEvidence.getRowRecord().getRowText() + " [→ " + location + "]";
        }
        List<String> parts = new ArrayList<String>();
        for (String projection : projections) {
            StructuredTableCellRecord cellRecord = findCell(rowEvidence.getCellRecords(), projection);
            if (cellRecord != null) {
                parts.add(cellRecord.getColumnName() + "=" + cellRecord.getCellValue());
            }
        }
        if (parts.isEmpty()) {
            return "- " + rowEvidence.getRowRecord().getRowText() + " [→ " + location + "]";
        }
        List<String> filterParts = new ArrayList<String>();
        for (Map.Entry<String, String> filter : plan.getFilters().entrySet()) {
            StructuredTableCellRecord cellRecord = findCell(rowEvidence.getCellRecords(), filter.getKey());
            if (cellRecord != null) {
                filterParts.add(cellRecord.getColumnName() + "=" + cellRecord.getCellValue());
            }
        }
        if (!filterParts.isEmpty()) {
            parts.addAll(0, filterParts);
        }
        return "- " + String.join("; ", parts) + " [→ " + location + "]";
    }

    private String renderLocation(StructuredTableRowEvidence rowEvidence) {
        StructuredTableRecord tableRecord = rowEvidence.getTableRecord();
        return tableRecord.getSourcePath()
                + " / "
                + tableRecord.getTableName()
                + " row="
                + rowEvidence.getRowRecord().getRowNumber();
    }

    private StructuredTableCellRecord findCell(List<StructuredTableCellRecord> cellRecords, String columnName) {
        return structuredColumnNameResolver.findCell(cellRecords, columnName);
    }

    private String resolveCellRole(StructuredQueryPlan plan, StructuredTableCellRecord cellRecord) {
        for (String filterColumnName : plan.getFilters().keySet()) {
            if (structuredColumnNameResolver.matches(cellRecord.getColumnName(), filterColumnName)) {
                return "filter";
            }
        }
        if (plan.getQueryType() == StructuredQueryType.ROW_COMPARE) {
            for (Map<String, String> compareFilter : plan.getCompareFilters()) {
                for (String filterColumnName : compareFilter.keySet()) {
                    if (structuredColumnNameResolver.matches(cellRecord.getColumnName(), filterColumnName)) {
                        return "filter";
                    }
                }
            }
        }
        for (String projection : plan.getProjections()) {
            if (structuredColumnNameResolver.matches(cellRecord.getColumnName(), projection)) {
                return "projection";
            }
        }
        if (plan.getQueryType() == StructuredQueryType.ROW_COMPARE && !plan.getProjections().isEmpty()) {
            return null;
        }
        if (plan.getProjections().isEmpty()) {
            return "row";
        }
        return null;
    }

    private String buildTitle(StructuredTableRecord tableRecord) {
        if (tableRecord.getSheetName() == null || tableRecord.getSheetName().isBlank()) {
            return tableRecord.getSourcePath() + " / " + tableRecord.getTableName();
        }
        return tableRecord.getSourcePath() + " / " + tableRecord.getSheetName();
    }

    private QuerySourceResponse buildSourceResponse(
            Long sourceId,
            Long tableId,
            String sourcePath,
            String tableName,
            String sheetName
    ) {
        return new QuerySourceResponse(
                sourceId,
                null,
                "structured-table:" + tableId,
                buildTitle(sourcePath, tableName, sheetName),
                List.of(sourcePath),
                "structured_table"
        );
    }

    private String buildTitle(String sourcePath, String tableName, String sheetName) {
        if (sheetName == null || sheetName.isBlank()) {
            return sourcePath + " / " + tableName;
        }
        return sourcePath + " / " + sheetName;
    }
}
