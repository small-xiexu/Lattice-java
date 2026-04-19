package com.xbk.lattice.admin.service;

import com.xbk.lattice.api.admin.AdminVectorIndexRebuildRequest;
import com.xbk.lattice.api.admin.AdminVectorIndexRebuildResponse;
import com.xbk.lattice.api.admin.AdminVectorIndexStatusResponse;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ArticleChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleChunkVectorJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleVectorJdbcRepository;
import com.xbk.lattice.query.service.ArticleChunkVectorIndexService;
import com.xbk.lattice.query.service.ArticleVectorIndexService;
import com.xbk.lattice.query.service.QueryVectorConfigService;
import com.xbk.lattice.query.service.QueryVectorConfigState;
import com.xbk.lattice.query.service.SearchCapabilityService;
import com.xbk.lattice.query.service.VectorSchemaInspection;
import com.xbk.lattice.query.service.VectorSchemaInspector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 管理侧向量索引维护服务
 *
 * 职责：汇总向量索引状态并提供全量重建入口
 *
 * @author xiexu
 */
@Slf4j
@Service
@Profile("jdbc")
public class AdminVectorIndexMaintenanceService {

    private final SearchCapabilityService searchCapabilityService;

    private final QueryVectorConfigService queryVectorConfigService;

    private final ArticleJdbcRepository articleJdbcRepository;

    private final ArticleChunkJdbcRepository articleChunkJdbcRepository;

    private final ArticleVectorJdbcRepository articleVectorJdbcRepository;

    private final ArticleVectorIndexService articleVectorIndexService;

    private final ArticleChunkVectorJdbcRepository articleChunkVectorJdbcRepository;

    private final ArticleChunkVectorIndexService articleChunkVectorIndexService;

    private final VectorSchemaInspector vectorSchemaInspector;

    /**
     * 创建管理侧向量索引维护服务。
     *
     * @param querySearchProperties 查询检索配置
     * @param searchCapabilityService 检索能力探测服务
     * @param articleJdbcRepository 文章仓储
     * @param articleVectorJdbcRepository 向量索引仓储
     * @param articleVectorIndexService 向量索引服务
     */
    public AdminVectorIndexMaintenanceService(
            SearchCapabilityService searchCapabilityService,
            QueryVectorConfigService queryVectorConfigService,
            ArticleJdbcRepository articleJdbcRepository,
            ArticleChunkJdbcRepository articleChunkJdbcRepository,
            ArticleVectorJdbcRepository articleVectorJdbcRepository,
            ArticleVectorIndexService articleVectorIndexService,
            ArticleChunkVectorJdbcRepository articleChunkVectorJdbcRepository,
            ArticleChunkVectorIndexService articleChunkVectorIndexService,
            VectorSchemaInspector vectorSchemaInspector
    ) {
        this.searchCapabilityService = searchCapabilityService;
        this.queryVectorConfigService = queryVectorConfigService;
        this.articleJdbcRepository = articleJdbcRepository;
        this.articleChunkJdbcRepository = articleChunkJdbcRepository;
        this.articleVectorJdbcRepository = articleVectorJdbcRepository;
        this.articleVectorIndexService = articleVectorIndexService;
        this.articleChunkVectorJdbcRepository = articleChunkVectorJdbcRepository;
        this.articleChunkVectorIndexService = articleChunkVectorIndexService;
        this.vectorSchemaInspector = vectorSchemaInspector;
    }

    /**
     * 返回向量索引当前状态。
     *
     * @return 向量索引状态
     */
    public AdminVectorIndexStatusResponse getStatus() {
        QueryVectorConfigState state = queryVectorConfigService.getCurrentState();
        VectorSchemaInspection schemaInspection = vectorSchemaInspector.inspect();
        boolean vectorEnabled = state.isVectorEnabled();
        String configuredModelName = state.getModelName();
        int configuredExpectedDimensions = state.getProfileDimensions() == null
                ? 0
                : state.getProfileDimensions().intValue();
        boolean vectorTypeAvailable = searchCapabilityService.supportsVectorType();
        boolean vectorIndexTableAvailable = searchCapabilityService.hasArticleVectorIndex();
        boolean indexingAvailable = articleVectorIndexService.isIndexingAvailable();
        int articleCount = articleJdbcRepository.findAll().size();
        int indexedArticleCount = vectorIndexTableAvailable ? articleVectorJdbcRepository.countAll() : 0;
        List<String> indexedModelNames = vectorIndexTableAvailable
                ? articleVectorJdbcRepository.findDistinctModelNames()
                : List.of();
        Optional<OffsetDateTime> latestUpdatedAt = vectorIndexTableAvailable
                ? articleVectorJdbcRepository.findLatestUpdatedAt()
                : Optional.empty();
        Optional<String> embeddingColumnType = vectorIndexTableAvailable
                ? articleVectorJdbcRepository.findEmbeddingColumnType()
                : Optional.empty();
        String normalizedEmbeddingColumnType = normalizeEmbeddingColumnType(embeddingColumnType.orElse(""));
        return new AdminVectorIndexStatusResponse(
                vectorEnabled,
                vectorTypeAvailable,
                vectorIndexTableAvailable,
                indexingAvailable,
                state.getEmbeddingModelProfileId(),
                state.getProviderType(),
                configuredModelName,
                configuredExpectedDimensions,
                state.getProfileDimensions(),
                normalizedEmbeddingColumnType,
                schemaInspection.getSchemaDimensions(),
                state.getProfileDimensions() == null || schemaInspection.getSchemaDimensions() == null
                        ? null
                        : Boolean.valueOf(schemaInspection.isDimensionsConsistent()),
                schemaInspection.isDimensionsConsistent(),
                schemaInspection.isAnnIndexReady(),
                schemaInspection.getAnnIndexType(),
                articleCount,
                indexedArticleCount,
                indexedModelNames,
                latestUpdatedAt.map(OffsetDateTime::toString).orElse("")
        );
    }

    /**
     * 执行向量索引全量重建。
     *
     * @param request 重建请求
     * @return 重建结果
     */
    @Transactional(rollbackFor = Exception.class)
    public AdminVectorIndexRebuildResponse rebuild(AdminVectorIndexRebuildRequest request) {
        AdminVectorIndexRebuildRequest effectiveRequest = request == null
                ? new AdminVectorIndexRebuildRequest()
                : request;
        validateRebuildPrerequisites();
        List<ArticleRecord> articleRecords = articleJdbcRepository.findAll();
        int previousIndexedArticleCount = articleVectorJdbcRepository.countAll();
        int previousIndexedChunkCount = articleChunkVectorJdbcRepository == null
                ? 0
                : articleChunkVectorJdbcRepository.countAll();
        if (effectiveRequest.isTruncateFirst()) {
            articleVectorJdbcRepository.deleteAll();
            if (articleChunkVectorJdbcRepository != null) {
                articleChunkVectorJdbcRepository.deleteAll();
            }
        }
        articleVectorIndexService.indexArticles(articleRecords);
        if (articleChunkVectorIndexService != null) {
            if (articleChunkJdbcRepository != null) {
                articleChunkJdbcRepository.rebuildAll(articleRecords);
            }
            articleChunkVectorIndexService.indexArticles(articleRecords);
        }
        int indexedArticleCount = articleVectorJdbcRepository.countAll();
        int indexedChunkCount = articleChunkVectorJdbcRepository == null
                ? 0
                : articleChunkVectorJdbcRepository.countAll();
        String configuredModelName = articleVectorIndexService.getConfiguredModelName();
        String operator = normalizeOperator(effectiveRequest.getOperator());
        OffsetDateTime rebuiltAt = articleVectorJdbcRepository.findLatestUpdatedAt().orElse(OffsetDateTime.now());
        log.info(
                "Vector index rebuild finished. truncateFirst: {}, targetArticleCount: {}, indexedArticleCount: {}, indexedChunkCount: {}, configuredModelName: {}, operator: {}",
                effectiveRequest.isTruncateFirst(),
                articleRecords.size(),
                indexedArticleCount,
                indexedChunkCount,
                configuredModelName,
                operator
        );
        return new AdminVectorIndexRebuildResponse(
                articleRecords.size(),
                previousIndexedArticleCount,
                indexedArticleCount,
                previousIndexedChunkCount,
                indexedChunkCount,
                effectiveRequest.isTruncateFirst(),
                configuredModelName,
                operator,
                rebuiltAt.toString()
        );
    }

    /**
     * 校验重建前提条件。
     */
    private void validateRebuildPrerequisites() {
        if (!queryVectorConfigService.getCurrentState().isVectorEnabled()) {
            throw new IllegalArgumentException("当前未启用向量索引");
        }
        if (!searchCapabilityService.supportsVectorType()) {
            throw new IllegalArgumentException("当前数据库未启用 vector 类型");
        }
        if (!searchCapabilityService.hasArticleVectorIndex()) {
            throw new IllegalArgumentException("当前 schema 不存在 article_vector_index 表");
        }
        if (!articleVectorIndexService.isIndexingAvailable()) {
            throw new IllegalArgumentException("当前 embedding 模型或向量索引能力不可用");
        }
        if (!vectorSchemaInspector.inspect().isDimensionsConsistent()) {
            throw new IllegalArgumentException("当前 embedding profile 维度与 schema 维度不一致");
        }
    }

    /**
     * 规范化向量列类型展示。
     *
     * @param embeddingColumnType 数据库返回的类型描述
     * @return 规范化后的类型描述
     */
    private String normalizeEmbeddingColumnType(String embeddingColumnType) {
        if (embeddingColumnType == null || embeddingColumnType.isBlank()) {
            return "";
        }
        String normalizedType = embeddingColumnType.trim();
        int vectorIndex = normalizedType.lastIndexOf("vector(");
        if (vectorIndex < 0) {
            return normalizedType;
        }
        return normalizedType.substring(vectorIndex);
    }

    /**
     * 规范化操作人。
     *
     * @param operator 操作人
     * @return 规范化后的操作人
     */
    private String normalizeOperator(String operator) {
        if (operator == null || operator.trim().isBlank()) {
            return "admin";
        }
        return operator.trim();
    }
}
