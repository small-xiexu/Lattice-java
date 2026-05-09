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
 * 事实卡证据窗口与汇总支持
 *
 * 职责：承载 FactCardGenerationService 拆分出来的同类辅助逻辑
 *
 * @author xiexu
 */
abstract class FactCardGenerationWindowSupport extends FactCardGenerationStatusPolicySupport {

/**
     * 构造相邻 source chunk 证据窗口。
     *
     * @param chunks 原始 chunk 列表
     * @return 证据窗口列表
     */
    List<FactCardSourceChunkView> buildEvidenceWindows(List<FactCardSourceChunkView> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<FactCardSourceChunkView> evidenceWindows = new ArrayList<FactCardSourceChunkView>();
        FactCardSourceChunkView currentWindow = null;
        for (FactCardSourceChunkView chunk : chunks) {
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
    boolean shouldMergeAdjacentChunks(FactCardSourceChunkView currentWindow, FactCardSourceChunkView nextChunk) {
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
    List<FactCardRecord> generateForChunk(FactCardSourceChunkView chunk) {
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

    FactCardGenerationSummary summarize(List<FactCardRecord> factCardRecords) {
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
}
