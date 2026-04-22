package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ArticleVectorJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleVectorRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
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
 * 文章向量索引服务
 *
 * 职责：为已编译文章生成、更新并持久化 embedding 索引
 *
 * @author xiexu
 */
@Slf4j
@Service
@Profile("jdbc")
public class ArticleVectorIndexService {

    private static final Pattern VECTOR_DIMENSIONS_PATTERN = Pattern.compile(".*vector\\((\\d+)\\)$");

    private final QuerySearchProperties querySearchProperties;

    private final SearchCapabilityService searchCapabilityService;

    private final ArticleVectorJdbcRepository articleVectorJdbcRepository;

    private final ConfiguredVectorEmbeddingService configuredVectorEmbeddingService;

    /**
     * 创建文章向量索引服务。
     *
     * @param querySearchProperties 查询检索配置
     * @param searchCapabilityService 检索能力探测服务
     * @param articleVectorJdbcRepository 文章向量索引仓储
     * @param configuredVectorEmbeddingService 可配置 embedding 服务
     */
    @Autowired
    public ArticleVectorIndexService(
            QuerySearchProperties querySearchProperties,
            SearchCapabilityService searchCapabilityService,
            ArticleVectorJdbcRepository articleVectorJdbcRepository,
            ConfiguredVectorEmbeddingService configuredVectorEmbeddingService
    ) {
        this.querySearchProperties = querySearchProperties;
        this.searchCapabilityService = searchCapabilityService;
        this.articleVectorJdbcRepository = articleVectorJdbcRepository;
        this.configuredVectorEmbeddingService = configuredVectorEmbeddingService;
    }

    /**
     * 创建文章向量索引服务。
     *
     * @param querySearchProperties 查询检索配置
     * @param searchCapabilityService 检索能力探测服务
     * @param articleVectorJdbcRepository 文章向量索引仓储
     * @param embeddingModel embedding 模型
     */
    public ArticleVectorIndexService(
            QuerySearchProperties querySearchProperties,
            SearchCapabilityService searchCapabilityService,
            ArticleVectorJdbcRepository articleVectorJdbcRepository,
            EmbeddingModel embeddingModel
    ) {
        this(
                querySearchProperties,
                searchCapabilityService,
                articleVectorJdbcRepository,
                new ConfiguredVectorEmbeddingService(querySearchProperties, embeddingModel)
        );
    }

    /**
     * 创建默认禁用的文章向量索引服务。
     */
    public ArticleVectorIndexService() {
        this(new QuerySearchProperties(), SearchCapabilityService.disabled(), null, (EmbeddingModel) null);
    }

    /**
     * 为单篇文章更新向量索引。
     *
     * @param articleRecord 文章记录
     */
    public void indexArticle(ArticleRecord articleRecord) {
        if (articleRecord == null || !isIndexingAvailable()) {
            return;
        }
        alignSchemaDimensionsIfNecessary();

        String contentHash = buildContentHash(articleRecord);
        Long configuredProfileId = getConfiguredModelProfileId();
        String configuredModelName = getConfiguredModelName();
        int configuredExpectedDimensions = configuredVectorEmbeddingService.getConfiguredExpectedDimensions();
        String configuredIndexVersion = buildIndexVersion(configuredProfileId, configuredExpectedDimensions);
        Optional<ArticleVectorRecord> existingRecord = articleVectorJdbcRepository.findByArticleKey(articleRecord.getArticleKey());
        if (shouldSkipIndexing(
                existingRecord,
                contentHash,
                configuredProfileId,
                configuredModelName,
                configuredExpectedDimensions,
                configuredIndexVersion
        )) {
            return;
        }

        try {
            float[] embedding = configuredVectorEmbeddingService.embed(buildEmbeddingText(articleRecord));
            if (!hasExpectedDimensions(embedding, articleRecord.getConceptId())) {
                return;
            }
            articleVectorJdbcRepository.upsert(new ArticleVectorRecord(
                    articleRecord.getArticleKey(),
                    articleRecord.getConceptId(),
                    configuredProfileId,
                    configuredModelName,
                    configuredExpectedDimensions,
                    configuredIndexVersion,
                    contentHash,
                    embedding,
                    OffsetDateTime.now()
            ));
        }
        catch (RuntimeException ex) {
            log.warn("Vector indexing fallback for article: {}", articleRecord.getConceptId(), ex);
        }
    }

    /**
     * 批量更新文章向量索引。
     *
     * @param articleRecords 文章记录列表
     */
    public void indexArticles(List<ArticleRecord> articleRecords) {
        if (articleRecords == null || articleRecords.isEmpty()) {
            return;
        }
        for (ArticleRecord articleRecord : articleRecords) {
            indexArticle(articleRecord);
        }
    }

    /**
     * 返回当前是否可执行向量索引。
     *
     * @return 是否可执行
     */
    public boolean isIndexingAvailable() {
        return querySearchProperties.getVector().isEnabled()
                && articleVectorJdbcRepository != null
                && configuredVectorEmbeddingService != null
                && configuredVectorEmbeddingService.isAvailable()
                && searchCapabilityService.supportsVectorType()
                && searchCapabilityService.hasArticleVectorIndex();
    }

    /**
     * 返回当前配置的 embedding 模型名称。
     *
     * @return embedding 模型名称
     */
    public String getConfiguredModelName() {
        return configuredVectorEmbeddingService == null ? "" : configuredVectorEmbeddingService.getConfiguredModelName();
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
        if (expectedDimensions <= 0 || articleVectorJdbcRepository == null) {
            return;
        }
        if (articleVectorJdbcRepository.countAll() > 0) {
            return;
        }
        Integer schemaDimensions = extractSchemaDimensions(
                articleVectorJdbcRepository.findEmbeddingColumnType().orElse("")
        ).orElse(null);
        if (schemaDimensions == null || schemaDimensions.intValue() == expectedDimensions) {
            return;
        }
        articleVectorJdbcRepository.alignEmbeddingColumnDimensions(expectedDimensions);
        log.info(
                "Aligned article vector schema dimensions before compile indexing. schemaDimensions: {}, targetDimensions: {}",
                schemaDimensions,
                expectedDimensions
        );
    }

    /**
     * 判断当前文章是否可直接跳过向量重建。
     *
     * @param existingRecord 已有向量记录
     * @param contentHash 最新内容哈希
     * @param configuredProfileId 当前配置模型主键
     * @param configuredModelName 当前配置模型名
     * @param configuredExpectedDimensions 当前配置维度
     * @param configuredIndexVersion 当前索引版本
     * @return 是否跳过
     */
    private boolean shouldSkipIndexing(
            Optional<ArticleVectorRecord> existingRecord,
            String contentHash,
            Long configuredProfileId,
            String configuredModelName,
            int configuredExpectedDimensions,
            String configuredIndexVersion
    ) {
        if (existingRecord.isEmpty()) {
            return false;
        }
        ArticleVectorRecord currentRecord = existingRecord.orElseThrow();
        boolean modelIdentityMatches = configuredProfileId == null
                ? Objects.equals(configuredModelName, currentRecord.getModelName())
                : Objects.equals(configuredProfileId, currentRecord.getModelProfileId());
        return contentHash.equals(currentRecord.getContentHash())
                && modelIdentityMatches
                && configuredExpectedDimensions == currentRecord.getEmbeddingDimensions()
                && configuredIndexVersion.equals(currentRecord.getIndexVersion());
    }

    /**
     * 构建索引使用的 embedding 文本。
     *
     * @param articleRecord 文章记录
     * @return embedding 文本
     */
    private String buildEmbeddingText(ArticleRecord articleRecord) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(articleRecord.getTitle()).append('\n');
        if (articleRecord.getSummary() != null && !articleRecord.getSummary().isBlank()) {
            stringBuilder.append(articleRecord.getSummary()).append('\n');
        }
        stringBuilder.append(articleRecord.getContent());
        if (!articleRecord.getSourcePaths().isEmpty()) {
            stringBuilder.append('\n').append(String.join("\n", articleRecord.getSourcePaths()));
        }
        return stringBuilder.toString();
    }

    /**
     * 构建文章内容哈希。
     *
     * @param articleRecord 文章记录
     * @return 内容哈希
     */
    private String buildContentHash(ArticleRecord articleRecord) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digestBytes = messageDigest.digest(
                    buildEmbeddingText(articleRecord).getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(digestBytes);
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("sha-256 is not available", ex);
        }
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
     * 校验 embedding 维度是否与当前索引基线一致。
     *
     * @param embedding embedding 向量
     * @param conceptId 概念标识
     * @return 是否匹配
     */
    private boolean hasExpectedDimensions(float[] embedding, String conceptId) {
        int expectedDimensions = configuredVectorEmbeddingService.getConfiguredExpectedDimensions();
        if (expectedDimensions <= 0) {
            return true;
        }
        if (embedding != null && embedding.length == expectedDimensions) {
            return true;
        }

        int actualDimensions = embedding == null ? 0 : embedding.length;
        log.warn(
                "Vector indexing skipped for article: {} because embedding dimensions {} do not match expected {}",
                conceptId,
                actualDimensions,
                expectedDimensions
        );
        return false;
    }

    /**
     * 构建文章级向量索引版本。
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
        stringBuilder.append("-article-v1");
        return stringBuilder.toString();
    }
}
