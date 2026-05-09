package com.xbk.lattice.query.service;

import com.xbk.lattice.shared.json.JsonMappers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 答案 Markdown 证据归一化器
 *
 * 职责：将 fallback 证据行、Markdown 表格行与结构化 JSON 行归一为可展示事实句
 *
 * 不属于本类的事：不选择证据、不计算问题相关性、不补 citation
 *
 * @author xiexu
 */
final class AnswerMarkdownEvidenceNormalizer {

    private static final ObjectMapper OBJECT_MAPPER = JsonMappers.defaultMapper();

    /**
     * 判断候选句是否明显来自目录、页码或页分隔符。
     *
     * @param normalizedLine 归一化候选句
     * @return 目录行返回 true
     */
    boolean looksLikeTableOfContentsLine(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String trimmedLine = normalizedLine.trim();
        String lowerCaseLine = lowerCase(trimmedLine);
        if ("目录".equals(trimmedLine) || lowerCaseLine.startsWith("=== page:")) {
            return true;
        }
        return trimmedLine.length() <= 120
                && (trimmedLine.matches(".*[？?].*\\s+\\d+$") || trimmedLine.matches(".*\\t\\d+$"));
    }

    /**
     * 判断一行是否为纯媒体嵌入，而不是可供问答引用的自然语言内容。
     *
     * @param normalizedLine 归一化后的单行文本
     * @return 纯媒体行返回 true
     */
    boolean isNonTextMediaLine(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String trimmedLine = normalizedLine.trim();
        String lowerCaseLine = trimmedLine.toLowerCase(Locale.ROOT);
        return trimmedLine.matches("^!\\[[^\\]]*]\\([^)]*\\)$")
                || lowerCaseLine.matches("^<img\\b[^>]*>$");
    }

    /**
     * 判断当前行是否为 Markdown 表头，且下一行为分隔线。
     *
     * @param currentLine 当前行
     * @param nextLine 下一行
     * @return 命中表头返回 true
     */
    boolean isMarkdownTableHeaderWithDivider(String currentLine, String nextLine) {
        if (currentLine == null || nextLine == null) {
            return false;
        }
        String normalizedCurrentLine = currentLine.trim();
        String normalizedNextLine = nextLine.trim();
        if (!(normalizedCurrentLine.startsWith("|") && normalizedCurrentLine.endsWith("|"))) {
            return false;
        }
        if (!(normalizedNextLine.startsWith("|") && normalizedNextLine.endsWith("|"))) {
            return false;
        }
        return isMarkdownTableDividerRow(parseMarkdownTableCells(normalizedNextLine));
    }

    /**
     * 过滤 fallback 片段中的元数据行，优先保留真正的事实内容。
     *
     * @param matchedLines 原始命中行
     * @return 过滤后的命中行
     */
    List<String> filterFallbackMatchedLines(List<String> matchedLines) {
        List<String> filteredLines = new ArrayList<String>();
        if (matchedLines == null) {
            return filteredLines;
        }
        for (String matchedLine : matchedLines) {
            String normalizedLine = normalizeFallbackLineCandidate(matchedLine);
            if (normalizedLine.isEmpty()) {
                continue;
            }
            filteredLines.add(normalizedLine);
        }
        return filteredLines;
    }

    /**
     * 归一化 fallback 片段候选行，保留 summary/content 等真正有信息的字段值。
     *
     * @param candidateLine 原始候选行
     * @return 归一化后的可展示文本；无法展示时返回空串
     */
    String normalizeFallbackLineCandidate(String candidateLine) {
        String normalizedLine = candidateLine == null ? "" : candidateLine.trim();
        normalizedLine = stripStructuredLinePrefix(normalizedLine);
        String lowerCaseLine = normalizedLine.toLowerCase(Locale.ROOT);
        if (normalizedLine.isEmpty() || "---".equals(normalizedLine)) {
            return "";
        }
        if (isNonTextMediaLine(normalizedLine)) {
            return "";
        }
        if (looksLikeTableOfContentsLine(normalizedLine)) {
            return "";
        }
        if (normalizedLine.startsWith("|") && normalizedLine.endsWith("|")) {
            return SensitiveTextMasker.mask(normalizeMarkdownTableRow(normalizedLine));
        }
        String structuredJsonLine = normalizeStructuredJsonLine(normalizedLine);
        if (!structuredJsonLine.isBlank()) {
            return SensitiveTextMasker.mask(structuredJsonLine);
        }
        if (lowerCaseLine.startsWith("summary:")
                || lowerCaseLine.startsWith("description:")
                || lowerCaseLine.startsWith("content:")) {
            return SensitiveTextMasker.mask(extractFallbackFieldValue(normalizedLine));
        }
        if (lowerCaseLine.startsWith("<h1")
                || lowerCaseLine.startsWith("<h2")
                || lowerCaseLine.startsWith("<h3")
                || lowerCaseLine.startsWith("<h4")) {
            return "";
        }
        if (lowerCaseLine.startsWith("title:")
                || lowerCaseLine.startsWith("referential_keywords:")
                || lowerCaseLine.startsWith("sources:")
                || lowerCaseLine.startsWith("source_paths:")
                || lowerCaseLine.startsWith("article_key:")
                || lowerCaseLine.startsWith("concept_id:")
                || lowerCaseLine.startsWith("file_path:")
                || lowerCaseLine.startsWith("metadata:")
                || lowerCaseLine.startsWith("depends_on:")
                || lowerCaseLine.startsWith("related:")
                || lowerCaseLine.startsWith("confidence:")
                || lowerCaseLine.startsWith("compiled_at:")
                || lowerCaseLine.startsWith("review_status:")
                || lowerCaseLine.startsWith("lifecycle:")) {
            return "";
        }
        return SensitiveTextMasker.mask(normalizedLine);
    }

    /**
     * 归一 Markdown 表格行，尽量还原为可直接展示的事实句。
     *
     * @param tableRow 表格行
     * @return 归一后的事实句
     */
    String normalizeMarkdownTableRow(String tableRow) {
        List<String> cells = parseMarkdownTableCells(tableRow);
        if (cells.isEmpty() || isMarkdownTableDividerRow(cells)) {
            return "";
        }
        List<String> normalizedCells = new ArrayList<String>();
        for (String cell : cells) {
            String normalizedCell = stripTableCellMarkup(cell);
            if (normalizedCell.isBlank()) {
                continue;
            }
            normalizedCells.add(normalizedCell);
        }
        if (normalizedCells.isEmpty()) {
            return "";
        }
        if (normalizedCells.size() >= 3 && normalizedCells.get(0).matches("\\d+")) {
            normalizedCells.remove(0);
        }
        if (!normalizedCells.isEmpty() && normalizedCells.get(0).contains("=")) {
            String assignmentSentence = normalizeAssignmentCell(normalizedCells.get(0));
            if (normalizedCells.size() >= 2) {
                return assignmentSentence + "，" + normalizedCells.get(1);
            }
            return assignmentSentence;
        }
        if (normalizedCells.size() >= 2 && looksLikeConfigFactKey(normalizedCells.get(0))) {
            String baseSentence = normalizedCells.get(0) + " = " + normalizedCells.get(1);
            if (normalizedCells.size() >= 3) {
                return baseSentence + "，" + normalizedCells.get(2);
            }
            return baseSentence;
        }
        if (looksLikeLabelValueTableRow(normalizedCells)) {
            String baseSentence = normalizedCells.get(0) + " = " + normalizedCells.get(1);
            if (normalizedCells.size() >= 3) {
                return baseSentence + "，" + normalizedCells.get(2);
            }
            return baseSentence;
        }
        return String.join("；", normalizedCells);
    }

    /**
     * 从结构化 JSON 中提取可读的字符串值，作为通用证据候选。
     *
     * @param content 原始内容
     * @return 可读值列表
     */
    List<String> selectStructuredJsonValueLines(String content) {
        List<String> valueLines = new ArrayList<String>();
        if (content == null || content.isBlank()) {
            return valueLines;
        }
        String trimmedContent = content.trim();
        if (!(trimmedContent.startsWith("{") || trimmedContent.startsWith("["))) {
            return valueLines;
        }
        JsonNode jsonNode = readJsonNode(trimmedContent);
        if (jsonNode == null) {
            return valueLines;
        }
        collectStructuredJsonValueLines(jsonNode, valueLines);
        return valueLines;
    }

    /**
     * 判断候选句是否以“配置键/指标名 = 值”的直接事实形式开头。
     *
     * @param normalizedLine 归一化后的候选句
     * @return 直接事实句返回 true
     */
    boolean startsWithDirectStructuredFactAssignment(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        int assignmentDelimiterIndex = structuredAssignmentDelimiterIndex(normalizedLine);
        if (assignmentDelimiterIndex <= 0) {
            return false;
        }
        String assignmentKey = normalizedLine.substring(0, assignmentDelimiterIndex).trim();
        if (assignmentKey.isBlank()) {
            return false;
        }
        return looksLikeConfigFactKey(assignmentKey);
    }

    /**
     * 查找结构化赋值分隔符位置。
     *
     * @param normalizedLine 归一化候选句
     * @return 分隔符位置；不存在返回 -1
     */
    int structuredAssignmentDelimiterIndex(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return -1;
        }
        int equalsIndex = normalizedLine.indexOf(" = ");
        int colonIndex = normalizedLine.indexOf(": ");
        int chineseColonIndex = normalizedLine.indexOf("：");
        if (colonIndex < 0) {
            colonIndex = chineseColonIndex;
        }
        else if (chineseColonIndex >= 0) {
            colonIndex = Math.min(colonIndex, chineseColonIndex);
        }
        if (equalsIndex < 0) {
            return colonIndex;
        }
        if (colonIndex < 0) {
            return equalsIndex;
        }
        return Math.min(equalsIndex, colonIndex);
    }

    /**
     * 判断文本是否更像配置键或阈值字段名。
     *
     * @param cell 单元格
     * @return 配置键返回 true
     */
    boolean looksLikeConfigFactKey(String cell) {
        String normalizedCell = lowerCase(cell);
        return normalizedCell.contains(".")
                || normalizedCell.contains("_")
                || normalizedCell.contains("threshold")
                || normalizedCell.contains("window")
                || normalizedCell.contains("timeout")
                || normalizedCell.contains("retry")
                || normalizedCell.matches(".*[A-Za-z][A-Za-z0-9._-]{2,}.*");
    }

    /**
     * 判断表格单元格是否像可直接回答的值。
     *
     * @param cell 单元格
     * @return 值单元格返回 true
     */
    boolean looksLikeScalarTableValue(String cell) {
        if (cell == null || cell.isBlank()) {
            return false;
        }
        String normalizedCell = lowerCase(cell);
        return normalizedCell.matches(".*\\d.*")
                || normalizedCell.contains("/")
                || normalizedCell.contains("_")
                || normalizedCell.contains("-")
                || normalizedCell.contains("@")
                || normalizedCell.contains("=")
                || normalizedCell.contains("是")
                || normalizedCell.contains("否")
                || normalizedCell.length() <= 12;
    }

    /**
     * 去掉结构化抽取残留前缀，避免把内部标记直接暴露给用户。
     *
     * @param candidateLine 候选行
     * @return 去前缀后的文本
     */
    private String stripStructuredLinePrefix(String candidateLine) {
        if (candidateLine == null || candidateLine.isBlank()) {
            return "";
        }
        return candidateLine
                .replaceFirst("^(?i)table_row:\\s*", "")
                .replaceFirst("^(?i)sheet=\\S+;\\s*", "")
                .replaceFirst("^(?i)row=\\d+;\\s*", "")
                .trim();
    }

    /**
     * 将 JSON 行归一成可读事实句，避免 fallback 直接展示内部结构。
     *
     * @param normalizedLine 候选行
     * @return 可读事实句；非 JSON 返回空串
     */
    private String normalizeStructuredJsonLine(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return "";
        }
        String trimmedLine = normalizedLine.trim();
        if (!(trimmedLine.startsWith("{") || trimmedLine.startsWith("["))) {
            return "";
        }
        List<String> valueLines = selectStructuredJsonValueLines(trimmedLine);
        if (valueLines.isEmpty()) {
            return "";
        }
        return String.join("；", valueLines.subList(0, Math.min(4, valueLines.size())));
    }

    /**
     * 把文本解析为 JSON 节点。
     *
     * @param content 文本内容
     * @return JSON 节点
     */
    private JsonNode readJsonNode(String content) {
        try {
            return OBJECT_MAPPER.readTree(content);
        }
        catch (Exception ex) {
            return null;
        }
    }

    /**
     * 递归收集 JSON 字符串叶子节点。
     *
     * @param jsonNode JSON 节点
     * @param valueLines 输出值
     */
    private void collectStructuredJsonValueLines(JsonNode jsonNode, List<String> valueLines) {
        if (jsonNode == null || valueLines.size() >= 24) {
            return;
        }
        if (jsonNode.isTextual()) {
            String textValue = normalizeFallbackLineCandidate(jsonNode.asText());
            if (!textValue.isBlank()) {
                addDistinctStructuredJsonValue(valueLines, textValue);
            }
            return;
        }
        if (jsonNode.isNumber() || jsonNode.isBoolean()) {
            addDistinctStructuredJsonValue(valueLines, jsonNode.asText());
            return;
        }
        if (jsonNode.isObject()) {
            jsonNode.fields().forEachRemaining(entry -> collectStructuredJsonValueLines(entry.getValue(), valueLines));
            return;
        }
        if (jsonNode.isArray()) {
            for (JsonNode childNode : jsonNode) {
                collectStructuredJsonValueLines(childNode, valueLines);
                if (valueLines.size() >= 24) {
                    break;
                }
            }
        }
    }

    /**
     * 去重追加 JSON 值候选。
     *
     * @param valueLines 已收集值
     * @param textValue 候选值
     */
    private void addDistinctStructuredJsonValue(List<String> valueLines, String textValue) {
        String normalizedValue = textValue == null ? "" : textValue.trim();
        if (normalizedValue.isBlank() || normalizedValue.length() > 260) {
            return;
        }
        if (!valueLines.contains(normalizedValue)) {
            valueLines.add(normalizedValue);
        }
    }

    /**
     * 判断表格数据行是否像“标签列 + 值列”的结构化事实。
     *
     * @param normalizedCells 归一化单元格
     * @return 标签值行返回 true
     */
    private boolean looksLikeLabelValueTableRow(List<String> normalizedCells) {
        if (normalizedCells == null || normalizedCells.size() < 2) {
            return false;
        }
        String labelCell = normalizedCells.get(0);
        String valueCell = normalizedCells.get(1);
        if (labelCell == null || labelCell.isBlank() || valueCell == null || valueCell.isBlank()) {
            return false;
        }
        if (labelCell.length() > 40 || valueCell.length() > 80) {
            return false;
        }
        if (isMarkdownTableHeaderCell(labelCell) && isMarkdownTableHeaderCell(valueCell)) {
            return false;
        }
        return looksLikeScalarTableValue(valueCell)
                || (normalizedCells.size() >= 3 && looksLikeScalarTableValue(normalizedCells.get(2)));
    }

    /**
     * 解析 Markdown 表格单元格。
     *
     * @param tableRow 表格行
     * @return 单元格列表
     */
    private List<String> parseMarkdownTableCells(String tableRow) {
        List<String> cells = new ArrayList<String>();
        if (tableRow == null || tableRow.isBlank()) {
            return cells;
        }
        String[] rawCells = tableRow.split("\\|");
        for (String rawCell : rawCells) {
            String normalizedCell = rawCell == null ? "" : rawCell.trim();
            if (normalizedCell.isEmpty()) {
                continue;
            }
            cells.add(normalizedCell);
        }
        return cells;
    }

    /**
     * 判断表格行是否为分隔线。
     *
     * @param cells 表格单元格
     * @return 分隔线返回 true
     */
    private boolean isMarkdownTableDividerRow(List<String> cells) {
        for (String cell : cells) {
            if (!cell.matches(":?-{2,}:?")) {
                return false;
            }
        }
        return !cells.isEmpty();
    }

    /**
     * 判断表格单元格是否更像表头标签。
     *
     * @param cell 单元格
     * @return 表头标签返回 true
     */
    private boolean isMarkdownTableHeaderCell(String cell) {
        return "序号".equals(cell)
                || "检查项".equals(cell)
                || "说明".equals(cell)
                || "配置键".equals(cell)
                || "精确值".equals(cell)
                || "项目".equals(cell)
                || "类型".equals(cell)
                || "标识符".equals(cell)
                || "建议值".equals(cell)
                || "是否自动".equals(cell)
                || "典型形态".equals(cell)
                || "示例".equals(cell)
                || "方法".equals(cell)
                || "返回值".equals(cell)
                || "类别".equals(cell)
                || "含义".equals(cell)
                || "来源说明".equals(cell)
                || "优先检查项".equals(cell)
                || "触发信号".equals(cell);
    }

    /**
     * 去掉表格单元格内的 Markdown 修饰。
     *
     * @param cell 原始单元格
     * @return 归一后的单元格
     */
    private String stripTableCellMarkup(String cell) {
        String normalizedCell = cell == null ? "" : cell.trim();
        normalizedCell = normalizedCell.replace("**", "");
        normalizedCell = normalizedCell.replace("`", "");
        return normalizedCell.trim();
    }

    /**
     * 归一化已写成 key=value 形式的配置单元格，提升展示一致性。
     *
     * @param assignmentCell 配置单元格
     * @return 归一化后的配置表达式
     */
    private String normalizeAssignmentCell(String assignmentCell) {
        String normalizedCell = stripTableCellMarkup(assignmentCell);
        return normalizedCell.replaceAll("\\s*=\\s*", " = ");
    }

    /**
     * 提取结构化元数据行中的字段值。
     *
     * @param line 原始行
     * @return 字段值
     */
    private String extractFallbackFieldValue(String line) {
        if (line == null || line.isBlank()) {
            return "";
        }
        int colonIndex = line.indexOf(':');
        if (colonIndex < 0 || colonIndex >= line.length() - 1) {
            return line.trim();
        }
        String fieldValue = line.substring(colonIndex + 1).trim();
        fieldValue = fieldValue.replaceAll("^[\"']+", "");
        fieldValue = fieldValue.replaceAll("[\"']+$", "");
        return fieldValue.trim();
    }

    /**
     * 把文本转成小写字符串，便于 fallback 相关性判断。
     *
     * @param value 原始文本
     * @return 小写文本
     */
    private String lowerCase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
