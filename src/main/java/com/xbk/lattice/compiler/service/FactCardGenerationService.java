package com.xbk.lattice.compiler.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xbk.lattice.compiler.service.mapper.FactCardGenerationMapper;
import com.xbk.lattice.infra.persistence.FactCardJdbcRepository;
import com.xbk.lattice.infra.persistence.FactCardRecord;
import com.xbk.lattice.infra.persistence.FactCardTerminalUnitJdbcRepository;
import com.xbk.lattice.infra.persistence.FactCardTerminalUnitRecord;
import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;
import com.xbk.lattice.query.evidence.domain.FactCardType;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final FactCardTerminalUnitJdbcRepository factCardTerminalUnitJdbcRepository;

    private final FactCardTerminalUnitMaterializer factCardTerminalUnitMaterializer;

    private final FactCardTerminalUnitFieldAliasEnricher fieldAliasEnricher;

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
        this(
                factCardGenerationMapper,
                factCardJdbcRepository,
                null,
                new FactCardTerminalUnitMaterializer(),
                null
        );
    }

    /**
     * 创建事实证据卡生成服务。
     *
     * @param factCardGenerationMapper 事实卡生成 Mapper
     * @param factCardJdbcRepository 事实证据卡仓储
     * @param factCardTerminalUnitJdbcRepository terminal unit 仓储
     * @param factCardTerminalUnitMaterializer terminal unit 物化器
     */
    @Autowired
    public FactCardGenerationService(
            FactCardGenerationMapper factCardGenerationMapper,
            FactCardJdbcRepository factCardJdbcRepository,
            FactCardTerminalUnitJdbcRepository factCardTerminalUnitJdbcRepository,
            FactCardTerminalUnitMaterializer factCardTerminalUnitMaterializer,
            @Autowired(required = false) FactCardTerminalUnitFieldAliasEnricher fieldAliasEnricher
    ) {
        this.factCardGenerationMapper = factCardGenerationMapper;
        this.factCardJdbcRepository = factCardJdbcRepository;
        this.factCardTerminalUnitJdbcRepository = factCardTerminalUnitJdbcRepository;
        this.factCardTerminalUnitMaterializer = factCardTerminalUnitMaterializer == null
                ? new FactCardTerminalUnitMaterializer()
                : factCardTerminalUnitMaterializer;
        this.fieldAliasEnricher = fieldAliasEnricher;
    }

    /**
     * 重建指定源文件的全部事实证据卡。
     *
     * @param sourceFileId 源文件主键
     * @return 新生成并已持久化的事实证据卡
     */
    @Transactional(rollbackFor = Exception.class)
    public List<FactCardRecord> rebuildForSourceFile(Long sourceFileId) {
        return rebuildForSourceFile(sourceFileId, null);
    }

    /**
     * 在 compile job scope 下重建指定源文件的全部事实证据卡。
     *
     * @param sourceFileId 源文件主键
     * @param scopeId      compile job scope，null 表示无 scope
     * @return 新生成并已持久化的事实证据卡
     */
    @Transactional(rollbackFor = Exception.class)
    public List<FactCardRecord> rebuildForSourceFile(Long sourceFileId, String scopeId) {
        if (sourceFileId == null || factCardGenerationMapper == null || factCardJdbcRepository == null) {
            return List.of();
        }
        List<FactCardRecord> factCardRecords = generateForSourceFile(sourceFileId);
        deleteTerminalUnitsBySourceFileId(sourceFileId);
        factCardJdbcRepository.deleteBySourceFileId(sourceFileId);
        for (FactCardRecord factCardRecord : factCardRecords) {
            FactCardRecord savedFactCardRecord = factCardJdbcRepository.upsert(factCardRecord);
            materializeTerminalUnits(savedFactCardRecord, scopeId);
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

    /**
     * 删除指定源文件的 terminal unit。
     *
     * @param sourceFileId 源文件主键
     */
    private void deleteTerminalUnitsBySourceFileId(Long sourceFileId) {
        if (factCardTerminalUnitJdbcRepository == null) {
            return;
        }
        factCardTerminalUnitJdbcRepository.deleteBySourceFileId(sourceFileId);
    }

    /**
     * 为已保存事实卡物化 terminal unit。
     *
     * @param factCardRecord 已保存事实卡
     */
    private void materializeTerminalUnits(FactCardRecord factCardRecord) {
        materializeTerminalUnits(factCardRecord, null);
    }

    private void materializeTerminalUnits(FactCardRecord factCardRecord, String scopeId) {
        if (factCardTerminalUnitJdbcRepository == null || factCardTerminalUnitMaterializer == null) {
            return;
        }
        List<FactCardTerminalUnitRecord> terminalUnitRecords =
                factCardTerminalUnitMaterializer.materialize(factCardRecord);
        if (fieldAliasEnricher != null) {
            if (scopeId != null && !scopeId.isBlank()) {
                terminalUnitRecords = fieldAliasEnricher.enrich(terminalUnitRecords, factCardRecord, scopeId);
            } else {
                terminalUnitRecords = fieldAliasEnricher.enrich(terminalUnitRecords, factCardRecord);
            }
        }
        factCardTerminalUnitJdbcRepository.upsertAll(terminalUnitRecords);
    }
}
