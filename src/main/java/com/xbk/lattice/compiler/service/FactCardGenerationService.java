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
 * 事实证据卡生成服务
 *
 * 职责：协调 source chunk 读取、事实卡生成和 fact_cards 持久化
 *
 * @author xiexu
 */
@Service
public class FactCardGenerationService extends FactCardGenerationWindowSupport {

private final FactCardGenerationMapper factCardGenerationMapper;

    private final FactCardJdbcRepository factCardJdbcRepository;

    /**
     * 创建事实证据卡生成服务。
     *
     * @param factCardGenerationMapper 事实卡生成 Mapper
     * @param factCardJdbcRepository 事实证据卡仓储
     */
    public FactCardGenerationService(
            FactCardGenerationMapper factCardGenerationMapper,
            FactCardJdbcRepository factCardJdbcRepository
    ) {
        this.factCardGenerationMapper = factCardGenerationMapper;
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
        if (sourceFileId == null || factCardGenerationMapper == null || factCardJdbcRepository == null) {
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
        if (sourceFileId == null || factCardGenerationMapper == null) {
            return List.of();
        }
        List<FactCardSourceChunkView> chunks = findChunksBySourceFileId(sourceFileId);
        List<FactCardSourceChunkView> evidenceWindows = buildEvidenceWindows(chunks);
        List<FactCardRecord> factCardRecords = new ArrayList<FactCardRecord>();
        for (FactCardSourceChunkView evidenceWindow : evidenceWindows) {
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
    private List<FactCardSourceChunkView> findChunksBySourceFileId(Long sourceFileId) {
        return factCardGenerationMapper.findChunksBySourceFileId(sourceFileId);
    }
}
