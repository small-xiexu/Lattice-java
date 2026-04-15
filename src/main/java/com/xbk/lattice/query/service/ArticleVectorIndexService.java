package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ArticleVectorJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleVectorRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

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

    private final QuerySearchProperties querySearchProperties;

    private final SearchCapabilityService searchCapabilityService;

    private final ArticleVectorJdbcRepository articleVectorJdbcRepository;

    private final EmbeddingModel embeddingModel;

    /**
     * 创建文章向量索引服务。
     *
     * @param querySearchProperties 查询检索配置
     * @param searchCapabilityService 检索能力探测服务
     * @param articleVectorJdbcRepository 文章向量索引仓储
     * @param embeddingModelProvider embedding 模型提供器
     */
    @Autowired
    public ArticleVectorIndexService(
            QuerySearchProperties querySearchProperties,
            SearchCapabilityService searchCapabilityService,
            ArticleVectorJdbcRepository articleVectorJdbcRepository,
            ObjectProvider<EmbeddingModel> embeddingModelProvider
    ) {
        this(
                querySearchProperties,
                searchCapabilityService,
                articleVectorJdbcRepository,
                embeddingModelProvider.getIfAvailable()
        );
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
        this.querySearchProperties = querySearchProperties;
        this.searchCapabilityService = searchCapabilityService;
        this.articleVectorJdbcRepository = articleVectorJdbcRepository;
        this.embeddingModel = embeddingModel;
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

        String contentHash = buildContentHash(articleRecord);
        Optional<ArticleVectorRecord> existingRecord = articleVectorJdbcRepository.findByConceptId(articleRecord.getConceptId());
        if (existingRecord.isPresent() && contentHash.equals(existingRecord.orElseThrow().getContentHash())) {
            return;
        }

        try {
            float[] embedding = embeddingModel.embed(buildEmbeddingText(articleRecord));
            if (!hasExpectedDimensions(embedding, articleRecord.getConceptId())) {
                return;
            }
            articleVectorJdbcRepository.upsert(new ArticleVectorRecord(
                    articleRecord.getConceptId(),
                    querySearchProperties.getVector().getEmbeddingModel(),
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
                && embeddingModel != null
                && searchCapabilityService.supportsVectorType()
                && searchCapabilityService.hasArticleVectorIndex();
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

    /**
     * 校验 embedding 维度是否与当前索引基线一致。
     *
     * @param embedding embedding 向量
     * @param conceptId 概念标识
     * @return 是否匹配
     */
    private boolean hasExpectedDimensions(float[] embedding, String conceptId) {
        int expectedDimensions = querySearchProperties.getVector().getExpectedDimensions();
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
}
