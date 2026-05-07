package com.xbk.lattice.compiler.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xbk.lattice.infra.persistence.FactCardJdbcRepository;
import com.xbk.lattice.infra.persistence.FactCardRecord;
import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;
import com.xbk.lattice.query.evidence.domain.FactCardType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 事实证据卡生成服务
 *
 * 职责：从 source chunk 中抽取通用结构化证据卡并持久化到 fact_cards
 *
 * @author xiexu
 */
@Service
public class FactCardGenerationService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private static final Pattern BULLET_PATTERN = Pattern.compile("^\\s*[-*+]\\s+(.+?)\\s*$");

    private static final Pattern ORDERED_PATTERN = Pattern.compile(
            "^\\s*((?:\\d+)|(?:[一二三四五六七八九十]+))[.)、．]\\s*(.+?)\\s*$"
    );

    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile(
            "^\\s*([^:=：]{1,80}?)\\s*[:=：]\\s*(.+?)\\s*$"
    );

    private static final List<StatusDefinition> STATUS_DEFINITIONS = List.of(
            new StatusDefinition("待确认", "PENDING"),
            new StatusDefinition("未确认", "PENDING"),
            new StatusDefinition("待处理", "PENDING"),
            new StatusDefinition("pending", "PENDING"),
            new StatusDefinition("已确认", "CONFIRMED"),
            new StatusDefinition("已完成", "CONFIRMED"),
            new StatusDefinition("完成", "CONFIRMED"),
            new StatusDefinition("done", "CONFIRMED"),
            new StatusDefinition("无流量", "FLOW_OFF"),
            new StatusDefinition("有流量", "FLOW_ON"),
            new StatusDefinition("删除", "REMOVED"),
            new StatusDefinition("移除", "REMOVED"),
            new StatusDefinition("下线", "REMOVED"),
            new StatusDefinition("废弃", "REMOVED"),
            new StatusDefinition("禁用", "REMOVED"),
            new StatusDefinition("改为", "CHANGED"),
            new StatusDefinition("调整为", "CHANGED"),
            new StatusDefinition("变更为", "CHANGED")
    );

    private static final List<String> POLICY_MARKERS = List.of(
            "必须",
            "不得",
            "禁止",
            "统一要求",
            "原则",
            "强约束",
            "只能",
            "需要",
            "应当",
            "应该",
            "不可",
            "不允许"
    );

    private static final List<String> POLICY_SCOPE_MARKERS = List.of(
            "适用范围",
            "适用于",
            "适用",
            "对于",
            "当",
            "在",
            "范围"
    );

    private final JdbcTemplate jdbcTemplate;

    private final FactCardJdbcRepository factCardJdbcRepository;

    /**
     * 创建事实证据卡生成服务。
     *
     * @param jdbcTemplate JDBC 模板
     * @param factCardJdbcRepository 事实证据卡仓储
     */
    public FactCardGenerationService(
            JdbcTemplate jdbcTemplate,
            FactCardJdbcRepository factCardJdbcRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.factCardJdbcRepository = factCardJdbcRepository;
    }

    /**
     * 重建指定源文件的全部事实证据卡。
     *
     * @param sourceFileId 源文件主键
     * @return 新生成并已持久化的事实证据卡
     */
    @Transactional(rollbackFor = Exception.class)
    public List<FactCardRecord> rebuildForSourceFile(Long sourceFileId) {
        if (sourceFileId == null || jdbcTemplate == null || factCardJdbcRepository == null) {
            return List.of();
        }
        List<FactCardRecord> factCardRecords = generateForSourceFile(sourceFileId);
        factCardJdbcRepository.deleteBySourceFileId(sourceFileId);
        for (FactCardRecord factCardRecord : factCardRecords) {
            factCardJdbcRepository.upsert(factCardRecord);
        }
        return factCardRecords;
    }

    /**
     * 重建指定源文件的事实证据卡并返回质量摘要。
     *
     * @param sourceFileId 源文件主键
     * @return 生成摘要
     */
    @Transactional(rollbackFor = Exception.class)
    public FactCardGenerationSummary rebuildForSourceFileWithSummary(Long sourceFileId) {
        List<FactCardRecord> factCardRecords = rebuildForSourceFile(sourceFileId);
        return summarize(factCardRecords);
    }

    /**
     * 生成指定源文件的事实证据卡，不执行持久化。
     *
     * @param sourceFileId 源文件主键
     * @return 事实证据卡列表
     */
    public List<FactCardRecord> generateForSourceFile(Long sourceFileId) {
        if (sourceFileId == null || jdbcTemplate == null) {
            return List.of();
        }
        List<SourceChunkView> chunks = findChunksBySourceFileId(sourceFileId);
        List<SourceChunkView> evidenceWindows = buildEvidenceWindows(chunks);
        List<FactCardRecord> factCardRecords = new ArrayList<FactCardRecord>();
        for (SourceChunkView evidenceWindow : evidenceWindows) {
            factCardRecords.addAll(generateForChunk(evidenceWindow));
        }
        return factCardRecords;
    }

    /**
     * 按源文件主键读取 source chunk。
     *
     * @param sourceFileId 源文件主键
     * @return source chunk 视图
     */
    private List<SourceChunkView> findChunksBySourceFileId(Long sourceFileId) {
        return jdbcTemplate.query(
                """
                        select sfc.id,
                               sf.source_id,
                               sfc.source_file_id,
                               sfc.file_path,
                               sfc.chunk_index,
                               sfc.chunk_text
                        from source_file_chunks sfc
                        left join source_files sf on sf.id = sfc.source_file_id
                        where sfc.source_file_id = ?
                        order by sfc.chunk_index asc, sfc.id asc
                        """,
                this::mapSourceChunkView,
                sourceFileId
        );
    }

    /**
     * 映射 source chunk 视图。
     *
     * @param resultSet 结果集
     * @param rowNum 行号
     * @return source chunk 视图
     * @throws SQLException SQL 异常
     */
    private SourceChunkView mapSourceChunkView(ResultSet resultSet, int rowNum) throws SQLException {
        return new SourceChunkView(
                resultSet.getObject("id", Long.class),
                readLong(resultSet, "source_id"),
                resultSet.getObject("source_file_id", Long.class),
                resultSet.getString("file_path"),
                resultSet.getInt("chunk_index"),
                resultSet.getString("chunk_text")
        );
    }

    /**
     * 构造相邻 source chunk 证据窗口。
     *
     * @param chunks 原始 chunk 列表
     * @return 证据窗口列表
     */
    private List<SourceChunkView> buildEvidenceWindows(List<SourceChunkView> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<SourceChunkView> evidenceWindows = new ArrayList<SourceChunkView>();
        SourceChunkView currentWindow = null;
        for (SourceChunkView chunk : chunks) {
            if (currentWindow == null) {
                currentWindow = chunk;
                continue;
            }
            if (shouldMergeAdjacentChunks(currentWindow, chunk)) {
                currentWindow = currentWindow.mergeWith(chunk);
                continue;
            }
            evidenceWindows.add(currentWindow);
            currentWindow = chunk;
        }
        if (currentWindow != null) {
            evidenceWindows.add(currentWindow);
        }
        return evidenceWindows;
    }

    /**
     * 判断两个相邻 chunk 是否应合并为同一个证据窗口。
     *
     * @param currentWindow 当前证据窗口
     * @param nextChunk 下一个 chunk
     * @return 需要合并返回 true
     */
    private boolean shouldMergeAdjacentChunks(SourceChunkView currentWindow, SourceChunkView nextChunk) {
        String previousLine = lastMeaningfulLine(currentWindow.getChunkText());
        String nextLine = firstMeaningfulLine(nextChunk.getChunkText());
        if (previousLine.isBlank() || nextLine.isBlank()) {
            return false;
        }
        if (isTableLine(previousLine) && isTableLine(nextLine)) {
            return true;
        }
        if (isBulletLine(previousLine) && isBulletLine(nextLine)) {
            return true;
        }
        if (isOrderedLine(previousLine) && isOrderedLine(nextLine)) {
            return isLikelyOrderedContinuation(previousLine, nextLine);
        }
        if (isKeyValueLine(previousLine) && isKeyValueLine(nextLine)) {
            return true;
        }
        if (isPolicyLine(previousLine) && isPolicyLine(nextLine)) {
            return true;
        }
        return isLikelyTitleLine(previousLine) && isStructuralStartLine(nextLine);
    }

    /**
     * 为单个 source chunk 生成事实证据卡。
     *
     * @param chunk source chunk 视图
     * @return 事实证据卡列表
     */
    private List<FactCardRecord> generateForChunk(SourceChunkView chunk) {
        List<String> lines = splitLines(chunk.getChunkText());
        List<FactCardRecord> records = new ArrayList<FactCardRecord>();
        records.addAll(generateTableCards(chunk, lines));
        records.addAll(generateBulletEnumCards(chunk, lines));
        records.addAll(generateKeyValueEnumCards(chunk, lines));
        records.addAll(generateSequenceCards(chunk, lines));
        records.addAll(generateStatusCards(chunk, lines));
        records.addAll(generatePolicyCards(chunk, lines));
        return records;
    }

    /**
     * 汇总生成结果质量指标。
     *
     * @param factCardRecords 事实证据卡记录
     * @return 生成摘要
     */
    private FactCardGenerationSummary summarize(List<FactCardRecord> factCardRecords) {
        int withSourceChunkCount = 0;
        int evidenceLocatedCount = 0;
        List<String> cardIds = new ArrayList<String>();
        for (FactCardRecord factCardRecord : factCardRecords) {
            cardIds.add(factCardRecord.getCardId());
            if (!factCardRecord.getSourceChunkIds().isEmpty()) {
                withSourceChunkCount++;
            }
            if (factCardRecord.getReviewStatus() == FactCardReviewStatus.VALID
                    || factCardRecord.getReviewStatus() == FactCardReviewStatus.INCOMPLETE) {
                evidenceLocatedCount++;
            }
        }
        return new FactCardGenerationSummary(
                factCardRecords.size(),
                withSourceChunkCount,
                evidenceLocatedCount,
                cardIds
        );
    }

    /**
     * 从 Markdown 表格生成事实证据卡。
     *
     * @param chunk source chunk 视图
     * @param lines chunk 行
     * @return 事实证据卡列表
     */
    private List<FactCardRecord> generateTableCards(SourceChunkView chunk, List<String> lines) {
        List<TableBlock> tableBlocks = findTableBlocks(lines);
        List<FactCardRecord> records = new ArrayList<FactCardRecord>();
        for (TableBlock tableBlock : tableBlocks) {
            if (tableBlock.getRows().isEmpty()) {
                continue;
            }
            records.add(buildTableEnumCard(chunk, tableBlock));
            if (tableBlock.getHeaders().size() >= 2) {
                records.add(buildTableCompareCard(chunk, tableBlock));
            }
        }
        return records;
    }

    /**
     * 生成表格枚举事实卡。
     *
     * @param chunk source chunk 视图
     * @param tableBlock 表格块
     * @return 事实证据卡
     */
    private FactCardRecord buildTableEnumCard(SourceChunkView chunk, TableBlock tableBlock) {
        ObjectNode rootNode = OBJECT_MAPPER.createObjectNode();
        ArrayNode itemsNode = OBJECT_MAPPER.createArrayNode();
        for (List<String> row : tableBlock.getRows()) {
            ObjectNode itemNode = OBJECT_MAPPER.createObjectNode();
            for (int index = 0; index < tableBlock.getHeaders().size(); index++) {
                String header = tableBlock.getHeaders().get(index);
                itemNode.put(normalizeJsonField(header, index), valueAt(row, index));
            }
            itemsNode.add(itemNode);
        }
        rootNode.put("structure", "markdown_table");
        rootNode.set("items", itemsNode);
        String itemsJson = writeJson(rootNode);
        String claim = "识别到 " + tableBlock.getRows().size() + " 行结构化表格条目。";
        return buildRecord(
                chunk,
                FactCardType.FACT_ENUM,
                AnswerShape.ENUM,
                "结构化表格条目",
                claim,
                itemsJson,
                tableBlock.getEvidenceText(),
                resolveReviewStatus(chunk, tableBlock.getEvidenceText(), true),
                tableBlock.getRows().isEmpty() ? 0.50D : 0.86D
        );
    }

    /**
     * 生成表格对照事实卡。
     *
     * @param chunk source chunk 视图
     * @param tableBlock 表格块
     * @return 事实证据卡
     */
    private FactCardRecord buildTableCompareCard(SourceChunkView chunk, TableBlock tableBlock) {
        ObjectNode rootNode = OBJECT_MAPPER.createObjectNode();
        ArrayNode rowsNode = OBJECT_MAPPER.createArrayNode();
        boolean complete = true;
        for (List<String> row : tableBlock.getRows()) {
            ObjectNode rowNode = OBJECT_MAPPER.createObjectNode();
            for (int index = 0; index < tableBlock.getHeaders().size(); index++) {
                String value = valueAt(row, index);
                rowNode.put(normalizeJsonField(tableBlock.getHeaders().get(index), index), value);
            }
            if (!isCompleteCompareRow(row, tableBlock.getHeaders().size())) {
                complete = false;
            }
            rowsNode.add(rowNode);
        }
        rootNode.put("structure", "markdown_compare_table");
        rootNode.set("rows", rowsNode);
        String claim = "识别到 " + tableBlock.getRows().size() + " 行对照结构。";
        FactCardReviewStatus reviewStatus = resolveReviewStatus(chunk, tableBlock.getEvidenceText(), complete);
        double confidence = complete ? 0.84D : 0.58D;
        return buildRecord(
                chunk,
                FactCardType.FACT_COMPARE,
                AnswerShape.COMPARE,
                "结构化对照表",
                claim,
                writeJson(rootNode),
                tableBlock.getEvidenceText(),
                reviewStatus,
                confidence
        );
    }

    /**
     * 从 bullet 列表生成枚举事实卡。
     *
     * @param chunk source chunk 视图
     * @param lines chunk 行
     * @return 事实证据卡列表
     */
    private List<FactCardRecord> generateBulletEnumCards(SourceChunkView chunk, List<String> lines) {
        List<LineItem> items = findBulletItems(lines);
        if (items.size() < 2) {
            return List.of();
        }
        ObjectNode rootNode = OBJECT_MAPPER.createObjectNode();
        ArrayNode itemsNode = OBJECT_MAPPER.createArrayNode();
        for (LineItem item : items) {
            ObjectNode itemNode = OBJECT_MAPPER.createObjectNode();
            itemNode.put("order", item.getOrder());
            itemNode.put("text", item.getText());
            itemsNode.add(itemNode);
        }
        rootNode.put("structure", "bullet_list");
        rootNode.set("items", itemsNode);
        String evidenceText = joinEvidence(items);
        FactCardRecord record = buildRecord(
                chunk,
                FactCardType.FACT_ENUM,
                AnswerShape.ENUM,
                "结构化列表条目",
                "识别到 " + items.size() + " 个列表条目。",
                writeJson(rootNode),
                evidenceText,
                resolveReviewStatus(chunk, evidenceText, true),
                0.82D
        );
        return List.of(record);
    }

    /**
     * 从重复键值行生成枚举事实卡。
     *
     * @param chunk source chunk 视图
     * @param lines chunk 行
     * @return 事实证据卡列表
     */
    private List<FactCardRecord> generateKeyValueEnumCards(SourceChunkView chunk, List<String> lines) {
        List<KeyValueItem> items = findKeyValueItems(lines);
        if (items.size() < 2) {
            return List.of();
        }
        ObjectNode rootNode = OBJECT_MAPPER.createObjectNode();
        ArrayNode itemsNode = OBJECT_MAPPER.createArrayNode();
        for (KeyValueItem item : items) {
            ObjectNode itemNode = OBJECT_MAPPER.createObjectNode();
            itemNode.put("key", item.getKey());
            itemNode.put("value", item.getValue());
            itemNode.put("raw", item.getRaw());
            itemsNode.add(itemNode);
        }
        rootNode.put("structure", "key_value_list");
        rootNode.set("items", itemsNode);
        String evidenceText = joinKeyValueEvidence(items);
        FactCardRecord record = buildRecord(
                chunk,
                FactCardType.FACT_ENUM,
                AnswerShape.ENUM,
                "结构化键值条目",
                "识别到 " + items.size() + " 个键值条目。",
                writeJson(rootNode),
                evidenceText,
                resolveReviewStatus(chunk, evidenceText, true),
                0.80D
        );
        return List.of(record);
    }

    /**
     * 从有序列表生成顺序事实卡。
     *
     * @param chunk source chunk 视图
     * @param lines chunk 行
     * @return 事实证据卡列表
     */
    private List<FactCardRecord> generateSequenceCards(SourceChunkView chunk, List<String> lines) {
        List<LineItem> items = findOrderedItems(lines);
        if (items.size() < 2) {
            return List.of();
        }
        ObjectNode rootNode = OBJECT_MAPPER.createObjectNode();
        ArrayNode stepsNode = OBJECT_MAPPER.createArrayNode();
        int stepIndex = 1;
        for (LineItem item : items) {
            ObjectNode stepNode = OBJECT_MAPPER.createObjectNode();
            stepNode.put("order", item.getOrder());
            stepNode.put("position", stepIndex);
            stepNode.put("text", item.getText());
            stepsNode.add(stepNode);
            stepIndex++;
        }
        rootNode.put("structure", "ordered_sequence");
        rootNode.set("steps", stepsNode);
        String evidenceText = joinEvidence(items);
        FactCardRecord record = buildRecord(
                chunk,
                FactCardType.FACT_SEQUENCE,
                AnswerShape.SEQUENCE,
                "结构化顺序步骤",
                "识别到 " + items.size() + " 个顺序步骤。",
                writeJson(rootNode),
                evidenceText,
                resolveReviewStatus(chunk, evidenceText, true),
                0.83D
        );
        return List.of(record);
    }

    /**
     * 从通用状态行生成状态事实卡。
     *
     * @param chunk source chunk 视图
     * @param lines chunk 行
     * @return 事实证据卡列表
     */
    private List<FactCardRecord> generateStatusCards(SourceChunkView chunk, List<String> lines) {
        List<StatusItem> items = findStatusItems(lines);
        if (items.size() < 2) {
            return List.of();
        }
        ObjectNode rootNode = OBJECT_MAPPER.createObjectNode();
        ArrayNode itemsNode = OBJECT_MAPPER.createArrayNode();
        for (StatusItem item : items) {
            ObjectNode itemNode = OBJECT_MAPPER.createObjectNode();
            itemNode.put("subject", item.getSubject());
            itemNode.put("status", item.getStatus());
            itemNode.put("statusGroup", item.getStatusGroup());
            itemNode.put("raw", item.getRaw());
            itemsNode.add(itemNode);
        }
        rootNode.put("structure", "status_group");
        rootNode.set("items", itemsNode);
        ArrayNode conflictSubjectsNode = OBJECT_MAPPER.createArrayNode();
        for (String subject : findConflictSubjects(items)) {
            conflictSubjectsNode.add(subject);
        }
        rootNode.set("conflictSubjects", conflictSubjectsNode);
        boolean conflict = conflictSubjectsNode.size() > 0;
        String evidenceText = joinStatusEvidence(items);
        FactCardReviewStatus reviewStatus = conflict
                ? FactCardReviewStatus.CONFLICT
                : resolveReviewStatus(chunk, evidenceText, true);
        double confidence = conflict ? 0.45D : 0.81D;
        String claim = conflict
                ? "识别到状态冲突，需人工确认互斥状态。"
                : "识别到 " + items.size() + " 个状态条目。";
        FactCardRecord record = buildRecord(
                chunk,
                FactCardType.FACT_STATUS,
                AnswerShape.STATUS,
                "结构化状态分组",
                claim,
                writeJson(rootNode),
                evidenceText,
                reviewStatus,
                confidence
        );
        return List.of(record);
    }

    /**
     * 从通用约束行生成规则事实卡。
     *
     * @param chunk source chunk 视图
     * @param lines chunk 行
     * @return 事实证据卡列表
     */
    private List<FactCardRecord> generatePolicyCards(SourceChunkView chunk, List<String> lines) {
        List<PolicyItem> constraints = findPolicyItems(lines);
        if (constraints.isEmpty()) {
            return List.of();
        }
        List<String> scopes = findPolicyScopes(lines);
        ObjectNode rootNode = OBJECT_MAPPER.createObjectNode();
        ArrayNode constraintsNode = OBJECT_MAPPER.createArrayNode();
        for (PolicyItem constraint : constraints) {
            ObjectNode constraintNode = OBJECT_MAPPER.createObjectNode();
            constraintNode.put("constraint", constraint.getText());
            constraintNode.put("raw", constraint.getRaw());
            constraintsNode.add(constraintNode);
        }
        ArrayNode scopesNode = OBJECT_MAPPER.createArrayNode();
        for (String scope : scopes) {
            scopesNode.add(scope);
        }
        rootNode.put("structure", "policy_constraints");
        rootNode.set("constraints", constraintsNode);
        rootNode.set("scopes", scopesNode);
        boolean complete = !scopes.isEmpty();
        String evidenceText = joinPolicyEvidence(lines, constraints, scopes);
        String claim = complete
                ? "识别到 " + constraints.size() + " 条规则约束及适用范围。"
                : "识别到 " + constraints.size() + " 条规则约束，但缺少适用范围。";
        FactCardRecord record = buildRecord(
                chunk,
                FactCardType.FACT_POLICY,
                AnswerShape.POLICY,
                "结构化规则约束",
                claim,
                writeJson(rootNode),
                evidenceText,
                resolveReviewStatus(chunk, evidenceText, complete),
                complete ? 0.80D : 0.56D
        );
        return List.of(record);
    }

    /**
     * 构造事实证据卡记录。
     *
     * @param chunk source chunk 视图
     * @param cardType 证据卡类型
     * @param answerShape 答案形态
     * @param title 标题
     * @param claim 结论
     * @param itemsJson 结构化条目 JSON
     * @param evidenceText 原文证据文本
     * @param reviewStatus 审查状态
     * @param confidence 置信度
     * @return 事实证据卡记录
     */
    private FactCardRecord buildRecord(
            SourceChunkView chunk,
            FactCardType cardType,
            AnswerShape answerShape,
            String title,
            String claim,
            String itemsJson,
            String evidenceText,
            FactCardReviewStatus reviewStatus,
            double confidence
    ) {
        String contentHash = sha256Hex(cardType.name() + "\n" + evidenceText + "\n" + itemsJson);
        String cardId = buildCardId(chunk, cardType, contentHash);
        return new FactCardRecord(
                cardId,
                chunk.getSourceId(),
                chunk.getSourceFileId(),
                cardType,
                answerShape,
                title + " - " + chunk.getFilePath() + "#" + chunk.getChunkIndex(),
                claim,
                itemsJson,
                evidenceText,
                chunk.getSourceChunkIds(),
                List.of(),
                confidence,
                reviewStatus,
                contentHash
        );
    }

    /**
     * 构造稳定证据卡标识。
     *
     * @param chunk source chunk 视图
     * @param cardType 证据卡类型
     * @param contentHash 内容哈希
     * @return 证据卡标识
     */
    private String buildCardId(SourceChunkView chunk, FactCardType cardType, String contentHash) {
        String hashPrefix = contentHash.substring(0, 16);
        return "fact-card:"
                + chunk.getSourceFileId()
                + ":"
                + chunk.getChunkIndex()
                + ":"
                + cardType.name().toLowerCase()
                + ":"
                + hashPrefix;
    }

    /**
     * 解析 source chunk 中的 Markdown 表格块。
     *
     * @param lines chunk 行
     * @return 表格块列表
     */
    private List<TableBlock> findTableBlocks(List<String> lines) {
        List<TableBlock> tableBlocks = new ArrayList<TableBlock>();
        int index = 0;
        while (index < lines.size()) {
            if (!isTableLine(lines.get(index))) {
                index++;
                continue;
            }
            int startIndex = index;
            List<String> tableLines = new ArrayList<String>();
            while (index < lines.size() && isTableLine(lines.get(index))) {
                tableLines.add(lines.get(index));
                index++;
            }
            TableBlock tableBlock = parseTableBlock(tableLines, startIndex);
            if (tableBlock != null) {
                tableBlocks.add(tableBlock);
            }
        }
        return tableBlocks;
    }

    /**
     * 判断对照行是否完整。
     *
     * @param row 表格行
     * @param headerSize 表头数量
     * @return 对照行是否完整
     */
    private boolean isCompleteCompareRow(List<String> row, int headerSize) {
        if (headerSize < 2) {
            return false;
        }
        for (int index = 0; index < headerSize; index++) {
            if (valueAt(row, index).isBlank()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 解析单个 Markdown 表格块。
     *
     * @param tableLines 表格行
     * @param startIndex 起始行号
     * @return 表格块
     */
    private TableBlock parseTableBlock(List<String> tableLines, int startIndex) {
        if (tableLines.size() < 2) {
            return null;
        }
        List<String> headers = splitTableCells(tableLines.get(0));
        int dataStartIndex = isSeparatorRow(tableLines.get(1)) ? 2 : 1;
        List<List<String>> rows = new ArrayList<List<String>>();
        for (int index = dataStartIndex; index < tableLines.size(); index++) {
            List<String> row = splitTableCells(tableLines.get(index));
            if (!row.isEmpty()) {
                rows.add(row);
            }
        }
        if (headers.isEmpty() || rows.isEmpty()) {
            return null;
        }
        return new TableBlock(headers, rows, String.join("\n", tableLines), startIndex);
    }

    /**
     * 判断是否为 Markdown 表格行。
     *
     * @param line 文本行
     * @return 是否为表格行
     */
    private boolean isTableLine(String line) {
        return line != null && line.trim().startsWith("|") && line.trim().endsWith("|");
    }

    /**
     * 判断是否为 bullet 行。
     *
     * @param line 文本行
     * @return 是否为 bullet 行
     */
    private boolean isBulletLine(String line) {
        return line != null && BULLET_PATTERN.matcher(line).matches();
    }

    /**
     * 判断是否为有序列表行。
     *
     * @param line 文本行
     * @return 是否为有序列表行
     */
    private boolean isOrderedLine(String line) {
        return line != null && ORDERED_PATTERN.matcher(line).matches();
    }

    /**
     * 判断两个有序列表行是否可能是连续结构。
     *
     * @param previousLine 前一行
     * @param nextLine 后一行
     * @return 是连续结构返回 true
     */
    private boolean isLikelyOrderedContinuation(String previousLine, String nextLine) {
        Integer previousOrder = parseOrderNumber(previousLine);
        Integer nextOrder = parseOrderNumber(nextLine);
        if (previousOrder == null || nextOrder == null) {
            return true;
        }
        return nextOrder.intValue() == previousOrder.intValue() + 1;
    }

    /**
     * 解析有序列表行的数字序号。
     *
     * @param line 文本行
     * @return 序号数字
     */
    private Integer parseOrderNumber(String line) {
        Matcher matcher = ORDERED_PATTERN.matcher(line == null ? "" : line);
        if (!matcher.matches()) {
            return null;
        }
        String rawOrder = matcher.group(1).trim();
        if (rawOrder.matches("\\d+")) {
            return Integer.valueOf(rawOrder);
        }
        return parseChineseOrderNumber(rawOrder);
    }

    /**
     * 解析常见中文序号。
     *
     * @param value 中文序号
     * @return 序号数字
     */
    private Integer parseChineseOrderNumber(String value) {
        Map<Character, Integer> numberByCharacter = new LinkedHashMap<Character, Integer>();
        numberByCharacter.put(Character.valueOf('一'), Integer.valueOf(1));
        numberByCharacter.put(Character.valueOf('二'), Integer.valueOf(2));
        numberByCharacter.put(Character.valueOf('三'), Integer.valueOf(3));
        numberByCharacter.put(Character.valueOf('四'), Integer.valueOf(4));
        numberByCharacter.put(Character.valueOf('五'), Integer.valueOf(5));
        numberByCharacter.put(Character.valueOf('六'), Integer.valueOf(6));
        numberByCharacter.put(Character.valueOf('七'), Integer.valueOf(7));
        numberByCharacter.put(Character.valueOf('八'), Integer.valueOf(8));
        numberByCharacter.put(Character.valueOf('九'), Integer.valueOf(9));
        String normalized = safeText(value).trim();
        if (normalized.isBlank()) {
            return null;
        }
        if ("十".equals(normalized)) {
            return Integer.valueOf(10);
        }
        if (normalized.startsWith("十") && normalized.length() == 2) {
            Integer suffix = numberByCharacter.get(Character.valueOf(normalized.charAt(1)));
            return suffix == null ? null : Integer.valueOf(10 + suffix.intValue());
        }
        if (normalized.endsWith("十") && normalized.length() == 2) {
            Integer prefix = numberByCharacter.get(Character.valueOf(normalized.charAt(0)));
            return prefix == null ? null : Integer.valueOf(prefix.intValue() * 10);
        }
        if (normalized.length() == 1) {
            return numberByCharacter.get(Character.valueOf(normalized.charAt(0)));
        }
        if (normalized.length() == 3 && normalized.charAt(1) == '十') {
            Integer prefix = numberByCharacter.get(Character.valueOf(normalized.charAt(0)));
            Integer suffix = numberByCharacter.get(Character.valueOf(normalized.charAt(2)));
            if (prefix != null && suffix != null) {
                return Integer.valueOf(prefix.intValue() * 10 + suffix.intValue());
            }
        }
        return null;
    }

    /**
     * 判断是否为键值行。
     *
     * @param line 文本行
     * @return 是否为键值行
     */
    private boolean isKeyValueLine(String line) {
        return line != null && KEY_VALUE_PATTERN.matcher(stripListMarker(line)).matches();
    }

    /**
     * 判断是否为规则行。
     *
     * @param line 文本行
     * @return 是否为规则行
     */
    private boolean isPolicyLine(String line) {
        String text = stripListMarker(line).trim();
        return hasAnyMarker(text, POLICY_MARKERS) || hasAnyMarker(text, POLICY_SCOPE_MARKERS);
    }

    /**
     * 判断是否可能是标题行。
     *
     * @param line 文本行
     * @return 可能是标题返回 true
     */
    private boolean isLikelyTitleLine(String line) {
        String normalized = safeText(line).trim();
        if (normalized.isBlank()) {
            return false;
        }
        if (isStructuralStartLine(normalized) || normalized.length() > 80) {
            return false;
        }
        return normalized.startsWith("#")
                || normalized.endsWith("：")
                || normalized.endsWith(":")
                || (!normalized.matches(".*[。.!！?？]$") && !KEY_VALUE_PATTERN.matcher(normalized).matches());
    }

    /**
     * 判断是否为结构块起始行。
     *
     * @param line 文本行
     * @return 是结构块起始行返回 true
     */
    private boolean isStructuralStartLine(String line) {
        return isTableLine(line)
                || isBulletLine(line)
                || isOrderedLine(line)
                || isKeyValueLine(line)
                || isPolicyLine(line);
    }

    /**
     * 判断是否为 Markdown 表格分隔行。
     *
     * @param line 文本行
     * @return 是否为分隔行
     */
    private boolean isSeparatorRow(String line) {
        List<String> cells = splitTableCells(line);
        if (cells.isEmpty()) {
            return false;
        }
        for (String cell : cells) {
            String normalized = cell.replace(":", "").replace("-", "").trim();
            if (!normalized.isBlank()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 拆分 Markdown 表格单元格。
     *
     * @param line 表格行
     * @return 单元格列表
     */
    private List<String> splitTableCells(String line) {
        if (line == null || line.isBlank()) {
            return List.of();
        }
        String normalized = line.trim();
        if (normalized.startsWith("|")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("|")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        String[] rawCells = normalized.split("\\|", -1);
        List<String> cells = new ArrayList<String>();
        for (String rawCell : rawCells) {
            cells.add(rawCell.trim());
        }
        return cells;
    }

    /**
     * 查找 bullet 列表项。
     *
     * @param lines chunk 行
     * @return 列表项
     */
    private List<LineItem> findBulletItems(List<String> lines) {
        List<LineItem> items = new ArrayList<LineItem>();
        int order = 1;
        for (String line : lines) {
            Matcher matcher = BULLET_PATTERN.matcher(line);
            if (matcher.matches()) {
                items.add(new LineItem(String.valueOf(order), matcher.group(1).trim(), line.trim()));
                order++;
            }
        }
        return items;
    }

    /**
     * 查找有序列表项。
     *
     * @param lines chunk 行
     * @return 有序列表项
     */
    private List<LineItem> findOrderedItems(List<String> lines) {
        List<LineItem> items = new ArrayList<LineItem>();
        for (String line : lines) {
            Matcher matcher = ORDERED_PATTERN.matcher(line);
            if (matcher.matches()) {
                items.add(new LineItem(matcher.group(1).trim(), matcher.group(2).trim(), line.trim()));
            }
        }
        return items;
    }

    /**
     * 查找键值列表项。
     *
     * @param lines chunk 行
     * @return 键值列表项
     */
    private List<KeyValueItem> findKeyValueItems(List<String> lines) {
        List<KeyValueItem> items = new ArrayList<KeyValueItem>();
        for (String line : lines) {
            Matcher matcher = KEY_VALUE_PATTERN.matcher(stripListMarker(line));
            if (!matcher.matches()) {
                continue;
            }
            String key = matcher.group(1).trim();
            String value = matcher.group(2).trim();
            if (key.isBlank() || value.isBlank()) {
                continue;
            }
            items.add(new KeyValueItem(key, value, line.trim()));
        }
        return items;
    }

    /**
     * 查找状态条目。
     *
     * @param lines chunk 行
     * @return 状态条目
     */
    private List<StatusItem> findStatusItems(List<String> lines) {
        List<StatusItem> items = new ArrayList<StatusItem>();
        for (String line : lines) {
            String text = stripListMarker(line).trim();
            StatusDefinition statusDefinition = findStatusDefinition(text);
            if (statusDefinition == null) {
                continue;
            }
            String subject = resolveStatusSubject(text, statusDefinition.getValue());
            if (subject.isBlank()) {
                continue;
            }
            items.add(new StatusItem(subject, statusDefinition.getValue(), statusDefinition.getGroup(), line.trim()));
        }
        return items;
    }

    /**
     * 查找规则约束条目。
     *
     * @param lines chunk 行
     * @return 规则约束条目
     */
    private List<PolicyItem> findPolicyItems(List<String> lines) {
        List<PolicyItem> items = new ArrayList<PolicyItem>();
        for (String line : lines) {
            String text = stripListMarker(line).trim();
            if (!hasAnyMarker(text, POLICY_MARKERS)) {
                continue;
            }
            items.add(new PolicyItem(text, line.trim()));
        }
        return items;
    }

    /**
     * 查找规则适用范围。
     *
     * @param lines chunk 行
     * @return 适用范围行
     */
    private List<String> findPolicyScopes(List<String> lines) {
        List<String> scopes = new ArrayList<String>();
        for (String line : lines) {
            String text = stripListMarker(line).trim();
            if (hasAnyMarker(text, POLICY_SCOPE_MARKERS)) {
                scopes.add(line.trim());
            }
        }
        return scopes;
    }

    /**
     * 查找状态定义。
     *
     * @param text 文本
     * @return 状态定义
     */
    private StatusDefinition findStatusDefinition(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (StatusDefinition statusDefinition : STATUS_DEFINITIONS) {
            if (text.contains(statusDefinition.getValue())) {
                return statusDefinition;
            }
        }
        return null;
    }

    /**
     * 解析状态条目的主语。
     *
     * @param text 文本
     * @param status 状态词
     * @return 状态主语
     */
    private String resolveStatusSubject(String text, String status) {
        Matcher matcher = KEY_VALUE_PATTERN.matcher(text);
        if (matcher.matches()) {
            String key = matcher.group(1).trim();
            String value = matcher.group(2).trim();
            if (value.contains(status)) {
                return normalizeStatusSubject(key);
            }
            if (key.contains(status)) {
                return normalizeStatusSubject(value);
            }
        }
        String subject = text.replace(status, " ");
        return normalizeStatusSubject(subject);
    }

    /**
     * 规范化状态主语。
     *
     * @param value 原始主语
     * @return 规范化主语
     */
    private String normalizeStatusSubject(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("^[\\s:：,，;；\\-—>]+", "")
                .replaceAll("[\\s:：,，;；\\-—>]+$", "")
                .trim();
    }

    /**
     * 查询状态冲突主语。
     *
     * @param items 状态条目
     * @return 冲突主语
     */
    private List<String> findConflictSubjects(List<StatusItem> items) {
        Map<String, Set<String>> groupsBySubject = new LinkedHashMap<String, Set<String>>();
        Map<String, String> displaySubjectByKey = new LinkedHashMap<String, String>();
        for (StatusItem item : items) {
            String key = conflictSubjectKey(item.getSubject());
            displaySubjectByKey.putIfAbsent(key, item.getSubject());
            groupsBySubject.computeIfAbsent(key, ignored -> new LinkedHashSet<String>()).add(item.getStatusGroup());
        }
        List<String> conflictSubjects = new ArrayList<String>();
        for (Map.Entry<String, Set<String>> entry : groupsBySubject.entrySet()) {
            if (entry.getValue().size() > 1) {
                conflictSubjects.add(displaySubjectByKey.get(entry.getKey()));
            }
        }
        return conflictSubjects;
    }

    /**
     * 生成状态冲突判定 key。
     *
     * @param subject 主语
     * @return 判定 key
     */
    private String conflictSubjectKey(String subject) {
        return safeText(subject)
                .replaceAll("\\s+", "")
                .replaceAll("[：:，,；;\\-—>]", "")
                .toLowerCase();
    }

    /**
     * 判断文本是否包含任一标记。
     *
     * @param text 文本
     * @param markers 标记列表
     * @return 是否包含
     */
    private boolean hasAnyMarker(String text, List<String> markers) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String marker : markers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 去掉列表标记，便于识别列表内键值结构。
     *
     * @param line 原始行
     * @return 去标记后的行
     */
    private String stripListMarker(String line) {
        if (line == null) {
            return "";
        }
        Matcher bulletMatcher = BULLET_PATTERN.matcher(line);
        if (bulletMatcher.matches()) {
            return bulletMatcher.group(1);
        }
        Matcher orderedMatcher = ORDERED_PATTERN.matcher(line);
        if (orderedMatcher.matches()) {
            return orderedMatcher.group(2);
        }
        return line;
    }

    /**
     * 解析审查状态。
     *
     * @param chunk source chunk 视图
     * @param evidenceText 证据文本
     * @param complete 结构是否完整
     * @return 审查状态
     */
    private FactCardReviewStatus resolveReviewStatus(SourceChunkView chunk, String evidenceText, boolean complete) {
        if (!complete) {
            return FactCardReviewStatus.INCOMPLETE;
        }
        String chunkText = safeText(chunk.getChunkText());
        if (!chunkText.contains(safeText(evidenceText))) {
            return FactCardReviewStatus.LOW_CONFIDENCE;
        }
        return FactCardReviewStatus.VALID;
    }

    /**
     * 拼接列表项证据文本。
     *
     * @param items 列表项
     * @return 证据文本
     */
    private String joinEvidence(List<LineItem> items) {
        List<String> lines = new ArrayList<String>();
        for (LineItem item : items) {
            lines.add(item.getRaw());
        }
        return String.join("\n", lines);
    }

    /**
     * 拼接键值项证据文本。
     *
     * @param items 键值项
     * @return 证据文本
     */
    private String joinKeyValueEvidence(List<KeyValueItem> items) {
        List<String> lines = new ArrayList<String>();
        for (KeyValueItem item : items) {
            lines.add(item.getRaw());
        }
        return String.join("\n", lines);
    }

    /**
     * 拼接状态条目证据文本。
     *
     * @param items 状态条目
     * @return 证据文本
     */
    private String joinStatusEvidence(List<StatusItem> items) {
        List<String> lines = new ArrayList<String>();
        for (StatusItem item : items) {
            lines.add(item.getRaw());
        }
        return String.join("\n", lines);
    }

    /**
     * 拼接规则条目证据文本。
     *
     * @param constraints 规则约束
     * @param scopes 适用范围
     * @return 证据文本
     */
    private String joinPolicyEvidence(List<String> sourceLines, List<PolicyItem> constraints, List<String> scopes) {
        List<String> lines = new ArrayList<String>();
        Set<String> relevantLines = new LinkedHashSet<String>();
        for (PolicyItem constraint : constraints) {
            relevantLines.add(constraint.getRaw());
        }
        for (String scope : scopes) {
            relevantLines.add(scope);
        }
        for (String sourceLine : sourceLines) {
            String normalizedLine = sourceLine == null ? "" : sourceLine.trim();
            if (relevantLines.contains(normalizedLine)) {
                addUniqueLine(lines, normalizedLine);
            }
        }
        return String.join("\n", lines);
    }

    /**
     * 按原始顺序添加唯一证据行。
     *
     * @param lines 已收集证据行
     * @param line 待添加证据行
     */
    private void addUniqueLine(List<String> lines, String line) {
        if (line == null || line.isBlank() || lines.contains(line)) {
            return;
        }
        lines.add(line);
    }

    /**
     * 读取最后一个非空行。
     *
     * @param text 文本
     * @return 最后一个非空行
     */
    private String lastMeaningfulLine(String text) {
        List<String> lines = splitLines(text);
        for (int index = lines.size() - 1; index >= 0; index--) {
            String line = lines.get(index);
            if (line != null && !line.trim().isBlank()) {
                return line.trim();
            }
        }
        return "";
    }

    /**
     * 读取第一个非空行。
     *
     * @param text 文本
     * @return 第一个非空行
     */
    private String firstMeaningfulLine(String text) {
        List<String> lines = splitLines(text);
        for (String line : lines) {
            if (line != null && !line.trim().isBlank()) {
                return line.trim();
            }
        }
        return "";
    }

    /**
     * 拆分文本行。
     *
     * @param text 文本
     * @return 行列表
     */
    private List<String> splitLines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] rawLines = text.split("\\R");
        List<String> lines = new ArrayList<String>();
        Collections.addAll(lines, rawLines);
        return lines;
    }

    /**
     * 读取可空长整型列。
     *
     * @param resultSet 结果集
     * @param columnName 列名
     * @return 长整型值
     * @throws SQLException SQL 异常
     */
    private Long readLong(ResultSet resultSet, String columnName) throws SQLException {
        Object value = resultSet.getObject(columnName);
        return value == null ? null : resultSet.getLong(columnName);
    }

    /**
     * 序列化 JSON 节点。
     *
     * @param node JSON 节点
     * @return JSON 字符串
     */
    private String writeJson(ObjectNode node) {
        try {
            return OBJECT_MAPPER.writeValueAsString(node);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to write fact card items json", ex);
        }
    }

    /**
     * 读取列表指定下标的值。
     *
     * @param values 值列表
     * @param index 下标
     * @return 值
     */
    private String valueAt(List<String> values, int index) {
        if (index < 0 || index >= values.size()) {
            return "";
        }
        return values.get(index);
    }

    /**
     * 规范化 JSON 字段名。
     *
     * @param header 表头
     * @param index 下标
     * @return JSON 字段名
     */
    private String normalizeJsonField(String header, int index) {
        String normalized = safeText(header)
                .trim()
                .replaceAll("\\s+", "_")
                .replaceAll("[^A-Za-z0-9_\\u4e00-\\u9fa5]", "");
        if (normalized.isBlank()) {
            return "column_" + (index + 1);
        }
        return normalized;
    }

    /**
     * 计算 SHA-256 十六进制哈希。
     *
     * @param value 原始值
     * @return 哈希值
     */
    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(safeText(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) {
                builder.append(String.format("%02x", Byte.valueOf(item)));
            }
            return builder.toString();
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    /**
     * 返回非空文本。
     *
     * @param value 原始值
     * @return 非空文本
     */
    private String safeText(String value) {
        return value == null ? "" : value;
    }

    /**
     * source chunk 轻量视图。
     *
     * @author xiexu
     */
    private static final class SourceChunkView {

        private final Long id;

        private final Long sourceId;

        private final Long sourceFileId;

        private final String filePath;

        private final int chunkIndex;

        private final String chunkText;

        private final List<Long> sourceChunkIds;

        /**
         * 创建 source chunk 视图。
         *
         * @param id chunk 主键
         * @param sourceId 资料源主键
         * @param sourceFileId 源文件主键
         * @param filePath 文件路径
         * @param chunkIndex chunk 序号
         * @param chunkText chunk 文本
         */
        private SourceChunkView(
                Long id,
                Long sourceId,
                Long sourceFileId,
                String filePath,
                int chunkIndex,
                String chunkText
        ) {
            this(id, sourceId, sourceFileId, filePath, chunkIndex, chunkText, List.of(id));
        }

        /**
         * 创建 source chunk 视图。
         *
         * @param id chunk 主键
         * @param sourceId 资料源主键
         * @param sourceFileId 源文件主键
         * @param filePath 文件路径
         * @param chunkIndex chunk 序号
         * @param chunkText chunk 文本
         * @param sourceChunkIds 窗口包含的 chunk 主键
         */
        private SourceChunkView(
                Long id,
                Long sourceId,
                Long sourceFileId,
                String filePath,
                int chunkIndex,
                String chunkText,
                List<Long> sourceChunkIds
        ) {
            this.id = id;
            this.sourceId = sourceId;
            this.sourceFileId = sourceFileId;
            this.filePath = filePath;
            this.chunkIndex = chunkIndex;
            this.chunkText = chunkText;
            this.sourceChunkIds = sourceChunkIds == null ? List.of() : List.copyOf(sourceChunkIds);
        }

        /**
         * 合并相邻 chunk 为证据窗口。
         *
         * @param nextChunk 下一个 chunk
         * @return 合并后的证据窗口
         */
        private SourceChunkView mergeWith(SourceChunkView nextChunk) {
            List<Long> mergedSourceChunkIds = new ArrayList<Long>(sourceChunkIds);
            mergedSourceChunkIds.addAll(nextChunk.getSourceChunkIds());
            String mergedChunkText = joinChunkText(chunkText, nextChunk.getChunkText());
            return new SourceChunkView(
                    id,
                    sourceId,
                    sourceFileId,
                    filePath,
                    chunkIndex,
                    mergedChunkText,
                    mergedSourceChunkIds
            );
        }

        /**
         * 拼接相邻 chunk 文本。
         *
         * @param previousText 前一段文本
         * @param nextText 后一段文本
         * @return 拼接文本
         */
        private String joinChunkText(String previousText, String nextText) {
            String previous = previousText == null ? "" : previousText.stripTrailing();
            String next = nextText == null ? "" : nextText.stripLeading();
            if (previous.isBlank()) {
                return next;
            }
            if (next.isBlank()) {
                return previous;
            }
            return previous + "\n" + next;
        }

        /**
         * 获取 chunk 主键。
         *
         * @return chunk 主键
         */
        private Long getId() {
            return id;
        }

        /**
         * 获取资料源主键。
         *
         * @return 资料源主键
         */
        private Long getSourceId() {
            return sourceId;
        }

        /**
         * 获取源文件主键。
         *
         * @return 源文件主键
         */
        private Long getSourceFileId() {
            return sourceFileId;
        }

        /**
         * 获取文件路径。
         *
         * @return 文件路径
         */
        private String getFilePath() {
            return filePath;
        }

        /**
         * 获取 chunk 序号。
         *
         * @return chunk 序号
         */
        private int getChunkIndex() {
            return chunkIndex;
        }

        /**
         * 获取 chunk 文本。
         *
         * @return chunk 文本
         */
        private String getChunkText() {
            return chunkText;
        }

        /**
         * 获取窗口包含的 chunk 主键。
         *
         * @return chunk 主键
         */
        private List<Long> getSourceChunkIds() {
            return sourceChunkIds;
        }
    }

    /**
     * 表格块。
     *
     * @author xiexu
     */
    private static final class TableBlock {

        private final List<String> headers;

        private final List<List<String>> rows;

        private final String evidenceText;

        private final int startIndex;

        /**
         * 创建表格块。
         *
         * @param headers 表头
         * @param rows 数据行
         * @param evidenceText 证据文本
         * @param startIndex 起始行号
         */
        private TableBlock(List<String> headers, List<List<String>> rows, String evidenceText, int startIndex) {
            this.headers = headers;
            this.rows = rows;
            this.evidenceText = evidenceText;
            this.startIndex = startIndex;
        }

        /**
         * 获取表头。
         *
         * @return 表头
         */
        private List<String> getHeaders() {
            return headers;
        }

        /**
         * 获取数据行。
         *
         * @return 数据行
         */
        private List<List<String>> getRows() {
            return rows;
        }

        /**
         * 获取证据文本。
         *
         * @return 证据文本
         */
        private String getEvidenceText() {
            return evidenceText;
        }

        /**
         * 获取起始行号。
         *
         * @return 起始行号
         */
        private int getStartIndex() {
            return startIndex;
        }
    }

    /**
     * 文本列表项。
     *
     * @author xiexu
     */
    private static final class LineItem {

        private final String order;

        private final String text;

        private final String raw;

        /**
         * 创建文本列表项。
         *
         * @param order 顺序标识
         * @param text 文本
         * @param raw 原始行
         */
        private LineItem(String order, String text, String raw) {
            this.order = order;
            this.text = text;
            this.raw = raw;
        }

        /**
         * 获取顺序标识。
         *
         * @return 顺序标识
         */
        private String getOrder() {
            return order;
        }

        /**
         * 获取文本。
         *
         * @return 文本
         */
        private String getText() {
            return text;
        }

        /**
         * 获取原始行。
         *
         * @return 原始行
         */
        private String getRaw() {
            return raw;
        }
    }

    /**
     * 键值列表项。
     *
     * @author xiexu
     */
    private static final class KeyValueItem {

        private final String key;

        private final String value;

        private final String raw;

        /**
         * 创建键值列表项。
         *
         * @param key 键
         * @param value 值
         * @param raw 原始行
         */
        private KeyValueItem(String key, String value, String raw) {
            this.key = key;
            this.value = value;
            this.raw = raw;
        }

        /**
         * 获取键。
         *
         * @return 键
         */
        private String getKey() {
            return key;
        }

        /**
         * 获取值。
         *
         * @return 值
         */
        private String getValue() {
            return value;
        }

        /**
         * 获取原始行。
         *
         * @return 原始行
         */
        private String getRaw() {
            return raw;
        }
    }

    /**
     * 状态定义。
     *
     * @author xiexu
     */
    private static final class StatusDefinition {

        private final String value;

        private final String group;

        /**
         * 创建状态定义。
         *
         * @param value 状态词
         * @param group 互斥分组
         */
        private StatusDefinition(String value, String group) {
            this.value = value;
            this.group = group;
        }

        /**
         * 获取状态词。
         *
         * @return 状态词
         */
        private String getValue() {
            return value;
        }

        /**
         * 获取互斥分组。
         *
         * @return 互斥分组
         */
        private String getGroup() {
            return group;
        }
    }

    /**
     * 状态条目。
     *
     * @author xiexu
     */
    private static final class StatusItem {

        private final String subject;

        private final String status;

        private final String statusGroup;

        private final String raw;

        /**
         * 创建状态条目。
         *
         * @param subject 状态主语
         * @param status 状态词
         * @param statusGroup 互斥分组
         * @param raw 原始行
         */
        private StatusItem(String subject, String status, String statusGroup, String raw) {
            this.subject = subject;
            this.status = status;
            this.statusGroup = statusGroup;
            this.raw = raw;
        }

        /**
         * 获取状态主语。
         *
         * @return 状态主语
         */
        private String getSubject() {
            return subject;
        }

        /**
         * 获取状态词。
         *
         * @return 状态词
         */
        private String getStatus() {
            return status;
        }

        /**
         * 获取互斥分组。
         *
         * @return 互斥分组
         */
        private String getStatusGroup() {
            return statusGroup;
        }

        /**
         * 获取原始行。
         *
         * @return 原始行
         */
        private String getRaw() {
            return raw;
        }
    }

    /**
     * 规则约束条目。
     *
     * @author xiexu
     */
    private static final class PolicyItem {

        private final String text;

        private final String raw;

        /**
         * 创建规则约束条目。
         *
         * @param text 约束文本
         * @param raw 原始行
         */
        private PolicyItem(String text, String raw) {
            this.text = text;
            this.raw = raw;
        }

        /**
         * 获取约束文本。
         *
         * @return 约束文本
         */
        private String getText() {
            return text;
        }

        /**
         * 获取原始行。
         *
         * @return 原始行
         */
        private String getRaw() {
            return raw;
        }
    }
}
