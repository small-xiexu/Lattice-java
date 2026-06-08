package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.FactCardTerminalUnitMapper;
import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;
import com.xbk.lattice.query.evidence.domain.FactCardType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 事实证据卡终端字段证据单元 JDBC 仓储
 *
 * 职责：提供 fact_card_terminal_units 的持久化、幂等重建与 lexical 检索能力
 *
 * @author xiexu
 */
@Repository
public class FactCardTerminalUnitJdbcRepository {

    private final FactCardTerminalUnitMapper factCardTerminalUnitMapper;

    /**
     * 创建终端字段证据单元 JDBC 仓储。
     *
     * @param factCardTerminalUnitMapper 终端字段证据单元 Mapper
     */
    @Autowired
    public FactCardTerminalUnitJdbcRepository(FactCardTerminalUnitMapper factCardTerminalUnitMapper) {
        this.factCardTerminalUnitMapper = factCardTerminalUnitMapper;
    }

    /**
     * 保存或更新终端字段证据单元。
     *
     * @param record 终端字段证据单元记录
     * @return 入库后的终端字段证据单元记录
     */
    public FactCardTerminalUnitRecord upsert(FactCardTerminalUnitRecord record) {
        validateRecord(record);
        if (!tableAvailable()) {
            return record;
        }
        FactCardTerminalUnitRecord savedRecord = factCardTerminalUnitMapper.upsert(normalizedRecord(record));
        if (savedRecord == null) {
            throw new IllegalStateException("fact card terminal unit upsert returned no row");
        }
        return savedRecord;
    }

    /**
     * 批量保存或更新终端字段证据单元。
     *
     * @param records 终端字段证据单元记录列表
     * @return 入库后的终端字段证据单元记录列表
     */
    public List<FactCardTerminalUnitRecord> upsertAll(List<FactCardTerminalUnitRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return records.stream().map(this::upsert).toList();
    }

    /**
     * 按稳定业务标识查询终端字段证据单元。
     *
     * @param unitId 稳定业务标识
     * @return 终端字段证据单元记录
     */
    public Optional<FactCardTerminalUnitRecord> findByUnitId(String unitId) {
        if (!tableAvailable() || !hasText(unitId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(factCardTerminalUnitMapper.findByUnitId(unitId));
    }

    /**
     * 按事实卡主键查询终端字段证据单元。
     *
     * @param factCardId 事实卡主键
     * @return 终端字段证据单元列表
     */
    public List<FactCardTerminalUnitRecord> findByFactCardId(Long factCardId) {
        if (!tableAvailable() || factCardId == null) {
            return List.of();
        }
        return factCardTerminalUnitMapper.findByFactCardId(factCardId);
    }

    /**
     * 按源文件主键查询终端字段证据单元。
     *
     * @param sourceFileId 源文件主键
     * @return 终端字段证据单元列表
     */
    public List<FactCardTerminalUnitRecord> findBySourceFileId(Long sourceFileId) {
        if (!tableAvailable() || sourceFileId == null) {
            return List.of();
        }
        return factCardTerminalUnitMapper.findBySourceFileId(sourceFileId);
    }

    /**
     * 按事实卡主键删除终端字段证据单元。
     *
     * @param factCardId 事实卡主键
     * @return 删除数量
     */
    public int deleteByFactCardId(Long factCardId) {
        if (!tableAvailable() || factCardId == null) {
            return 0;
        }
        return factCardTerminalUnitMapper.deleteByFactCardId(factCardId);
    }

    /**
     * 按源文件主键删除终端字段证据单元。
     *
     * @param sourceFileId 源文件主键
     * @return 删除数量
     */
    public int deleteBySourceFileId(Long sourceFileId) {
        if (!tableAvailable() || sourceFileId == null) {
            return 0;
        }
        return factCardTerminalUnitMapper.deleteBySourceFileId(sourceFileId);
    }

    /**
     * 删除全部终端字段证据单元。
     *
     * @return 删除数量
     */
    public int deleteAll() {
        if (!tableAvailable()) {
            return 0;
        }
        return factCardTerminalUnitMapper.deleteAll();
    }

    /**
     * 统计全部终端字段证据单元。
     *
     * @return 统计数量
     */
    public int countAll() {
        if (!tableAvailable()) {
            return 0;
        }
        return factCardTerminalUnitMapper.countAll();
    }

    /**
     * 执行 terminal unit 数据库侧 lexical 检索。
     *
     * @param question 查询问题
     * @param queryTokens 查询 token
     * @param limit 返回数量
     * @param tsConfig FTS 配置
     * @return lexical 命中记录
     */
    public List<LexicalSearchRecord> searchLexical(
            String question,
            List<String> queryTokens,
            int limit,
            String tsConfig
    ) {
        if (!tableAvailable()) {
            return List.of();
        }
        List<String> normalizedTokens = LexicalSearchTokenBudget.normalize(queryTokens);
        if (!hasText(question) && normalizedTokens.isEmpty()) {
            return List.of();
        }
        List<String> likeTokens = LexicalSearchTokenBudget.selectLikeTokens(normalizedTokens);
        List<String> likePatterns = likeTokens.stream()
                .map(this::likePattern)
                .toList();
        String ftsQueryText = LexicalSearchTokenBudget.buildFtsQueryText(question, likeTokens);
        return factCardTerminalUnitMapper.searchLexical(
                normalizeTsConfig(tsConfig),
                ftsQueryText,
                likePatterns,
                safeLimit(limit)
        );
    }

    /**
     * 判断 terminal unit 表是否可用。
     *
     * @return 表可用返回 true
     */
    public boolean tableAvailable() {
        if (factCardTerminalUnitMapper == null) {
            return false;
        }
        try {
            return factCardTerminalUnitMapper.tableExists();
        }
        catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * 校验终端字段证据单元必填字段。
     *
     * @param record 终端字段证据单元记录
     */
    private void validateRecord(FactCardTerminalUnitRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("fact card terminal unit record must not be null");
        }
        if (!hasText(record.getUnitId())) {
            throw new IllegalArgumentException("fact card terminal unit unitId must not be blank");
        }
        if (!hasText(record.getTerminalUnitIdentity())) {
            throw new IllegalArgumentException("fact card terminal unit identity must not be blank");
        }
        if (record.getFactCardId() == null) {
            throw new IllegalArgumentException("fact card terminal unit factCardId must not be null");
        }
        if (!hasText(record.getCardId())) {
            throw new IllegalArgumentException("fact card terminal unit cardId must not be blank");
        }
        if (record.getCardType() == null) {
            throw new IllegalArgumentException("fact card terminal unit cardType must not be null");
        }
        if (record.getAnswerShape() == null) {
            throw new IllegalArgumentException("fact card terminal unit answerShape must not be null");
        }
        if (!hasText(record.getContentHash())) {
            throw new IllegalArgumentException("fact card terminal unit contentHash must not be blank");
        }
    }

    /**
     * 规范化终端字段证据单元记录。
     *
     * @param record 原始记录
     * @return 规范化记录
     */
    private FactCardTerminalUnitRecord normalizedRecord(FactCardTerminalUnitRecord record) {
        FactCardReviewStatus reviewStatus = record.getReviewStatus() == null
                ? FactCardReviewStatus.LOW_CONFIDENCE
                : record.getReviewStatus();
        FactCardType cardType = record.getCardType();
        AnswerShape answerShape = record.getAnswerShape();
        return new FactCardTerminalUnitRecord(
                record.getId(),
                record.getUnitId(),
                record.getTerminalUnitIdentity(),
                record.getFactCardId(),
                record.getCardId(),
                record.getSourceId(),
                record.getSourceFileId(),
                record.getSourceChunkIds(),
                record.getArticleIds(),
                cardType,
                answerShape,
                safeText(record.getStructure()),
                record.getItemIndex(),
                safeText(record.getKeyPath()),
                safeText(record.getParentPath()),
                safeText(record.getTerminalKey()),
                jsonArrayOrDefault(record.getPathSegmentsJson()),
                safeText(record.getFieldLabel()),
                jsonArrayOrDefault(record.getFieldAliasesJson()),
                safeText(record.getFieldDescription()),
                safeText(record.getDisplayText()),
                safeText(record.getValueText()),
                safeText(record.getNormalizedValue()),
                hasText(record.getValueType()) ? record.getValueType() : "string",
                jsonObjectOrDefault(record.getSourceRefsJson()),
                safeText(record.getFtsText()),
                jsonObjectOrDefault(record.getMetadataJson()),
                reviewStatus,
                record.getConfidence(),
                record.getContentHash(),
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }

    /**
     * 规范化 FTS 配置。
     *
     * @param tsConfig FTS 配置
     * @return FTS 配置
     */
    private String normalizeTsConfig(String tsConfig) {
        return hasText(tsConfig) ? tsConfig.trim() : "simple";
    }

    /**
     * 计算安全返回数量。
     *
     * @param limit 原始数量
     * @return 安全数量
     */
    private int safeLimit(int limit) {
        return limit <= 0 ? 5 : limit;
    }

    /**
     * 构造 LIKE 匹配模式。
     *
     * @param queryToken 查询 token
     * @return LIKE 模式
     */
    private String likePattern(String queryToken) {
        return "%" + escapeLikePattern(queryToken) + "%";
    }

    /**
     * 转义 LIKE 模式。
     *
     * @param value 原始值
     * @return 转义后文本
     */
    private String escapeLikePattern(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    /**
     * 返回 JSON 数组或空数组。
     *
     * @param value JSON 文本
     * @return JSON 数组文本
     */
    private String jsonArrayOrDefault(String value) {
        return hasText(value) ? value : "[]";
    }

    /**
     * 返回 JSON 对象或空对象。
     *
     * @param value JSON 文本
     * @return JSON 对象文本
     */
    private String jsonObjectOrDefault(String value) {
        return hasText(value) ? value : "{}";
    }

    /**
     * 返回空安全文本。
     *
     * @param value 原始文本
     * @return 空安全文本
     */
    private String safeText(String value) {
        return value == null ? "" : value;
    }

    /**
     * 判断文本是否有内容。
     *
     * @param value 文本
     * @return 是否有内容
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
