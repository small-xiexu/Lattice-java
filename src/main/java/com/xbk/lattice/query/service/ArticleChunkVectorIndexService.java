package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.ArticleChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleChunkRecord;
import com.xbk.lattice.infra.persistence.ArticleChunkVectorJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleChunkVectorRecord;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 文章分块向量索引服务
 *
 * 职责：为 article_chunks 生成、更新并持久化 chunk 级 embedding 索引
 *
 * @author xiexu
 */
@Slf4j
@Service
public class ArticleChunkVectorIndexService {

    private static final Pattern VECTOR_DIMENSIONS_PATTERN = Pattern.compile(".*vector\\((\\d+)\\)$");

    private final QuerySearchProperties querySearchProperties;

    private final SearchCapabilityService searchCapabilityService;

    private final ArticleChunkJdbcRepository articleChunkJdbcRepository;

    private final ArticleChunkVectorJdbcRepository articleChunkVectorJdbcRepository;

    private final ConfiguredVectorEmbeddingService configuredVectorEmbeddingService;

    /**
     * 创建文章分块向量索引服务。
     *
     * @param querySearchProperties 查询检索配置
     * @param searchCapabilityService 检索能力探测服务
     * @param articleChunkJdbcRepository 文章分块仓储
     * @param articleChunkVectorJdbcRepository 分块向量仓储
     * @param configuredVectorEmbeddingService 可配置 embedding 服务
     */
    @Autowired
    public ArticleChunkVectorIndexService(
            QuerySearchProperties querySearchProperties,
            SearchCapabilityService searchCapabilityService,
            ArticleChunkJdbcRepository articleChunkJdbcRepository,
            ArticleChunkVectorJdbcRepository articleChunkVectorJdbcRepository,
            ConfiguredVectorEmbeddingService configuredVectorEmbeddingService
    ) {
        this.querySearchProperties = querySearchProperties;
        this.searchCapabilityService = searchCapabilityService;
        this.articleChunkJdbcRepository = articleChunkJdbcRepository;
        this.articleChunkVectorJdbcRepository = articleChunkVectorJdbcRepository;
        this.configuredVectorEmbeddingService = configuredVectorEmbeddingService;
    }

    /**
     * 创建默认禁用的分块向量索引服务。
     */
    public ArticleChunkVectorIndexService() {
        this(new QuerySearchProperties(), SearchCapabilityService.disabled(), null, null, null);
    }

    /**
     * 为单篇文章刷新 chunk 向量索引。
     *
     * @param articleRecord 文章记录
     */
    public void indexArticle(ArticleRecord articleRecord) {
        if (articleRecord == null || !isIndexingAvailable() || articleChunkJdbcRepository == null) {
            return;
        }
        alignSchemaDimensionsIfNecessary();
        List<ArticleChunkRecord> chunkRecords = hasText(articleRecord.getArticleKey())
                ? articleChunkJdbcRepository.findByArticleKey(articleRecord.getArticleKey())
                : articleChunkJdbcRepository.findByConceptId(articleRecord.getConceptId());
        if (chunkRecords.isEmpty()) {
            return;
        }
        Long configuredProfileId = getConfiguredModelProfileId();
        for (ArticleChunkRecord chunkRecord : chunkRecords) {
            String contentHash = buildContentHash(chunkRecord.getChunkText());
            Optional<ArticleChunkVectorRecord> existingRecord = articleChunkVectorJdbcRepository.findByArticleChunkId(chunkRecord.getId());
            if (shouldSkipIndexing(existingRecord, contentHash, configuredProfileId)) {
                continue;
            }
            try {
                long startedAt = System.currentTimeMillis();
                float[] embedding = configuredVectorEmbeddingService.embed(chunkRecord.getChunkText());
                if (!hasExpectedDimensions(embedding, chunkRecord.getConceptId())) {
                    continue;
                }
                articleChunkVectorJdbcRepository.upsert(new ArticleChunkVectorRecord(
                        chunkRecord.getId(),
                        chunkRecord.getArticleId(),
                        chunkRecord.getConceptId(),
                        chunkRecord.getChunkIndex(),
                        configuredProfileId,
                        contentHash,
                        embedding,
                        OffsetDateTime.now()
                ));
                log.info(
                        "[VECTOR][CHUNK_INDEX] conceptId={}, articleChunkId={}, chunkIndex={}, profileId={}, latencyMs={}, success=true",
                        chunkRecord.getConceptId(),
                        chunkRecord.getId(),
                        chunkRecord.getChunkIndex(),
                        configuredProfileId,
                        System.currentTimeMillis() - startedAt
                );
            }
            catch (RuntimeException ex) {
                log.warn(
                        "[VECTOR][CHUNK_INDEX] conceptId={}, articleChunkId={}, chunkIndex={}, profileId={}, success=false",
                        chunkRecord.getConceptId(),
                        chunkRecord.getId(),
                        chunkRecord.getChunkIndex(),
                        configuredProfileId,
                        ex
                );
            }
        }
    }

    /**
     * 批量刷新 chunk 向量索引。
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
     * 返回当前是否可执行 chunk 向量索引。
     *
     * @return 是否可执行
     */
    public boolean isIndexingAvailable() {
        return querySearchProperties.getVector().isEnabled()
                && configuredVectorEmbeddingService != null
                && configuredVectorEmbeddingService.isAvailable()
                && articleChunkVectorJdbcRepository != null
                && searchCapabilityService.supportsVectorType();
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
        if (expectedDimensions <= 0 || articleChunkVectorJdbcRepository == null) {
            return;
        }
        if (articleChunkVectorJdbcRepository.countAll() > 0) {
            return;
        }
        Integer schemaDimensions = extractSchemaDimensions(
                articleChunkVectorJdbcRepository.findEmbeddingColumnType().orElse("")
        ).orElse(null);
        if (schemaDimensions == null || schemaDimensions.intValue() == expectedDimensions) {
            return;
        }
        articleChunkVectorJdbcRepository.alignEmbeddingColumnDimensions(expectedDimensions);
        log.info(
                "Aligned article chunk vector schema dimensions before compile indexing. schemaDimensions: {}, targetDimensions: {}",
                schemaDimensions,
                expectedDimensions
        );
    }

    private boolean shouldSkipIndexing(
            Optional<ArticleChunkVectorRecord> existingRecord,
            String contentHash,
            Long configuredProfileId
    ) {
        if (existingRecord.isEmpty()) {
            return false;
        }
        ArticleChunkVectorRecord currentRecord = existingRecord.orElseThrow();
        return contentHash.equals(currentRecord.getContentHash())
                && Objects.equals(configuredProfileId, currentRecord.getModelProfileId());
    }

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
                "Chunk vector indexing skipped for article: {} because embedding dimensions {} do not match expected {}",
                conceptId,
                actualDimensions,
                expectedDimensions
        );
        return false;
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

    private String buildContentHash(String content) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digestBytes = messageDigest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digestBytes);
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("sha-256 is not available", ex);
        }
    }

    /**
     * 判断文本是否有效。
     *
     * @param value 文本
     * @return 是否有效
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
