package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.FactCardMapper;
import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;
import com.xbk.lattice.query.evidence.domain.FactCardType;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 事实证据卡 JDBC 仓储
 *
 * 职责：提供 fact_cards 表的结构化证据卡持久化与 lexical 检索能力
 *
 * @author xiexu
 */
@Repository
public class FactCardJdbcRepository {

    private final FactCardMapper factCardMapper;

    /**
     * 创建事实证据卡 JDBC 仓储。
     *
     * @param factCardMapper 事实证据卡 Mapper
     */
    public FactCardJdbcRepository(FactCardMapper factCardMapper) {
        this.factCardMapper = factCardMapper;
    }

    /**
     * 保存或更新事实证据卡。
     *
     * @param factCardRecord 事实证据卡记录
     * @return 入库后的事实证据卡记录
     */
    public FactCardRecord upsert(FactCardRecord factCardRecord) {
        validateRecord(factCardRecord);
        if (factCardMapper == null) {
            return factCardRecord;
        }

        String searchText = buildSearchText(factCardRecord);
        FactCardRecord savedRecord = factCardMapper.upsert(normalizedRecord(factCardRecord), searchText);
        if (savedRecord == null) {
            throw new IllegalStateException("fact card upsert returned no row");
        }
        return savedRecord;
    }

    /**
     * 按证据卡稳定标识查询事实证据卡。
     *
     * @param cardId 证据卡稳定标识
     * @return 事实证据卡记录
     */
    public Optional<FactCardRecord> findByCardId(String cardId) {
        if (factCardMapper == null || !hasText(cardId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(factCardMapper.findByCardId(cardId));
    }

    /**
     * 按事实证据卡主键查询。
     *
     * @param id 事实证据卡主键
     * @return 事实证据卡记录
     */
    public Optional<FactCardRecord> findById(Long id) {
        if (factCardMapper == null || id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(factCardMapper.findById(id));
    }

    /**
     * 查询全部事实证据卡。
     *
     * @return 事实证据卡记录列表
     */
    public List<FactCardRecord> findAll() {
        if (factCardMapper == null) {
            return List.of();
        }
        return factCardMapper.findAll();
    }

    /**
     * 按源文件主键查询事实证据卡。
     *
     * @param sourceFileId 源文件主键
     * @return 事实证据卡记录列表
     */
    public List<FactCardRecord> findBySourceFileId(Long sourceFileId) {
        if (factCardMapper == null || sourceFileId == null) {
            return List.of();
        }
        return factCardMapper.findBySourceFileId(sourceFileId);
    }

    /**
     * 按源文件主键删除事实证据卡。
     *
     * @param sourceFileId 源文件主键
     * @return 删除数量
     */
    public int deleteBySourceFileId(Long sourceFileId) {
        if (factCardMapper == null || sourceFileId == null) {
            return 0;
        }
        return factCardMapper.deleteBySourceFileId(sourceFileId);
    }

    /**
     * 清空全部事实证据卡。
     *
     * @return 删除数量
     */
    public int deleteAll() {
        if (factCardMapper == null) {
            return 0;
        }
        return factCardMapper.deleteAll();
    }

    /**
     * 统计事实证据卡总数。
     *
     * @return 事实证据卡总数
     */
    public int countAll() {
        if (factCardMapper == null) {
            return 0;
        }
        return factCardMapper.countAll();
    }

    /**
     * 按证据卡类型统计数量。
     *
     * @return 证据卡类型到数量的映射
     */
    public Map<FactCardType, Integer> countByCardType() {
        Map<FactCardType, Integer> counts = new LinkedHashMap<FactCardType, Integer>();
        if (factCardMapper == null) {
            return counts;
        }
        for (FactCardCountRow row : factCardMapper.countByCardType()) {
            counts.put(FactCardType.fromValue(row.getCountKey()), Integer.valueOf(row.getCardCount()));
        }
        return counts;
    }

    /**
     * 按审查状态统计数量。
     *
     * @return 审查状态到数量的映射
     */
    public Map<FactCardReviewStatus, Integer> countByReviewStatus() {
        Map<FactCardReviewStatus, Integer> counts = new LinkedHashMap<FactCardReviewStatus, Integer>();
        if (factCardMapper == null) {
            return counts;
        }
        for (FactCardCountRow row : factCardMapper.countByReviewStatus()) {
            counts.put(FactCardReviewStatus.fromValue(row.getCountKey()), Integer.valueOf(row.getCardCount()));
        }
        return counts;
    }

    /**
     * 统计没有 source chunk 回指的事实证据卡数量。
     *
     * @return 无回指数量
     */
    public int countWithoutSourceChunks() {
        if (factCardMapper == null) {
            return 0;
        }
        return factCardMapper.countWithoutSourceChunks();
    }

    /**
     * 统计低置信事实证据卡数量。
     *
     * @return 低置信数量
     */
    public int countLowConfidence() {
        if (factCardMapper == null) {
            return 0;
        }
        return factCardMapper.countByReviewStatusValue(FactCardReviewStatus.LOW_CONFIDENCE.databaseValue());
    }

    /**
     * 执行 fact card 数据库侧 lexical 检索。
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
        if (factCardMapper == null) {
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
        return factCardMapper.searchLexical(
                normalizeTsConfig(tsConfig),
                question == null ? "" : question,
                likePatterns,
                safeLimit(limit)
        );
    }

    /**
     * 校验证据卡入库必填字段。
     *
     * @param factCardRecord 事实证据卡记录
     */
    private void validateRecord(FactCardRecord factCardRecord) {
        if (factCardRecord == null) {
            throw new IllegalArgumentException("factCardRecord must not be null");
        }
        if (!hasText(factCardRecord.getCardId())) {
            throw new IllegalArgumentException("fact card cardId must not be blank");
        }
        if (factCardRecord.getCardType() == null) {
            throw new IllegalArgumentException("fact card cardType must not be null");
        }
        if (factCardRecord.getAnswerShape() == null) {
            throw new IllegalArgumentException("fact card answerShape must not be null");
        }
        if (!hasText(factCardRecord.getTitle())) {
            throw new IllegalArgumentException("fact card title must not be blank");
        }
        if (!hasText(factCardRecord.getContentHash())) {
            throw new IllegalArgumentException("fact card contentHash must not be blank");
        }
    }

    /**
     * 构建事实证据卡检索文本。
     *
     * @param factCardRecord 事实证据卡记录
     * @return 检索文本
     */
    private String buildSearchText(FactCardRecord factCardRecord) {
        return String.join(
                " ",
                factCardRecord.getCardType().name(),
                factCardRecord.getAnswerShape().name(),
                safeText(factCardRecord.getTitle()),
                safeText(factCardRecord.getClaim()),
                safeText(factCardRecord.getItemsJson()),
                safeText(factCardRecord.getEvidenceText())
        ).trim();
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
     * 构建 LIKE 匹配模式。
     *
     * @param queryToken 查询 token
     * @return LIKE 匹配模式
     */
    private String likePattern(String queryToken) {
        return "%" + escapeLikePattern(queryToken) + "%";
    }

    /**
     * 转义 LIKE 模式中的通配符。
     *
     * @param value 原始值
     * @return 转义后的 LIKE 片段
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
     * 返回非空文本。
     *
     * @param value 原始文本
     * @return 非空文本
     */
    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private FactCardRecord normalizedRecord(FactCardRecord factCardRecord) {
        FactCardReviewStatus reviewStatus = factCardRecord.getReviewStatus() == null
                ? FactCardReviewStatus.LOW_CONFIDENCE
                : factCardRecord.getReviewStatus();
        return new FactCardRecord(
                factCardRecord.getId(),
                factCardRecord.getCardId(),
                factCardRecord.getSourceId(),
                factCardRecord.getSourceFileId(),
                factCardRecord.getCardType(),
                factCardRecord.getAnswerShape(),
                factCardRecord.getTitle(),
                safeText(factCardRecord.getClaim()),
                hasText(factCardRecord.getItemsJson()) ? factCardRecord.getItemsJson() : "{}",
                safeText(factCardRecord.getEvidenceText()),
                factCardRecord.getSourceChunkIds(),
                factCardRecord.getArticleIds(),
                factCardRecord.getConfidence(),
                reviewStatus,
                factCardRecord.getContentHash(),
                factCardRecord.getCreatedAt(),
                factCardRecord.getUpdatedAt()
        );
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
