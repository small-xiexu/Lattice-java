package com.xbk.lattice.documentparse.extractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * PDF 坐标文本表格格式化器
 *
 * 职责：基于文本坐标对齐关系，把 PDF 中散开的多列表格行重建为可检索文本
 *
 * @author xiexu
 */
final class PdfPositionedTextTableFormatter {

    private static final float ROW_TOLERANCE = 3.0F;

    private static final float COLUMN_ALIGNMENT_TOLERANCE = 14.0F;

    private static final float COLUMN_GAP_THRESHOLD = 24.0F;

    private static final float COLUMN_START_GAP_THRESHOLD = 80.0F;

    private static final float CONTINUATION_ROW_GAP_THRESHOLD = 22.0F;

    private static final int MIN_TABLE_ROW_COUNT = 2;

    private static final int MIN_ALIGNED_CELL_COUNT = 2;

    private static final int MAX_CELL_LENGTH = 240;

    /**
     * 格式化单页中的表格文本。
     *
     * @param pageNumber 页码
     * @param positionedTexts 坐标文本片段
     * @return 表格补充文本
     */
    String formatTables(int pageNumber, List<PositionedText> positionedTexts) {
        if (positionedTexts == null || positionedTexts.isEmpty()) {
            return "";
        }
        List<TableRow> tableRows = buildRows(positionedTexts);
        List<TableRow> candidateRows = selectCandidateRows(tableRows);
        if (candidateRows.size() < MIN_TABLE_ROW_COUNT) {
            return "";
        }
        List<ColumnCluster> columnClusters = buildColumnClusters(candidateRows);
        if (columnClusters.size() < MIN_ALIGNED_CELL_COUNT) {
            return "";
        }
        List<List<TableRow>> tableBlocks = buildTableBlocks(tableRows, columnClusters);
        return renderTableBlocks(pageNumber, tableBlocks, columnClusters);
    }

    /**
     * 按 y 坐标把文本片段归并为行。
     *
     * @param positionedTexts 坐标文本片段
     * @return 文本行
     */
    private List<TableRow> buildRows(List<PositionedText> positionedTexts) {
        List<PositionedText> sortedTexts = new ArrayList<PositionedText>();
        for (PositionedText positionedText : positionedTexts) {
            if (positionedText != null && hasText(positionedText.getText())) {
                sortedTexts.add(positionedText);
            }
        }
        sortedTexts.sort(Comparator.comparing(PositionedText::getY).thenComparing(PositionedText::getX));

        List<TableRow> rows = new ArrayList<TableRow>();
        TableRow currentRow = null;
        for (PositionedText positionedText : sortedTexts) {
            if (currentRow == null || Math.abs(currentRow.getY() - positionedText.getY()) > ROW_TOLERANCE) {
                currentRow = new TableRow(positionedText.getY());
                rows.add(currentRow);
            }
            currentRow.add(positionedText);
        }
        for (TableRow row : rows) {
            row.buildCells();
        }
        return rows;
    }

    /**
     * 选出具备多列结构的候选行。
     *
     * @param tableRows 全部文本行
     * @return 候选行
     */
    private List<TableRow> selectCandidateRows(List<TableRow> tableRows) {
        List<TableRow> candidateRows = new ArrayList<TableRow>();
        for (TableRow tableRow : tableRows) {
            if (tableRow.getCells().size() >= MIN_ALIGNED_CELL_COUNT) {
                candidateRows.add(tableRow);
            }
        }
        return candidateRows;
    }

    /**
     * 从候选行中归纳列位置簇。
     *
     * @param candidateRows 候选行
     * @return 列位置簇
     */
    private List<ColumnCluster> buildColumnClusters(List<TableRow> candidateRows) {
        List<ColumnCluster> clusters = new ArrayList<ColumnCluster>();
        for (TableRow candidateRow : candidateRows) {
            for (TextCell cell : candidateRow.getCells()) {
                ColumnCluster matchedCluster = findCluster(clusters, cell.getX());
                if (matchedCluster == null) {
                    clusters.add(new ColumnCluster(cell.getX()));
                }
                else {
                    matchedCluster.add(cell.getX());
                }
            }
        }
        List<ColumnCluster> retainedClusters = new ArrayList<ColumnCluster>();
        for (ColumnCluster cluster : clusters) {
            if (cluster.getCount() >= MIN_TABLE_ROW_COUNT) {
                retainedClusters.add(cluster);
            }
        }
        retainedClusters.sort(Comparator.comparing(ColumnCluster::getX));
        return retainedClusters;
    }

    /**
     * 查找能容纳当前 x 坐标的列位置簇。
     *
     * @param clusters 已有列位置簇
     * @param x 当前 x 坐标
     * @return 匹配的列位置簇
     */
    private ColumnCluster findCluster(List<ColumnCluster> clusters, float x) {
        ColumnCluster bestCluster = null;
        float bestDistance = Float.MAX_VALUE;
        for (ColumnCluster cluster : clusters) {
            float distance = Math.abs(cluster.getX() - x);
            if (distance <= COLUMN_ALIGNMENT_TOLERANCE && distance < bestDistance) {
                bestCluster = cluster;
                bestDistance = distance;
            }
        }
        return bestCluster;
    }

    /**
     * 按连续表格行切分表格块。
     *
     * @param tableRows 全部文本行
     * @param columnClusters 列位置簇
     * @return 表格块
     */
    private List<List<TableRow>> buildTableBlocks(List<TableRow> tableRows, List<ColumnCluster> columnClusters) {
        List<List<TableRow>> tableBlocks = new ArrayList<List<TableRow>>();
        List<TableRow> currentBlock = new ArrayList<TableRow>();
        for (TableRow tableRow : tableRows) {
            if (isAlignedTableRow(tableRow, columnClusters)) {
                currentBlock.add(tableRow);
            }
            else {
                flushTableBlock(tableBlocks, currentBlock);
            }
        }
        flushTableBlock(tableBlocks, currentBlock);
        return tableBlocks;
    }

    /**
     * 判断当前行是否与表格列对齐。
     *
     * @param tableRow 文本行
     * @param columnClusters 列位置簇
     * @return 对齐返回 true
     */
    private boolean isAlignedTableRow(TableRow tableRow, List<ColumnCluster> columnClusters) {
        if (tableRow.getCells().size() < MIN_ALIGNED_CELL_COUNT) {
            return false;
        }
        Set<ColumnCluster> matchedClusters = new HashSet<ColumnCluster>();
        for (TextCell cell : tableRow.getCells()) {
            ColumnCluster matchedCluster = findCluster(columnClusters, cell.getX());
            if (matchedCluster != null) {
                matchedClusters.add(matchedCluster);
            }
        }
        return matchedClusters.size() >= MIN_ALIGNED_CELL_COUNT;
    }

    /**
     * 刷新当前表格块。
     *
     * @param tableBlocks 表格块集合
     * @param currentBlock 当前表格块
     */
    private void flushTableBlock(List<List<TableRow>> tableBlocks, List<TableRow> currentBlock) {
        if (currentBlock.size() >= MIN_TABLE_ROW_COUNT) {
            tableBlocks.add(new ArrayList<TableRow>(currentBlock));
        }
        currentBlock.clear();
    }

    /**
     * 渲染表格块为可检索文本。
     *
     * @param pageNumber 页码
     * @param tableBlocks 表格块
     * @return 可检索表格文本
     */
    private String renderTableBlocks(
            int pageNumber,
            List<List<TableRow>> tableBlocks,
            List<ColumnCluster> columnClusters
    ) {
        if (tableBlocks.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int blockIndex = 0; blockIndex < tableBlocks.size(); blockIndex++) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("=== Table: page ")
                    .append(pageNumber)
                    .append(" block ")
                    .append(blockIndex + 1)
                    .append(" ===")
                    .append("\n");
            List<AlignedTableRow> alignedRows = alignAndMergeRows(tableBlocks.get(blockIndex), columnClusters);
            for (AlignedTableRow alignedRow : alignedRows) {
                builder.append("table_row: ").append(renderAlignedCells(alignedRow)).append("\n");
            }
        }
        return builder.toString().trim();
    }

    /**
     * 对齐并合并表格续行。
     *
     * @param tableBlock 表格块
     * @param columnClusters 列位置簇
     * @return 对齐后的表格行
     */
    private List<AlignedTableRow> alignAndMergeRows(List<TableRow> tableBlock, List<ColumnCluster> columnClusters) {
        List<AlignedTableRow> alignedRows = new ArrayList<AlignedTableRow>();
        for (TableRow tableRow : tableBlock) {
            AlignedTableRow alignedRow = alignRow(tableRow, columnClusters);
            if (alignedRow.isEmpty()) {
                continue;
            }
            if (!alignedRows.isEmpty() && shouldMergeContinuationRow(alignedRows.get(alignedRows.size() - 1), alignedRow)) {
                alignedRows.get(alignedRows.size() - 1).merge(alignedRow);
            }
            else {
                alignedRows.add(alignedRow);
            }
        }
        return alignedRows;
    }

    /**
     * 把单行单元格对齐到列位置。
     *
     * @param tableRow 表格行
     * @param columnClusters 列位置簇
     * @return 对齐后的表格行
     */
    private AlignedTableRow alignRow(TableRow tableRow, List<ColumnCluster> columnClusters) {
        AlignedTableRow alignedRow = new AlignedTableRow(columnClusters.size(), tableRow.getY());
        for (TextCell cell : tableRow.getCells()) {
            int clusterIndex = findClusterIndex(columnClusters, cell.getX());
            if (clusterIndex >= 0) {
                alignedRow.append(clusterIndex, cell.getText());
            }
        }
        return alignedRow;
    }

    /**
     * 查找单元格对应的列序号。
     *
     * @param columnClusters 列位置簇
     * @param x 单元格 x 坐标
     * @return 列序号；未命中返回 -1
     */
    private int findClusterIndex(List<ColumnCluster> columnClusters, float x) {
        int matchedIndex = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int index = 0; index < columnClusters.size(); index++) {
            ColumnCluster columnCluster = columnClusters.get(index);
            float distance = Math.abs(columnCluster.getX() - x);
            if (distance <= COLUMN_ALIGNMENT_TOLERANCE && distance < bestDistance) {
                matchedIndex = index;
                bestDistance = distance;
            }
        }
        return matchedIndex;
    }

    /**
     * 判断当前行是否是上一行单元格换行后的续行。
     *
     * @param previousRow 上一行
     * @param currentRow 当前行
     * @return 是续行返回 true
     */
    private boolean shouldMergeContinuationRow(AlignedTableRow previousRow, AlignedTableRow currentRow) {
        if (currentRow.getY() - previousRow.getY() > CONTINUATION_ROW_GAP_THRESHOLD) {
            return false;
        }
        int currentFirstFilledIndex = currentRow.firstFilledIndex();
        if (currentFirstFilledIndex <= 0) {
            return false;
        }
        return previousRow.hasTextAt(currentFirstFilledIndex);
    }

    /**
     * 渲染对齐后的单元格。
     *
     * @param alignedRow 对齐后的表格行
     * @return 单行文本
     */
    private String renderAlignedCells(AlignedTableRow alignedRow) {
        List<String> cellTexts = new ArrayList<String>();
        int lastFilledIndex = alignedRow.lastFilledIndex();
        for (int index = 0; index <= lastFilledIndex; index++) {
            String normalizedText = normalizeCellText(alignedRow.getText(index));
            if (!normalizedText.isBlank()) {
                cellTexts.add(normalizedText);
            }
        }
        return String.join(" | ", cellTexts);
    }

    /**
     * 归一化单元格文本。
     *
     * @param text 原始文本
     * @return 归一化文本
     */
    private String normalizeCellText(String text) {
        if (!hasText(text)) {
            return "";
        }
        String normalizedText = text.replaceAll("\\s+", " ").trim();
        if (normalizedText.length() <= MAX_CELL_LENGTH) {
            return normalizedText;
        }
        return normalizedText.substring(0, MAX_CELL_LENGTH) + "...";
    }

    /**
     * 判断文本是否有值。
     *
     * @param text 文本
     * @return 有值返回 true
     */
    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    /**
     * 对齐后的表格行。
     *
     * 职责：按列序号保存单元格文本，并支持续行合并
     *
     * @author xiexu
     */
    private static final class AlignedTableRow {

        private final List<StringBuilder> cellBuilders = new ArrayList<StringBuilder>();

        private final float y;

        /**
         * 创建对齐后的表格行。
         *
         * @param columnCount 列数量
         * @param y 行基线 y 坐标
         */
        private AlignedTableRow(int columnCount, float y) {
            this.y = y;
            for (int index = 0; index < columnCount; index++) {
                cellBuilders.add(new StringBuilder());
            }
        }

        /**
         * 向指定列追加文本。
         *
         * @param columnIndex 列序号
         * @param text 文本
         */
        private void append(int columnIndex, String text) {
            if (columnIndex < 0 || columnIndex >= cellBuilders.size() || text == null || text.isBlank()) {
                return;
            }
            StringBuilder cellBuilder = cellBuilders.get(columnIndex);
            if (cellBuilder.length() > 0) {
                cellBuilder.append(" ");
            }
            cellBuilder.append(text.trim());
        }

        /**
         * 合并续行。
         *
         * @param continuationRow 续行
         */
        private void merge(AlignedTableRow continuationRow) {
            for (int index = 0; index < cellBuilders.size(); index++) {
                append(index, continuationRow.getText(index));
            }
        }

        /**
         * 返回指定列文本。
         *
         * @param columnIndex 列序号
         * @return 列文本
         */
        private String getText(int columnIndex) {
            if (columnIndex < 0 || columnIndex >= cellBuilders.size()) {
                return "";
            }
            return cellBuilders.get(columnIndex).toString();
        }

        /**
         * 判断指定列是否有文本。
         *
         * @param columnIndex 列序号
         * @return 有文本返回 true
         */
        private boolean hasTextAt(int columnIndex) {
            return columnIndex >= 0
                    && columnIndex < cellBuilders.size()
                    && cellBuilders.get(columnIndex).length() > 0;
        }

        /**
         * 返回是否为空行。
         *
         * @return 空行返回 true
         */
        private boolean isEmpty() {
            return firstFilledIndex() < 0;
        }

        /**
         * 返回首个有值列序号。
         *
         * @return 首个有值列序号
         */
        private int firstFilledIndex() {
            for (int index = 0; index < cellBuilders.size(); index++) {
                if (cellBuilders.get(index).length() > 0) {
                    return index;
                }
            }
            return -1;
        }

        /**
         * 返回最后一个有值列序号。
         *
         * @return 最后一个有值列序号
         */
        private int lastFilledIndex() {
            for (int index = cellBuilders.size() - 1; index >= 0; index--) {
                if (cellBuilders.get(index).length() > 0) {
                    return index;
                }
            }
            return -1;
        }

        /**
         * 返回行基线 y 坐标。
         *
         * @return y 坐标
         */
        private float getY() {
            return y;
        }
    }

    /**
     * 坐标文本片段。
     *
     * 职责：承载 PDF 单个文本片段的坐标范围
     *
     * @author xiexu
     */
    static final class PositionedText {

        private final String text;

        private final float x;

        private final float y;

        private final float width;

        /**
         * 创建坐标文本片段。
         *
         * @param text 文本
         * @param x 左侧 x 坐标
         * @param y 基线 y 坐标
         * @param width 文本宽度
         */
        PositionedText(String text, float x, float y, float width) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.width = width;
        }

        /**
         * 返回文本。
         *
         * @return 文本
         */
        String getText() {
            return text;
        }

        /**
         * 返回左侧 x 坐标。
         *
         * @return x 坐标
         */
        float getX() {
            return x;
        }

        /**
         * 返回基线 y 坐标。
         *
         * @return y 坐标
         */
        float getY() {
            return y;
        }

        /**
         * 返回右侧 x 坐标。
         *
         * @return 右侧 x 坐标
         */
        float getRight() {
            return x + width;
        }
    }

    /**
     * 表格行。
     *
     * 职责：聚合同一 y 坐标附近的文本片段并切分单元格
     *
     * @author xiexu
     */
    private static final class TableRow {

        private final float y;

        private final List<PositionedText> positionedTexts = new ArrayList<PositionedText>();

        private final List<TextCell> cells = new ArrayList<TextCell>();

        /**
         * 创建表格行。
         *
         * @param y 行基线 y 坐标
         */
        private TableRow(float y) {
            this.y = y;
        }

        /**
         * 添加坐标文本片段。
         *
         * @param positionedText 坐标文本片段
         */
        private void add(PositionedText positionedText) {
            positionedTexts.add(positionedText);
        }

        /**
         * 基于 x 坐标间隔切分单元格。
         */
        private void buildCells() {
            positionedTexts.sort(Comparator.comparing(PositionedText::getX));
            TextCellBuilder cellBuilder = new TextCellBuilder();
            for (PositionedText positionedText : positionedTexts) {
                if (cellBuilder.hasText()) {
                    float gap = positionedText.getX() - cellBuilder.getRight();
                    float startGap = positionedText.getX() - cellBuilder.getLastX();
                    if (gap > COLUMN_GAP_THRESHOLD || startGap > COLUMN_START_GAP_THRESHOLD) {
                        cellBuilder.flush(cells);
                    }
                }
                cellBuilder.append(positionedText);
            }
            cellBuilder.flush(cells);
        }

        /**
         * 返回行基线 y 坐标。
         *
         * @return y 坐标
         */
        private float getY() {
            return y;
        }

        /**
         * 返回单元格。
         *
         * @return 单元格列表
         */
        private List<TextCell> getCells() {
            return cells;
        }
    }

    /**
     * 文本单元格。
     *
     * 职责：表示同一表格行内的一段列文本
     *
     * @author xiexu
     */
    private static final class TextCell {

        private final String text;

        private final float x;

        private final float right;

        /**
         * 创建文本单元格。
         *
         * @param text 文本
         * @param x 左侧 x 坐标
         * @param right 右侧 x 坐标
         */
        private TextCell(String text, float x, float right) {
            this.text = text;
            this.x = x;
            this.right = right;
        }

        /**
         * 返回文本。
         *
         * @return 文本
         */
        private String getText() {
            return text;
        }

        /**
         * 返回左侧 x 坐标。
         *
         * @return x 坐标
         */
        private float getX() {
            return x;
        }
    }

    /**
     * 文本单元格构建器。
     *
     * 职责：把相邻文本片段合并为同一个单元格
     *
     * @author xiexu
     */
    private static final class TextCellBuilder {

        private final StringBuilder textBuilder = new StringBuilder();

        private float x;

        private float right;

        private float lastX;

        /**
         * 追加坐标文本片段。
         *
         * @param positionedText 坐标文本片段
         */
        private void append(PositionedText positionedText) {
            if (!hasText()) {
                x = positionedText.getX();
                right = positionedText.getRight();
            }
            else {
                textBuilder.append(" ");
                right = Math.max(right, positionedText.getRight());
            }
            lastX = positionedText.getX();
            textBuilder.append(positionedText.getText());
        }

        /**
         * 刷新单元格到目标列表。
         *
         * @param cells 目标单元格列表
         */
        private void flush(List<TextCell> cells) {
            if (hasText()) {
                cells.add(new TextCell(textBuilder.toString().trim(), x, right));
            }
            textBuilder.setLength(0);
            x = 0.0F;
            right = 0.0F;
            lastX = 0.0F;
        }

        /**
         * 返回是否已有文本。
         *
         * @return 有文本返回 true
         */
        private boolean hasText() {
            return textBuilder.length() > 0;
        }

        /**
         * 返回当前右侧 x 坐标。
         *
         * @return 右侧 x 坐标
         */
        private float getRight() {
            return right;
        }

        /**
         * 返回上一个片段的左侧 x 坐标。
         *
         * @return 上一个片段 x 坐标
         */
        private float getLastX() {
            return lastX;
        }
    }

    /**
     * 列位置簇。
     *
     * 职责：聚合同一列在多行中的左侧 x 坐标
     *
     * @author xiexu
     */
    private static final class ColumnCluster {

        private float x;

        private int count;

        /**
         * 创建列位置簇。
         *
         * @param x 初始 x 坐标
         */
        private ColumnCluster(float x) {
            this.x = x;
            this.count = 1;
        }

        /**
         * 追加同列 x 坐标。
         *
         * @param nextX 新 x 坐标
         */
        private void add(float nextX) {
            x = ((x * count) + nextX) / (count + 1);
            count++;
        }

        /**
         * 返回聚合后的 x 坐标。
         *
         * @return x 坐标
         */
        private float getX() {
            return x;
        }

        /**
         * 返回聚合次数。
         *
         * @return 聚合次数
         */
        private int getCount() {
            return count;
        }
    }
}
