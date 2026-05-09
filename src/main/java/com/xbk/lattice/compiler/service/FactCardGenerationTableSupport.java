package com.xbk.lattice.compiler.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xbk.lattice.compiler.service.mapper.FactCardGenerationMapper;
import com.xbk.lattice.infra.persistence.FactCardJdbcRepository;
import com.xbk.lattice.infra.persistence.FactCardRecord;
import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;
import com.xbk.lattice.query.evidence.domain.FactCardType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
 * 事实卡表格生成支持
 *
 * 职责：承载 FactCardGenerationService 拆分出来的同类辅助逻辑
 *
 * @author xiexu
 */
abstract class FactCardGenerationTableSupport extends FactCardGenerationBaseSupport {

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
    FactCardRecord buildRecord(
            FactCardSourceChunkView chunk,
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
    String buildCardId(FactCardSourceChunkView chunk, FactCardType cardType, String contentHash) {
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
     * 从 Markdown 表格生成事实证据卡。
     *
     * @param chunk source chunk 视图
     * @param lines chunk 行
     * @return 事实证据卡列表
     */
    List<FactCardRecord> generateTableCards(FactCardSourceChunkView chunk, List<String> lines) {
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
    FactCardRecord buildTableEnumCard(FactCardSourceChunkView chunk, TableBlock tableBlock) {
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
    FactCardRecord buildTableCompareCard(FactCardSourceChunkView chunk, TableBlock tableBlock) {
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
     * 解析 source chunk 中的 Markdown 表格块。
     *
     * @param lines chunk 行
     * @return 表格块列表
     */
    List<TableBlock> findTableBlocks(List<String> lines) {
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
    boolean isCompleteCompareRow(List<String> row, int headerSize) {
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
    TableBlock parseTableBlock(List<String> tableLines, int startIndex) {
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
}
