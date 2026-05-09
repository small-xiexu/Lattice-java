package com.xbk.lattice.query.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 表格字段定义 fallback 结论构建器
 *
 * 职责：从 Excel / Markdown 表格证据中抽取字段定义、编码对照与指定字段含义结论
 *
 * 不属于本类的事：不选择 fallback 证据、不处理普通枚举/流程/状态结论、不负责 citation 细则
 *
 * @author xiexu
 */
final class AnswerSpreadsheetFieldDefinitionConclusionBuilder {

    private final AnswerGenerationService support;

    private final AnswerMarkdownEvidenceNormalizer evidenceNormalizer = new AnswerMarkdownEvidenceNormalizer();

    /**
     * 创建表格字段定义 fallback 结论构建器。
     *
     * @param support 答案生成支撑逻辑
     */
    AnswerSpreadsheetFieldDefinitionConclusionBuilder(AnswerGenerationService support) {
        this.support = support;
    }

    /**
     * 为 Excel/Markdown 表格编译出来的“报文字段定义”文章构造稳定 fallback。
     *
     * @param question 用户问题
     * @param primaryHit 首要证据
     * @return 字段定义结论
     */
    List<String> buildSpreadsheetFieldDefinitionConclusionLines(String question, QueryArticleHit primaryHit) {
        if (!looksLikeSpreadsheetFieldDefinitionQuestion(question, primaryHit)) {
            return List.of();
        }
        String citationLiteral = support.joinConclusionCitations(List.of(primaryHit));
        String content = primaryHit.getContent();
        List<FieldDefinitionTableSummary> tableSummaries = extractFieldDefinitionTableSummaries(content);
        if (tableSummaries.isEmpty()) {
            return List.of();
        }
        List<String> codeMappings = extractCodeMappings(content);
        List<String> conclusionLines = new ArrayList<String>();
        List<String> datasetSignals = new ArrayList<String>();
        for (FieldDefinitionTableSummary tableSummary : tableSummaries) {
            datasetSignals.add(tableSummary.getDisplayName());
        }
        if (!codeMappings.isEmpty()) {
            datasetSignals.add("code mappings");
        }
        if (!datasetSignals.isEmpty()) {
            conclusionLines.add("Structured field definitions: "
                    + String.join(", ", datasetSignals)
                    + ". "
                    + citationLiteral);
        }
        for (FieldDefinitionTableSummary tableSummary : tableSummaries) {
            conclusionLines.add(tableSummary.getDisplayName()
                    + "has "
                    + tableSummary.getFieldDefinitions().size()
                    + " fields: "
                    + String.join("; ", tableSummary.getFieldDefinitions())
                    + ". "
                    + citationLiteral);
        }
        if (!codeMappings.isEmpty()) {
            conclusionLines.add("Code mappings: "
                    + String.join("; ", codeMappings)
                    + ". "
                    + citationLiteral);
        }
        return conclusionLines;
    }

    /**
     * 为“指定字段/配置/枚举分别是什么”这类精确标识题构造通用 fallback。
     *
     * @param question 用户问题
     * @param fallbackHits fallback 证据
     * @return 字段含义结论
     */
    List<String> buildFocusedSpreadsheetFieldDefinitionConclusionLines(
            String question,
            List<QueryArticleHit> fallbackHits
    ) {
        if (!looksLikeFocusedReferentialDefinitionQuestion(question) || fallbackHits == null || fallbackHits.isEmpty()) {
            return List.of();
        }
        List<String> requestedIdentifiers = support.extractRequestedReferentialIdentifiers(question);
        if (requestedIdentifiers.isEmpty()) {
            return List.of();
        }
        List<String> conclusionLines = new ArrayList<String>();
        for (String requestedIdentifier : requestedIdentifiers) {
            FieldDefinitionMatch definitionMatch = findFieldDefinitionMatch(fallbackHits, requestedIdentifier);
            if (definitionMatch == null) {
                continue;
            }
            conclusionLines.add(definitionMatch.getDefinitionLine()
                    + " "
                    + support.joinConclusionCitations(List.of(definitionMatch.getQueryArticleHit())));
        }
        return conclusionLines;
    }

    /**
     * 判断是否为显式点名多个标识并询问定义/含义的题目。
     *
     * @param question 用户问题
     * @return 聚焦精确标识定义题返回 true
     */
    private boolean looksLikeFocusedReferentialDefinitionQuestion(String question) {
        if (!looksLikeReferentialKnowledgeQuestion(question)) {
            return false;
        }
        List<String> requestedIdentifiers = support.extractRequestedReferentialIdentifiers(question);
        if (requestedIdentifiers.size() < 2) {
            return false;
        }
        return true;
    }

    /**
     * 判断问题是否属于字段名、状态码、枚举值、配置键等精确标识知识题。
     *
     * @param question 用户问题
     * @return 精确标识知识题返回 true
     */
    private boolean looksLikeReferentialKnowledgeQuestion(String question) {
        List<String> requestedIdentifiers = support.extractRequestedReferentialIdentifiers(question);
        if (requestedIdentifiers.isEmpty()) {
            return false;
        }
        return true;
    }

    /**
     * 在候选证据中查找某个标识的最佳字段定义行。
     *
     * @param fallbackHits fallback 证据
     * @param identifier 标识
     * @return 字段定义匹配；没有则返回 null
     */
    private FieldDefinitionMatch findFieldDefinitionMatch(List<QueryArticleHit> fallbackHits, String identifier) {
        for (QueryArticleHit fallbackHit : fallbackHits) {
            String definitionLine = buildFieldDefinitionLine(fallbackHit.getContent(), identifier);
            if (!definitionLine.isBlank()) {
                return new FieldDefinitionMatch(fallbackHit, definitionLine);
            }
        }
        return null;
    }

    /**
     * 从证据正文中构造某个标识的定义行。
     *
     * @param content 证据正文
     * @param identifier 标识
     * @return 定义行；证据不足时返回空串
     */
    private String buildFieldDefinitionLine(String content, String identifier) {
        if (content == null || content.isBlank() || identifier == null || identifier.isBlank()) {
            return "";
        }
        for (String rawLine : content.split("\\R")) {
            if (!lineContainsIdentifier(rawLine, identifier)) {
                continue;
            }
            String definitionLine = buildFieldDefinitionLineFromRawLine(rawLine, identifier);
            if (!definitionLine.isBlank()) {
                return definitionLine;
            }
        }
        return "";
    }

    /**
     * 判断一行文本是否包含指定精确标识。
     *
     * @param rawLine 原始行
     * @param identifier 标识
     * @return 包含返回 true
     */
    private boolean lineContainsIdentifier(String rawLine, String identifier) {
        if (rawLine == null || rawLine.isBlank() || identifier == null || identifier.isBlank()) {
            return false;
        }
        String normalizedLine = lowerCase(rawLine);
        String normalizedIdentifier = lowerCase(identifier);
        return normalizedLine.contains(normalizedIdentifier);
    }

    /**
     * 从 Markdown 表格行、CSV/TSV 行或普通文本行中抽取字段定义。
     *
     * @param rawLine 原始证据行
     * @param identifier 标识
     * @return 定义行；无法抽取时返回空串
     */
    private String buildFieldDefinitionLineFromRawLine(String rawLine, String identifier) {
        List<String> cells = splitStructuredDefinitionRow(rawLine);
        if (cells.size() >= 2) {
            String definitionLine = buildFieldDefinitionLineFromCells(cells, identifier);
            if (!definitionLine.isBlank()) {
                return definitionLine;
            }
        }
        String normalizedLine = evidenceNormalizer.normalizeFallbackLineCandidate(rawLine);
        if (normalizedLine.isBlank() || looksLikeHeadingOnlyFallbackLine(rawLine)) {
            return "";
        }
        return "`" + identifier + "`: " + support.trimTrailingFallbackPunctuation(normalizedLine);
    }

    /**
     * 切分结构化字段定义行，兼容 Markdown 表格、CSV 与 TSV。
     *
     * @param rawLine 原始行
     * @return 单元格
     */
    private List<String> splitStructuredDefinitionRow(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) {
            return List.of();
        }
        List<String> markdownCells = splitMarkdownTableRow(rawLine);
        if (!markdownCells.isEmpty()) {
            return markdownCells;
        }
        if (rawLine.contains("\t")) {
            return splitDelimitedDefinitionRow(rawLine, '\t');
        }
        if (rawLine.contains(",")) {
            return splitDelimitedDefinitionRow(rawLine, ',');
        }
        return List.of();
    }

    /**
     * 按分隔符切分单行字段定义，保留表格抽取后的空单元格位置。
     *
     * @param rawLine 原始行
     * @param delimiter 分隔符
     * @return 单元格
     */
    private List<String> splitDelimitedDefinitionRow(String rawLine, char delimiter) {
        List<String> cells = new ArrayList<String>();
        StringBuilder cellBuilder = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < rawLine.length(); index++) {
            char currentChar = rawLine.charAt(index);
            if (currentChar == '"') {
                quoted = !quoted;
                cellBuilder.append(currentChar);
                continue;
            }
            if (currentChar == delimiter && !quoted) {
                cells.add(cleanupMarkdownTableCell(cellBuilder.toString()));
                cellBuilder.setLength(0);
                continue;
            }
            cellBuilder.append(currentChar);
        }
        cells.add(cleanupMarkdownTableCell(cellBuilder.toString()));
        return cells;
    }

    /**
     * 基于结构化单元格构造字段定义。
     *
     * @param cells 单元格
     * @param identifier 标识
     * @return 定义行；无法抽取时返回空串
     */
    private String buildFieldDefinitionLineFromCells(List<String> cells, String identifier) {
        int identifierIndex = indexOfCellIdentifier(cells, identifier);
        if (identifierIndex < 0) {
            return "";
        }
        List<String> nonBlankTailCells = collectNonBlankTailCells(cells, identifierIndex + 1);
        if (nonBlankTailCells.isEmpty()) {
            return "";
        }
        String type = selectTypeCell(nonBlankTailCells);
        String length = selectLengthCell(nonBlankTailCells, type);
        String description = selectDescriptionCell(nonBlankTailCells, type, length);
        String enumValue = selectEnumCell(nonBlankTailCells, description);
        List<String> parts = new ArrayList<String>();
        if (!type.isBlank()) {
            parts.add("type `" + type + "`");
        }
        if (!length.isBlank()) {
            parts.add("length `" + length + "`");
        }
        if (!description.isBlank()) {
            parts.add(description);
        }
        if (!enumValue.isBlank()) {
            parts.add("enum/value: " + enumValue);
        }
        if (parts.isEmpty()) {
            return "";
        }
        return "`" + identifier + "`: " + String.join("; ", parts) + ".";
    }

    /**
     * 查找单元格中精确匹配标识的位置。
     *
     * @param cells 单元格
     * @param identifier 标识
     * @return 下标；未找到返回 -1
     */
    private int indexOfCellIdentifier(List<String> cells, String identifier) {
        String normalizedIdentifier = lowerCase(identifier);
        for (int index = 0; index < cells.size(); index++) {
            String normalizedCell = lowerCase(cleanupMarkdownTableCell(cells.get(index)));
            if (normalizedCell.equals(normalizedIdentifier)) {
                return index;
            }
        }
        for (int index = 0; index < cells.size(); index++) {
            String normalizedCell = lowerCase(cleanupMarkdownTableCell(cells.get(index)));
            if (normalizedCell.contains(normalizedIdentifier)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * 收集字段名之后的有效单元格。
     *
     * @param cells 原始单元格
     * @param startIndex 起始下标
     * @return 非空尾部单元格
     */
    private List<String> collectNonBlankTailCells(List<String> cells, int startIndex) {
        List<String> tailCells = new ArrayList<String>();
        for (int index = Math.max(0, startIndex); index < cells.size(); index++) {
            String cell = cleanupMarkdownTableCell(cells.get(index));
            if (cell.isBlank() || looksLikeSpreadsheetUsageFlag(cell)) {
                continue;
            }
            tailCells.add(cell);
        }
        return tailCells;
    }

    /**
     * 选择类型单元格。
     *
     * @param tailCells 字段名后的单元格
     * @return 类型
     */
    private String selectTypeCell(List<String> tailCells) {
        if (tailCells.isEmpty()) {
            return "";
        }
        String firstCell = tailCells.get(0);
        if (!containsHanText(firstCell) && firstCell.length() <= 24) {
            return firstCell;
        }
        return "";
    }

    /**
     * 选择长度单元格。
     *
     * @param tailCells 字段名后的单元格
     * @param type 已选类型
     * @return 长度
     */
    private String selectLengthCell(List<String> tailCells, String type) {
        int startIndex = type.isBlank() ? 0 : 1;
        for (int index = startIndex; index < tailCells.size(); index++) {
            String cell = tailCells.get(index);
            if (looksLikeFieldLengthCell(cell)) {
                return cell;
            }
            if (containsHanText(cell)) {
                return "";
            }
        }
        return "";
    }

    /**
     * 选择说明单元格。
     *
     * @param tailCells 字段名后的单元格
     * @param type 已选类型
     * @param length 已选长度
     * @return 说明
     */
    private String selectDescriptionCell(List<String> tailCells, String type, String length) {
        for (String cell : tailCells) {
            if (cell.equals(type)
                    || cell.equals(length)
                    || looksLikeFieldLengthCell(cell)
                    || looksLikeSpreadsheetExampleValueCell(cell)) {
                continue;
            }
            if (containsHanText(cell) && !looksLikeEnumValueCell(cell)) {
                return trimLongDefinitionCell(cell);
            }
        }
        for (String cell : tailCells) {
            if (!cell.equals(type)
                    && !cell.equals(length)
                    && !looksLikeEnumValueCell(cell)
                    && !looksLikeSpreadsheetExampleValueCell(cell)) {
                return trimLongDefinitionCell(cell);
            }
        }
        return "";
    }

    /**
     * 选择枚举/取值单元格。
     *
     * @param tailCells 字段名后的单元格
     * @param description 已选说明
     * @return 枚举/取值
     */
    private String selectEnumCell(List<String> tailCells, String description) {
        for (String cell : tailCells) {
            if (cell.equals(description)) {
                continue;
            }
            if (looksLikeEnumValueCell(cell)) {
                return trimLongDefinitionCell(cell);
            }
        }
        return "";
    }

    /**
     * 判断单元格是否为字段长度。
     *
     * @param cell 单元格
     * @return 字段长度返回 true
     */
    private boolean looksLikeFieldLengthCell(String cell) {
        if (cell == null || cell.isBlank() || containsHanText(cell)) {
            return false;
        }
        return cell.length() <= 16 && cell.matches("[A-Za-z0-9_./ -]+");
    }

    /**
     * 判断单元格是否像枚举值。
     *
     * @param cell 单元格
     * @return 枚举值返回 true
     */
    private boolean looksLikeEnumValueCell(String cell) {
        if (cell == null || cell.isBlank()) {
            return false;
        }
        return cell.matches(".*\\d{2,}.*[\\p{IsHan}A-Za-z].*")
                && (cell.contains(" ")
                || countOccurrences(cell, "；") > 0
                || countOccurrences(cell, "、") > 0
                || cell.matches(".*\\d{2}[^\\d].*\\d{2}.*"));
    }

    /**
     * 判断单元格是否只是“是否使用”等布尔标记。
     *
     * @param cell 单元格
     * @return 用法标记返回 true
     */
    private boolean looksLikeSpreadsheetUsageFlag(String cell) {
        String normalizedCell = lowerCase(cell);
        return normalizedCell.equals("y")
                || normalizedCell.equals("n")
                || normalizedCell.equals("yes")
                || normalizedCell.equals("no")
                || normalizedCell.equals("true")
                || normalizedCell.equals("false");
    }

    /**
     * 判断单元格是否只是示例值，而不是字段说明。
     *
     * @param cell 单元格
     * @return 示例值返回 true
     */
    private boolean looksLikeSpreadsheetExampleValueCell(String cell) {
        if (cell == null || cell.isBlank()) {
            return false;
        }
        String normalizedCell = cell.replace("\"", "").trim();
        return normalizedCell.matches("\\d+(?:\\.\\d+)?")
                || (normalizedCell.length() <= 8
                && !containsHanText(normalizedCell)
                && normalizedCell.matches("[A-Za-z0-9_-]+"));
    }

    /**
     * 限制过长定义单元格，避免把整段 JSON 或表格余量塞进答案。
     *
     * @param cell 单元格
     * @return 裁剪后的单元格
     */
    private String trimLongDefinitionCell(String cell) {
        if (cell == null || cell.length() <= 180) {
            return cell == null ? "" : cell;
        }
        return cell.substring(0, 180).stripTrailing() + "...";
    }

    /**
     * 判断问题是否在询问表格字段定义。
     *
     * @param question 用户问题
     * @param primaryHit 首要证据
     * @return 字段定义题返回 true
     */
    private boolean looksLikeSpreadsheetFieldDefinitionQuestion(String question, QueryArticleHit primaryHit) {
        if (question == null || question.isBlank() || primaryHit == null) {
            return false;
        }
        if (support.extractRequestedReferentialIdentifiers(question).isEmpty()) {
            return false;
        }
        return !extractFieldDefinitionTableSummaries(primaryHit.getContent()).isEmpty();
    }

    /**
     * 从 Markdown 表格中抽取字段定义表摘要。
     *
     * @param content 证据正文
     * @return 字段定义表摘要
     */
    private List<FieldDefinitionTableSummary> extractFieldDefinitionTableSummaries(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<FieldDefinitionTableSummary> tableSummaries = new ArrayList<FieldDefinitionTableSummary>();
        String currentHeading = "";
        List<String> currentRows = new ArrayList<String>();
        for (String rawLine : content.split("\\R")) {
            String normalizedLine = rawLine == null ? "" : rawLine.trim();
            if (normalizedLine.startsWith("#")) {
                addFieldDefinitionTableSummary(tableSummaries, currentHeading, currentRows);
                String headingCandidate = cleanupHeadingLine(normalizedLine);
                if (!isGenericFieldDefinitionSubheading(headingCandidate) || currentHeading.isBlank()) {
                    currentHeading = headingCandidate;
                }
                currentRows = new ArrayList<String>();
                continue;
            }
            if (normalizedLine.startsWith("|")) {
                currentRows.add(normalizedLine);
                continue;
            }
            if (!currentRows.isEmpty() && !normalizedLine.isBlank()) {
                addFieldDefinitionTableSummary(tableSummaries, currentHeading, currentRows);
                currentRows = new ArrayList<String>();
            }
        }
        addFieldDefinitionTableSummary(tableSummaries, currentHeading, currentRows);
        return tableSummaries;
    }

    /**
     * 添加字段定义表摘要。
     *
     * @param tableSummaries 表摘要列表
     * @param heading 表格附近标题
     * @param rawRows 原始表格行
     */
    private void addFieldDefinitionTableSummary(
            List<FieldDefinitionTableSummary> tableSummaries,
            String heading,
            List<String> rawRows
    ) {
        List<String> fieldDefinitions = extractFieldDefinitionRows(rawRows);
        if (fieldDefinitions.isEmpty()) {
            return;
        }
        String displayName = resolveFieldDefinitionTableName(heading, tableSummaries.size() + 1);
        tableSummaries.add(new FieldDefinitionTableSummary(displayName, fieldDefinitions));
    }

    /**
     * 从表格行抽取字段定义行。
     *
     * @param rawRows 原始表格行
     * @return 字段定义行
     */
    private List<String> extractFieldDefinitionRows(List<String> rawRows) {
        if (rawRows == null || rawRows.isEmpty()) {
            return List.of();
        }
        List<String> fieldDefinitions = new ArrayList<String>();
        for (String rawLine : rawRows) {
            List<String> cells = splitMarkdownTableRow(rawLine);
            if (!looksLikeNumberedFieldDefinitionRow(cells)) {
                continue;
            }
            String fieldName = cleanupMarkdownTableCell(cells.get(1));
            String type = cleanupMarkdownTableCell(cells.get(2));
            String length = cleanupMarkdownTableCell(cells.get(3));
            String description = cleanupMarkdownTableCell(cells.get(4));
            if (fieldName.isBlank()) {
                continue;
            }
            fieldDefinitions.add("`"
                    + fieldName
                    + "`（"
                    + type
                    + "/"
                    + length
                    + "，"
                    + description
                    + "）");
        }
        return fieldDefinitions;
    }

    /**
     * 判断是否为编号字段定义表格行。
     *
     * @param cells 单元格
     * @return 是字段定义行返回 true
     */
    private boolean looksLikeNumberedFieldDefinitionRow(List<String> cells) {
        return cells != null
                && cells.size() >= 5
                && cleanupMarkdownTableCell(cells.get(0)).matches("\\d+")
                && !cleanupMarkdownTableCell(cells.get(1)).isBlank();
    }

    /**
     * 解析字段定义表显示名称。
     *
     * @param heading 表格附近标题
     * @param tableIndex 表格序号
     * @return 显示名称
     */
    private String resolveFieldDefinitionTableName(String heading, int tableIndex) {
        String normalizedHeading = cleanupHeadingLine(heading);
        List<String> identifiers = extractBacktickIdentifiers(normalizedHeading);
        if (!identifiers.isEmpty()) {
            return "field group `" + identifiers.get(0) + "` ";
        }
        Matcher latinIdentifierMatcher = Pattern.compile("([A-Za-z][A-Za-z0-9_]{2,})").matcher(normalizedHeading);
        if (latinIdentifierMatcher.find()) {
            return "field group `" + latinIdentifierMatcher.group(1) + "` ";
        }
        if (!normalizedHeading.isBlank()) {
            return "field group \"" + normalizedHeading + "\" ";
        }
        return "field group " + tableIndex + " ";
    }

    /**
     * 清理 Markdown 标题行。
     *
     * @param heading 标题行
     * @return 清理后的标题
     */
    private String cleanupHeadingLine(String heading) {
        if (heading == null || heading.isBlank()) {
            return "";
        }
        return heading.replaceFirst("^#+\\s*", "").trim();
    }

    /**
     * 提取反引号包裹的标识。
     *
     * @param value 原始文本
     * @return 标识列表
     */
    private List<String> extractBacktickIdentifiers(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        Matcher matcher = Pattern.compile("`([^`]+)`").matcher(value);
        List<String> identifiers = new ArrayList<String>();
        while (matcher.find()) {
            String identifier = matcher.group(1).trim();
            if (!identifier.isBlank()) {
                identifiers.add(identifier);
            }
        }
        return identifiers;
    }

    /**
     * 判断是否为字段定义表的通用子标题。
     *
     * @param heading 标题
     * @return 通用子标题返回 true
     */
    private boolean isGenericFieldDefinitionSubheading(String heading) {
        String normalizedHeading = lowerCase(heading);
        return normalizedHeading.contains("field")
                && normalizedHeading.contains("attribute");
    }

    /**
     * 从证据正文中抽取通用编码对照。
     *
     * @param content 证据正文
     * @return 编码对照
     */
    private List<String> extractCodeMappings(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<String> mappings = new ArrayList<String>();
        for (String rawLine : content.split("\\R")) {
            List<String> cells = splitMarkdownTableRow(rawLine);
            if (!looksLikeCodeMappingRow(cells)) {
                continue;
            }
            mappings.add("`" + cleanupMarkdownTableCell(cells.get(0)) + "`=" + cleanupMarkdownTableCell(cells.get(1)));
        }
        return mappings;
    }

    /**
     * 判断是否为编码对照行。
     *
     * @param cells 单元格
     * @return 是编码对照返回 true
     */
    private boolean looksLikeCodeMappingRow(List<String> cells) {
        if (cells == null || cells.size() != 2) {
            return false;
        }
        String code = cleanupMarkdownTableCell(cells.get(0));
        String meaning = cleanupMarkdownTableCell(cells.get(1));
        if (code.isBlank() || meaning.isBlank()) {
            return false;
        }
        return code.matches("[A-Za-z0-9_-]{1,12}") && containsHanText(meaning);
    }

    /**
     * 切分 Markdown 表格行。
     *
     * @param rawLine 原始行
     * @return 单元格
     */
    private List<String> splitMarkdownTableRow(String rawLine) {
        if (rawLine == null) {
            return List.of();
        }
        String line = rawLine.trim();
        if (!line.startsWith("|") || !line.endsWith("|") || line.matches("\\|\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)+\\|")) {
            return List.of();
        }
        String[] rawCells = line.substring(1, line.length() - 1).split("\\|", -1);
        List<String> cells = new ArrayList<String>();
        for (String rawCell : rawCells) {
            cells.add(rawCell.trim());
        }
        return cells;
    }

    /**
     * 清理 Markdown 表格单元格。
     *
     * @param value 原始单元格
     * @return 清理后的单元格
     */
    private String cleanupMarkdownTableCell(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
                .replace("**", "")
                .replace("`", "")
                .replace("<br>", " / ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 判断候选句是否更像章节标题，而不是字段定义。
     *
     * @param rawLine 原始候选句
     * @return 标题类候选返回 true
     */
    private boolean looksLikeHeadingOnlyFallbackLine(String rawLine) {
        if (rawLine == null) {
            return false;
        }
        String trimmedLine = rawLine.trim().toLowerCase(Locale.ROOT);
        return trimmedLine.startsWith("#")
                || trimmedLine.startsWith("<h1")
                || trimmedLine.startsWith("<h2")
                || trimmedLine.startsWith("<h3")
                || trimmedLine.startsWith("<h4");
    }

    /**
     * 统计子串出现次数。
     *
     * @param value 原始字符串
     * @param token 待统计子串
     * @return 出现次数
     */
    private int countOccurrences(String value, String token) {
        if (value == null || value.isBlank() || token == null || token.isBlank()) {
            return 0;
        }
        int count = 0;
        int fromIndex = 0;
        while (fromIndex >= 0) {
            fromIndex = value.indexOf(token, fromIndex);
            if (fromIndex < 0) {
                break;
            }
            count++;
            fromIndex += token.length();
        }
        return count;
    }

    /**
     * 判断字符串是否包含中文。
     *
     * @param value 原始文本
     * @return 包含中文返回 true
     */
    private boolean containsHanText(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.matches(".*\\p{IsHan}.*");
    }

    /**
     * 把文本转成小写字符串。
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

    /**
     * 字段定义匹配结果。
     *
     * @author xiexu
     */
    private static final class FieldDefinitionMatch {

        private final QueryArticleHit queryArticleHit;

        private final String definitionLine;

        /**
         * 创建字段定义匹配结果。
         *
         * @param queryArticleHit 命中的证据
         * @param definitionLine 定义行
         */
        private FieldDefinitionMatch(QueryArticleHit queryArticleHit, String definitionLine) {
            this.queryArticleHit = queryArticleHit;
            this.definitionLine = definitionLine;
        }

        /**
         * 获取命中的证据。
         *
         * @return 命中的证据
         */
        private QueryArticleHit getQueryArticleHit() {
            return queryArticleHit;
        }

        /**
         * 获取定义行。
         *
         * @return 定义行
         */
        private String getDefinitionLine() {
            return definitionLine;
        }
    }

    /**
     * 字段定义表摘要。
     *
     * @author xiexu
     */
    private static final class FieldDefinitionTableSummary {

        private final String displayName;

        private final List<String> fieldDefinitions;

        /**
         * 创建字段定义表摘要。
         *
         * @param displayName 显示名称
         * @param fieldDefinitions 字段定义
         */
        private FieldDefinitionTableSummary(String displayName, List<String> fieldDefinitions) {
            this.displayName = displayName == null ? "" : displayName;
            this.fieldDefinitions = fieldDefinitions == null ? List.of() : List.copyOf(fieldDefinitions);
        }

        /**
         * 获取显示名称。
         *
         * @return 显示名称
         */
        private String getDisplayName() {
            return displayName;
        }

        /**
         * 获取字段定义。
         *
         * @return 字段定义
         */
        private List<String> getFieldDefinitions() {
            return fieldDefinitions;
        }
    }
}
