package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.FactCardJdbcRepository;
import com.xbk.lattice.infra.persistence.FactCardRecord;
import com.xbk.lattice.infra.persistence.FactCardVectorJdbcRepository;
import com.xbk.lattice.infra.persistence.FactCardVectorRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 事实证据卡向量索引服务
 *
 * 职责：为 fact_cards 生成、更新并持久化 embedding 索引
 *
 * @author xiexu
 */
@Slf4j
@Service
public class FactCardVectorIndexService {

    private static final Pattern VECTOR_DIMENSIONS_PATTERN = Pattern.compile(".*vector\\((\\d+)\\)$");

    private final QuerySearchProperties querySearchProperties;

    private final SearchCapabilityService searchCapabilityService;

    private final FactCardJdbcRepository factCardJdbcRepository;

    private final FactCardVectorJdbcRepository factCardVectorJdbcRepository;

    private final ConfiguredVectorEmbeddingService configuredVectorEmbeddingService;

    /**
     * 创建事实证据卡向量索引服务。
     *
     * @param querySearchProperties 查询检索配置
     * @param searchCapabilityService 检索能力探测服务
     * @param factCardJdbcRepository 事实证据卡仓储
     * @param factCardVectorJdbcRepository 事实证据卡向量仓储
     * @param configuredVectorEmbeddingService 可配置 embedding 服务
     */
    public FactCardVectorIndexService(
            QuerySearchProperties querySearchProperties,
            SearchCapabilityService searchCapabilityService,
            FactCardJdbcRepository factCardJdbcRepository,
            FactCardVectorJdbcRepository factCardVectorJdbcRepository,
            ConfiguredVectorEmbeddingService configuredVectorEmbeddingService
    ) {
        this.querySearchProperties = querySearchProperties == null ? new QuerySearchProperties() : querySearchProperties;
        this.searchCapabilityService = searchCapabilityService == null
                ? SearchCapabilityService.disabled()
                : searchCapabilityService;
        this.factCardJdbcRepository = factCardJdbcRepository;
        this.factCardVectorJdbcRepository = factCardVectorJdbcRepository;
        this.configuredVectorEmbeddingService = configuredVectorEmbeddingService;
    }

    /**
     * 创建默认禁用的事实证据卡向量索引服务。
     */
    public FactCardVectorIndexService() {
        this(new QuerySearchProperties(), SearchCapabilityService.disabled(), null, null, null);
    }

    /**
     * 重建全部 fact card 向量索引。
     */
    public void rebuildAll() {
        if (!isIndexingAvailable() || factCardJdbcRepository == null) {
            return;
        }
        indexFactCards(factCardJdbcRepository.findAll());
    }

    /**
     * 批量刷新 fact card 向量索引。
     *
     * @param factCardRecords 事实证据卡列表
     */
    public void indexFactCards(List<FactCardRecord> factCardRecords) {
        if (factCardRecords == null || factCardRecords.isEmpty()) {
            return;
        }
        for (FactCardRecord factCardRecord : factCardRecords) {
            indexFactCard(factCardRecord);
        }
    }

    /**
     * 为单张 fact card 更新向量索引。
     *
     * @param factCardRecord 事实证据卡
     */
    public void indexFactCard(FactCardRecord factCardRecord) {
        if (factCardRecord == null || factCardRecord.getId() == null || !isIndexingAvailable()) {
            return;
        }
        alignSchemaDimensionsIfNecessary();

        String embeddingText = buildEmbeddingText(factCardRecord);
        if (embeddingText.isBlank()) {
            return;
        }
        String contentHash = buildContentHash(embeddingText);
        Long configuredProfileId = getConfiguredModelProfileId();
        int configuredExpectedDimensions = configuredVectorEmbeddingService.getConfiguredExpectedDimensions();
        String configuredIndexVersion = buildIndexVersion(configuredProfileId, configuredExpectedDimensions);
        Optional<FactCardVectorRecord> existingRecord =
                factCardVectorJdbcRepository.findByFactCardId(factCardRecord.getId());
        if (shouldSkipIndexing(
                existingRecord,
                contentHash,
                configuredProfileId,
                configuredExpectedDimensions,
                configuredIndexVersion
        )) {
            return;
        }

        try {
            long startedAt = System.currentTimeMillis();
            float[] embedding = configuredVectorEmbeddingService.embed(embeddingText);
            if (!hasExpectedDimensions(embedding, factCardRecord.getCardId())) {
                return;
            }
            factCardVectorJdbcRepository.upsert(new FactCardVectorRecord(
                    factCardRecord.getId(),
                    factCardRecord.getCardId(),
                    factCardRecord.getCardType(),
                    factCardRecord.getAnswerShape(),
                    configuredProfileId,
                    configuredExpectedDimensions,
                    configuredIndexVersion,
                    contentHash,
                    embedding,
                    OffsetDateTime.now()
            ));
            log.info(
                    "[VECTOR][FACT_CARD_INDEX] cardId={}, profileId={}, dimensions={}, latencyMs={}, success=true",
                    factCardRecord.getCardId(),
                    configuredProfileId,
                    configuredExpectedDimensions,
                    System.currentTimeMillis() - startedAt
            );
        }
        catch (RuntimeException ex) {
            log.warn(
                    "[VECTOR][FACT_CARD_INDEX] cardId={}, profileId={}, success=false",
                    factCardRecord.getCardId(),
                    configuredProfileId,
                    ex
            );
        }
    }

    /**
     * 返回当前是否可执行 fact card 向量索引。
     *
     * @return 是否可执行
     */
    public boolean isIndexingAvailable() {
        return querySearchProperties.getVector().isEnabled()
                && configuredVectorEmbeddingService != null
                && configuredVectorEmbeddingService.isAvailable()
                && factCardVectorJdbcRepository != null
                && searchCapabilityService.supportsVectorType()
                && searchCapabilityService.hasFactCardVectorIndex();
    }

    /**
     * 返回当前配置的 embedding profile 主键。
     *
     * @return embedding profile 主键
     */
    public Long getConfiguredModelProfileId() {
        return configuredVectorEmbeddingService == null ? null : configuredVectorEmbeddingService.getConfiguredProfileId();
    }

    /**
     * 在首次写入前把空表 schema 自动对齐到当前 embedding profile 维度。
     */
    private void alignSchemaDimensionsIfNecessary() {
        int expectedDimensions = configuredVectorEmbeddingService == null
                ? 0
                : configuredVectorEmbeddingService.getConfiguredExpectedDimensions();
        if (expectedDimensions <= 0 || factCardVectorJdbcRepository == null) {
            return;
        }
        if (factCardVectorJdbcRepository.countAll() > 0) {
            return;
        }
        Integer schemaDimensions = extractSchemaDimensions(
                factCardVectorJdbcRepository.findEmbeddingColumnType().orElse("")
        ).orElse(null);
        if (schemaDimensions == null || schemaDimensions.intValue() == expectedDimensions) {
            return;
        }
        factCardVectorJdbcRepository.alignEmbeddingColumnDimensions(expectedDimensions);
        log.info(
                "Aligned fact card vector schema dimensions before indexing. schemaDimensions: {}, targetDimensions: {}",
                schemaDimensions,
                expectedDimensions
        );
    }

    /**
     * 判断当前 fact card 是否可直接跳过向量重建。
     *
     * @param existingRecord 已有向量记录
     * @param contentHash 最新内容哈希
     * @param configuredProfileId 当前配置模型主键
     * @param configuredExpectedDimensions 当前配置维度
     * @param configuredIndexVersion 当前索引版本
     * @return 是否跳过
     */
    private boolean shouldSkipIndexing(
            Optional<FactCardVectorRecord> existingRecord,
            String contentHash,
            Long configuredProfileId,
            int configuredExpectedDimensions,
            String configuredIndexVersion
    ) {
        if (existingRecord.isEmpty()) {
            return false;
        }
        FactCardVectorRecord currentRecord = existingRecord.orElseThrow();
        return contentHash.equals(currentRecord.getContentHash())
                && Objects.equals(configuredProfileId, currentRecord.getModelProfileId())
                && configuredExpectedDimensions == currentRecord.getEmbeddingDimensions()
                && configuredIndexVersion.equals(currentRecord.getIndexVersion());
    }

    /**
     * 校验 embedding 维度是否与当前索引基线一致。
     *
     * @param embedding embedding 向量
     * @param cardId fact card 稳定标识
     * @return 是否匹配
     */
    private boolean hasExpectedDimensions(float[] embedding, String cardId) {
        int expectedDimensions = configuredVectorEmbeddingService.getConfiguredExpectedDimensions();
        if (expectedDimensions <= 0) {
            return embedding != null && embedding.length > 0;
        }
        if (embedding != null && embedding.length == expectedDimensions) {
            return true;
        }
        int actualDimensions = embedding == null ? 0 : embedding.length;
        log.warn(
                "Fact card vector indexing skipped for card: {} because embedding dimensions {} do not match expected {}",
                cardId,
                actualDimensions,
                expectedDimensions
        );
        return false;
    }

    /**
     * 构建 fact card embedding 文本。
     *
     * @param factCardRecord 事实证据卡
     * @return embedding 文本
     */
    private String buildEmbeddingText(FactCardRecord factCardRecord) {
        return String.join(
                "\n",
                safeText(factCardRecord.getTitle()),
                safeText(factCardRecord.getClaim()),
                safeText(factCardRecord.getItemsJson()),
                safeText(factCardRecord.getEvidenceText())
        ).trim();
    }

    /**
     * 构建内容哈希。
     *
     * @param embeddingText embedding 文本
     * @return 内容哈希
     */
    private String buildContentHash(String embeddingText) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digestBytes = messageDigest.digest(embeddingText.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digestBytes);
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("sha-256 is not available", ex);
        }
    }

    /**
     * 构建向量索引版本。
     *
     * @param profileId profile 主键
     * @param expectedDimensions 期望维度
     * @return 索引版本
     */
    private String buildIndexVersion(Long profileId, int expectedDimensions) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(profileId == null ? "unknown" : profileId);
        stringBuilder.append("-");
        stringBuilder.append(expectedDimensions);
        stringBuilder.append("-fact-card-v1");
        return stringBuilder.toString();
    }

    private Optional<Integer> extractSchemaDimensions(String embeddingColumnType) {
        if (embeddingColumnType == null || embeddingColumnType.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = VECTOR_DIMENSIONS_PATTERN.matcher(embeddingColumnType.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(Integer.valueOf(Integer.parseInt(matcher.group(1))));
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
}
