package com.xbk.lattice.infra.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 结构化表格 schema 画像器
 *
 * 职责：基于通用行列统计生成字段画像，不依赖具体文件、业务术语或列名
 *
 * @author xiexu
 */
public class StructuredTableSchemaProfiler {

    private static final int ENUM_DISTINCT_MAX = 20;

    private static final int LONG_TEXT_AVERAGE_LENGTH = 80;

    private static final int LONG_TEXT_MAX_LENGTH = 200;

    private static final double HIGH_UNIQUE_RATIO = 0.9D;

    private static final double TYPE_DOMINANCE_RATIO = 0.8D;

    /**
     * 生成表格 schema 画像。
     *
     * @param tableNode 结构化表格 JSON 节点
     * @return 可写入表元数据的 schema 画像
     */
    public Map<String, Object> profile(JsonNode tableNode) {
        Map<String, Object> profileNode = new LinkedHashMap<String, Object>();
        if (tableNode == null || tableNode.isMissingNode()) {
            return profileNode;
        }
        JsonNode rowsNode = tableNode.path("rows");
        if (!rowsNode.isArray()) {
            return profileNode;
        }
        int rowCount = resolveRowCount(tableNode, rowsNode);
        Map<String, ColumnStats> columnStatsByKey = new LinkedHashMap<String, ColumnStats>();
        collectDeclaredColumns(tableNode, columnStatsByKey);
        collectCellStats(rowsNode, columnStatsByKey);
        List<Map<String, Object>> columnProfiles = buildColumnProfiles(columnStatsByKey, rowCount);
        profileNode.put("version", Integer.valueOf(1));
        profileNode.put("rowCount", Integer.valueOf(rowCount));
        profileNode.put("columnCount", Integer.valueOf(columnProfiles.size()));
        profileNode.put("columns", columnProfiles);
        return profileNode;
    }

    /**
     * 解析数据行数。
     *
     * @param tableNode 结构化表格节点
     * @param rowsNode 行节点
     * @return 数据行数
     */
    private int resolveRowCount(JsonNode tableNode, JsonNode rowsNode) {
        int declaredRowCount = tableNode.path("rowCount").asInt(-1);
        if (declaredRowCount >= 0) {
            return declaredRowCount;
        }
        return rowsNode.size();
    }

    /**
     * 收集表头声明的列。
     *
     * @param tableNode 结构化表格节点
     * @param columnStatsByKey 列画像统计容器
     */
    private void collectDeclaredColumns(JsonNode tableNode, Map<String, ColumnStats> columnStatsByKey) {
        JsonNode columnsNode = tableNode.path("columns");
        if (!columnsNode.isArray()) {
            return;
        }
        for (JsonNode columnNode : columnsNode) {
            int columnIndex = columnNode.path("columnIndex").asInt(0);
            String columnName = columnNode.path("columnName").asText("");
            columnStatsByKey.computeIfAbsent(columnKey(columnIndex, columnName), ignored -> {
                ColumnStats columnStats = new ColumnStats(columnIndex, columnName);
                return columnStats;
            });
        }
    }

    /**
     * 收集单元格统计。
     *
     * @param rowsNode 行节点
     * @param columnStatsByKey 列画像统计容器
     */
    private void collectCellStats(JsonNode rowsNode, Map<String, ColumnStats> columnStatsByKey) {
        for (JsonNode rowNode : rowsNode) {
            JsonNode cellsNode = rowNode.path("cells");
            if (!cellsNode.isArray()) {
                continue;
            }
            Set<String> countedColumns = new LinkedHashSet<String>();
            for (JsonNode cellNode : cellsNode) {
                int columnIndex = cellNode.path("columnIndex").asInt(0);
                String columnName = cellNode.path("columnName").asText("");
                String columnKey = columnKey(columnIndex, columnName);
                if (countedColumns.contains(columnKey)) {
                    continue;
                }
                countedColumns.add(columnKey);
                ColumnStats columnStats = columnStatsByKey.computeIfAbsent(columnKey, ignored -> {
                    ColumnStats stats = new ColumnStats(columnIndex, columnName);
                    return stats;
                });
                columnStats.accept(cellNode);
            }
        }
    }

    /**
     * 构建列画像列表。
     *
     * @param columnStatsByKey 列统计
     * @param rowCount 数据行数
     * @return 列画像列表
     */
    private List<Map<String, Object>> buildColumnProfiles(Map<String, ColumnStats> columnStatsByKey, int rowCount) {
        List<Map<String, Object>> columnProfiles = new ArrayList<Map<String, Object>>();
        for (ColumnStats columnStats : columnStatsByKey.values()) {
            columnProfiles.add(columnStats.toProfile(rowCount));
        }
        return columnProfiles;
    }

    /**
     * 生成列统计 key。
     *
     * @param columnIndex 列序号
     * @param columnName 列名
     * @return 列统计 key
     */
    private String columnKey(int columnIndex, String columnName) {
        String normalizedColumnName = columnName == null ? "" : columnName.trim().toLowerCase(Locale.ROOT);
        return columnIndex + "::" + normalizedColumnName;
    }

    /**
     * 推断单元格值类型。
     *
     * @param valueType 显式值类型
     * @param normalizedValue 归一化值
     * @return 推断值类型
     */
    private static String resolveValueType(String valueType, String normalizedValue) {
        String normalizedType = valueType == null ? "" : valueType.trim().toLowerCase(Locale.ROOT);
        if (StringUtils.hasText(normalizedType) && !"text".equals(normalizedType)) {
            return normalizedType;
        }
        if (!StringUtils.hasText(normalizedValue)) {
            return "blank";
        }
        if (isNumeric(normalizedValue)) {
            return "number";
        }
        if (isDateLike(normalizedValue)) {
            return "date";
        }
        if ("true".equals(normalizedValue) || "false".equals(normalizedValue)) {
            return "boolean";
        }
        return "text";
    }

    /**
     * 判断是否为数值。
     *
     * @param value 值
     * @return 是否数值
     */
    private static boolean isNumeric(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            new BigDecimal(value.trim());
            return true;
        }
        catch (NumberFormatException ex) {
            return false;
        }
    }

    /**
     * 判断是否为日期样式文本。
     *
     * @param value 值
     * @return 是否日期样式
     */
    private static boolean isDateLike(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalizedValue = value.trim();
        return normalizedValue.matches("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}.*")
                || normalizedValue.matches("\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}.*");
    }

    /**
     * 计算比率。
     *
     * @param numerator 分子
     * @param denominator 分母
     * @return 比率
     */
    private static double ratio(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0.0D;
        }
        return (double) numerator / (double) denominator;
    }

    /**
     * 单列统计状态。
     *
     * 职责：累计单列的类型、唯一度与文本长度统计
     *
     * @author xiexu
     */
    private static class ColumnStats {

        private final int columnIndex;

        private final String columnName;

        private final Set<String> distinctValues = new LinkedHashSet<String>();

        private int nonBlankCount;

        private int numericCount;

        private int dateCount;

        private int booleanCount;

        private int textCount;

        private int totalTextLength;

        private int maxTextLength;

        /**
         * 创建单列统计状态。
         *
         * @param columnIndex 列序号
         * @param columnName 列名
         */
        private ColumnStats(int columnIndex, String columnName) {
            this.columnIndex = columnIndex;
            this.columnName = columnName == null ? "" : columnName;
        }

        /**
         * 累计单元格统计。
         *
         * @param cellNode 单元格节点
         */
        private void accept(JsonNode cellNode) {
            String normalizedValue = cellNode.path("normalizedValue").asText("");
            String cellValue = cellNode.path("cellValue").asText(normalizedValue);
            if (!StringUtils.hasText(normalizedValue) && !StringUtils.hasText(cellValue)) {
                return;
            }
            String effectiveValue = StringUtils.hasText(normalizedValue) ? normalizedValue.trim() : cellValue.trim();
            String valueType = cellNode.path("valueType").asText("");
            String resolvedValueType = resolveValueType(valueType, effectiveValue);
            nonBlankCount++;
            distinctValues.add(effectiveValue.toLowerCase(Locale.ROOT));
            int textLength = cellValue == null ? 0 : cellValue.trim().length();
            totalTextLength += textLength;
            maxTextLength = Math.max(maxTextLength, textLength);
            incrementTypeCount(resolvedValueType);
        }

        /**
         * 增加类型计数。
         *
         * @param resolvedValueType 归一化类型
         */
        private void incrementTypeCount(String resolvedValueType) {
            if ("number".equals(resolvedValueType)) {
                numericCount++;
                return;
            }
            if ("date".equals(resolvedValueType)) {
                dateCount++;
                return;
            }
            if ("boolean".equals(resolvedValueType)) {
                booleanCount++;
                return;
            }
            textCount++;
        }

        /**
         * 转换为列画像。
         *
         * @param rowCount 表格数据行数
         * @return 列画像
         */
        private Map<String, Object> toProfile(int rowCount) {
            int blankCount = Math.max(rowCount - nonBlankCount, 0);
            int distinctCount = distinctValues.size();
            double distinctRatio = ratio(distinctCount, Math.max(nonBlankCount, 1));
            double nonBlankRatio = ratio(nonBlankCount, Math.max(rowCount, 1));
            int averageTextLength = nonBlankCount == 0 ? 0 : totalTextLength / nonBlankCount;
            String dominantValueType = dominantValueType();
            boolean highUnique = nonBlankCount >= 2 && distinctRatio >= HIGH_UNIQUE_RATIO;
            boolean enumLike = isEnumLike(distinctCount, distinctRatio);
            boolean numericLike = ratio(numericCount, Math.max(nonBlankCount, 1)) >= TYPE_DOMINANCE_RATIO;
            boolean dateLike = ratio(dateCount, Math.max(nonBlankCount, 1)) >= TYPE_DOMINANCE_RATIO;
            boolean longTextLike = textCount > 0
                    && (averageTextLength >= LONG_TEXT_AVERAGE_LENGTH || maxTextLength >= LONG_TEXT_MAX_LENGTH);
            boolean suspectedPrimaryKey = highUnique && blankCount == 0 && !longTextLike;
            boolean aggregatable = numericLike;
            Map<String, Object> profile = new LinkedHashMap<String, Object>();
            profile.put("columnIndex", Integer.valueOf(columnIndex));
            profile.put("columnName", columnName);
            profile.put("nonBlankCount", Integer.valueOf(nonBlankCount));
            profile.put("blankCount", Integer.valueOf(blankCount));
            profile.put("distinctCount", Integer.valueOf(distinctCount));
            profile.put("distinctRatio", Double.valueOf(distinctRatio));
            profile.put("nonBlankRatio", Double.valueOf(nonBlankRatio));
            profile.put("dominantValueType", dominantValueType);
            profile.put("numericCount", Integer.valueOf(numericCount));
            profile.put("dateCount", Integer.valueOf(dateCount));
            profile.put("booleanCount", Integer.valueOf(booleanCount));
            profile.put("textCount", Integer.valueOf(textCount));
            profile.put("averageTextLength", Integer.valueOf(averageTextLength));
            profile.put("maxTextLength", Integer.valueOf(maxTextLength));
            profile.put("highUnique", Boolean.valueOf(highUnique));
            profile.put("enumLike", Boolean.valueOf(enumLike));
            profile.put("numericLike", Boolean.valueOf(numericLike));
            profile.put("dateLike", Boolean.valueOf(dateLike));
            profile.put("longTextLike", Boolean.valueOf(longTextLike));
            profile.put("suspectedPrimaryKey", Boolean.valueOf(suspectedPrimaryKey));
            profile.put("aggregatable", Boolean.valueOf(aggregatable));
            profile.put("profileTypes", profileTypes(
                    highUnique,
                    enumLike,
                    numericLike,
                    dateLike,
                    longTextLike,
                    suspectedPrimaryKey,
                    aggregatable
            ));
            return profile;
        }

        /**
         * 判断是否为枚举样式列。
         *
         * @param distinctCount 去重值数量
         * @param distinctRatio 去重率
         * @return 是否枚举样式
         */
        private boolean isEnumLike(int distinctCount, double distinctRatio) {
            if (nonBlankCount < 2 || distinctCount <= 0) {
                return false;
            }
            int dynamicDistinctMax = Math.max(2, nonBlankCount / 2);
            int allowedDistinctMax = Math.min(ENUM_DISTINCT_MAX, dynamicDistinctMax);
            return distinctCount <= allowedDistinctMax && distinctRatio <= 0.5D;
        }

        /**
         * 计算主导值类型。
         *
         * @return 主导值类型
         */
        private String dominantValueType() {
            Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
            counts.put("number", Integer.valueOf(numericCount));
            counts.put("date", Integer.valueOf(dateCount));
            counts.put("boolean", Integer.valueOf(booleanCount));
            counts.put("text", Integer.valueOf(textCount));
            String dominantType = "blank";
            int dominantCount = 0;
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                if (entry.getValue().intValue() > dominantCount) {
                    dominantType = entry.getKey();
                    dominantCount = entry.getValue().intValue();
                }
            }
            return dominantType;
        }

        /**
         * 组装画像类型标签。
         *
         * @param highUnique 是否高唯一度
         * @param enumLike 是否枚举
         * @param numericLike 是否数值
         * @param dateLike 是否日期
         * @param longTextLike 是否长文本
         * @param suspectedPrimaryKey 是否疑似主键
         * @param aggregatable 是否可聚合
         * @return 画像标签列表
         */
        private List<String> profileTypes(
                boolean highUnique,
                boolean enumLike,
                boolean numericLike,
                boolean dateLike,
                boolean longTextLike,
                boolean suspectedPrimaryKey,
                boolean aggregatable
        ) {
            List<String> profileTypes = new ArrayList<String>();
            if (highUnique) {
                profileTypes.add("HIGH_UNIQUE");
            }
            if (enumLike) {
                profileTypes.add("ENUM");
            }
            if (numericLike) {
                profileTypes.add("NUMERIC");
            }
            if (dateLike) {
                profileTypes.add("DATE");
            }
            if (longTextLike) {
                profileTypes.add("LONG_TEXT");
            }
            if (suspectedPrimaryKey) {
                profileTypes.add("SUSPECTED_PRIMARY_KEY");
            }
            if (aggregatable) {
                profileTypes.add("AGGREGATABLE");
            }
            return profileTypes;
        }
    }
}
