package com.xbk.lattice.compiler.graph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.ast.domain.AstGraphExtractReport;
import com.xbk.lattice.compiler.domain.AnalyzedConcept;
import com.xbk.lattice.compiler.domain.MergedConcept;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.compiler.domain.SourceBatch;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.redis.AbstractRedisJsonStore;
import com.xbk.lattice.query.service.RedisKeyValueStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis 版 Compile 工作集存储
 *
 * 职责：将 Compile 图跨节点的大对象载荷与版本化引用保留到 Redis
 *
 * @author xiexu
 */
@Component
@ConditionalOnProperty(prefix = "lattice.compiler.working-set", name = "store", havingValue = "redis")
public class RedisCompileWorkingSetStore extends AbstractRedisJsonStore implements CompileWorkingSetStore {

    private static final TypeReference<List<RawSource>> RAW_SOURCES_TYPE = new TypeReference<List<RawSource>>() {
    };

    private static final TypeReference<Map<String, List<RawSource>>> GROUPED_SOURCES_TYPE =
            new TypeReference<Map<String, List<RawSource>>>() {
            };

    private static final TypeReference<Map<String, List<SourceBatch>>> SOURCE_BATCHES_TYPE =
            new TypeReference<Map<String, List<SourceBatch>>>() {
            };

    private static final TypeReference<List<AnalyzedConcept>> ANALYZED_CONCEPTS_TYPE =
            new TypeReference<List<AnalyzedConcept>>() {
            };

    private static final TypeReference<List<MergedConcept>> MERGED_CONCEPTS_TYPE =
            new TypeReference<List<MergedConcept>>() {
            };

    private static final TypeReference<Map<String, List<MergedConcept>>> ENHANCEMENT_CONCEPTS_TYPE =
            new TypeReference<Map<String, List<MergedConcept>>>() {
            };

    private static final TypeReference<List<ArticleRecord>> ARTICLE_RECORDS_TYPE =
            new TypeReference<List<ArticleRecord>>() {
            };

    private static final TypeReference<List<ArticleReviewEnvelope>> ARTICLE_REVIEW_ENVELOPES_TYPE =
            new TypeReference<List<ArticleReviewEnvelope>>() {
            };

    /**
     * 创建 Redis 版 Compile 工作集存储。
     *
     * @param redisKeyValueStore Redis 键值存储
     * @param objectMapper JSON 映射器
     * @param properties Compile 工作集配置
     */
    public RedisCompileWorkingSetStore(
            RedisKeyValueStore redisKeyValueStore,
            ObjectMapper objectMapper,
            CompileWorkingSetProperties properties
    ) {
        super(redisKeyValueStore, objectMapper, properties.getKeyPrefix(), properties.getTtlSeconds());
    }

    @Override
    public String saveRawSources(String jobId, List<RawSource> rawSources) {
        return savePayload(jobId, "raw_sources", rawSources == null ? List.of() : rawSources);
    }

    @Override
    public List<RawSource> loadRawSources(String ref) {
        List<RawSource> rawSources = loadJson(ref, RAW_SOURCES_TYPE);
        return rawSources == null ? List.of() : rawSources;
    }

    @Override
    public String saveGroupedSources(String jobId, Map<String, List<RawSource>> groupedSources) {
        return savePayload(jobId, "grouped_sources", groupedSources == null ? Map.of() : groupedSources);
    }

    @Override
    public Map<String, List<RawSource>> loadGroupedSources(String ref) {
        Map<String, List<RawSource>> groupedSources = loadJson(ref, GROUPED_SOURCES_TYPE);
        return groupedSources == null ? new LinkedHashMap<String, List<RawSource>>() : groupedSources;
    }

    @Override
    public String saveSourceBatches(String jobId, Map<String, List<SourceBatch>> sourceBatches) {
        return savePayload(jobId, "source_batches", sourceBatches == null ? Map.of() : sourceBatches);
    }

    @Override
    public Map<String, List<SourceBatch>> loadSourceBatches(String ref) {
        Map<String, List<SourceBatch>> sourceBatches = loadJson(ref, SOURCE_BATCHES_TYPE);
        return sourceBatches == null ? new LinkedHashMap<String, List<SourceBatch>>() : sourceBatches;
    }

    @Override
    public String saveAnalyzedConcepts(String jobId, List<AnalyzedConcept> analyzedConcepts) {
        return savePayload(jobId, "analyzed_concepts", analyzedConcepts == null ? List.of() : analyzedConcepts);
    }

    @Override
    public List<AnalyzedConcept> loadAnalyzedConcepts(String ref) {
        List<AnalyzedConcept> analyzedConcepts = loadJson(ref, ANALYZED_CONCEPTS_TYPE);
        return analyzedConcepts == null ? List.of() : analyzedConcepts;
    }

    @Override
    public String saveMergedConcepts(String jobId, List<MergedConcept> mergedConcepts) {
        return savePayload(jobId, "merged_concepts", mergedConcepts == null ? List.of() : mergedConcepts);
    }

    @Override
    public List<MergedConcept> loadMergedConcepts(String ref) {
        List<MergedConcept> mergedConcepts = loadJson(ref, MERGED_CONCEPTS_TYPE);
        return mergedConcepts == null ? List.of() : mergedConcepts;
    }

    @Override
    public String saveEnhancementConcepts(String jobId, Map<String, List<MergedConcept>> enhancementConcepts) {
        return savePayload(jobId, "enhancement_concepts", enhancementConcepts == null ? Map.of() : enhancementConcepts);
    }

    @Override
    public Map<String, List<MergedConcept>> loadEnhancementConcepts(String ref) {
        Map<String, List<MergedConcept>> enhancementConcepts = loadJson(ref, ENHANCEMENT_CONCEPTS_TYPE);
        return enhancementConcepts == null ? new LinkedHashMap<String, List<MergedConcept>>() : enhancementConcepts;
    }

    @Override
    public String saveConceptsToCreate(String jobId, List<MergedConcept> conceptsToCreate) {
        return savePayload(jobId, "concepts_to_create", conceptsToCreate == null ? List.of() : conceptsToCreate);
    }

    @Override
    public List<MergedConcept> loadConceptsToCreate(String ref) {
        List<MergedConcept> conceptsToCreate = loadJson(ref, MERGED_CONCEPTS_TYPE);
        return conceptsToCreate == null ? List.of() : conceptsToCreate;
    }

    @Override
    public String saveDraftArticles(String jobId, List<ArticleRecord> draftArticles) {
        return savePayload(jobId, "draft_articles", draftArticles == null ? List.of() : draftArticles);
    }

    @Override
    public List<ArticleRecord> loadDraftArticles(String ref) {
        List<ArticleRecord> draftArticles = loadJson(ref, ARTICLE_RECORDS_TYPE);
        return draftArticles == null ? List.of() : draftArticles;
    }

    @Override
    public String saveReviewedArticles(String jobId, List<ArticleReviewEnvelope> reviewedArticles) {
        return savePayload(jobId, "reviewed_articles", reviewedArticles == null ? List.of() : reviewedArticles);
    }

    @Override
    public List<ArticleReviewEnvelope> loadReviewedArticles(String ref) {
        List<ArticleReviewEnvelope> reviewedArticles = loadJson(ref, ARTICLE_REVIEW_ENVELOPES_TYPE);
        return reviewedArticles == null ? List.of() : reviewedArticles;
    }

    @Override
    public String saveReviewPartition(String jobId, ReviewPartition reviewPartition) {
        return savePayload(jobId, "review_partition", reviewPartition);
    }

    @Override
    public ReviewPartition loadReviewPartition(String ref) {
        return loadJson(ref, ReviewPartition.class);
    }

    @Override
    public String saveAcceptedArticles(String jobId, List<ArticleReviewEnvelope> acceptedArticles) {
        return savePayload(jobId, "accepted_articles", acceptedArticles == null ? List.of() : acceptedArticles);
    }

    @Override
    public List<ArticleReviewEnvelope> loadAcceptedArticles(String ref) {
        List<ArticleReviewEnvelope> acceptedArticles = loadJson(ref, ARTICLE_REVIEW_ENVELOPES_TYPE);
        return acceptedArticles == null ? List.of() : acceptedArticles;
    }

    @Override
    public String saveNeedsHumanReviewArticles(String jobId, List<ArticleReviewEnvelope> needsHumanReviewArticles) {
        return savePayload(
                jobId,
                "needs_human_review_articles",
                needsHumanReviewArticles == null ? List.of() : needsHumanReviewArticles
        );
    }

    @Override
    public List<ArticleReviewEnvelope> loadNeedsHumanReviewArticles(String ref) {
        List<ArticleReviewEnvelope> needsHumanReviewArticles = loadJson(ref, ARTICLE_REVIEW_ENVELOPES_TYPE);
        return needsHumanReviewArticles == null ? List.of() : needsHumanReviewArticles;
    }

    @Override
    public String saveAstExtractReport(String jobId, AstGraphExtractReport astGraphExtractReport) {
        return savePayload(jobId, "ast_extract_report", astGraphExtractReport);
    }

    @Override
    public AstGraphExtractReport loadAstExtractReport(String ref) {
        return loadJson(ref, AstGraphExtractReport.class);
    }

    @Override
    public void deleteByJobId(String jobId) {
        deleteByOwnerPrefix(jobId + ":");
    }

    private String savePayload(String jobId, String payloadType, Object payload) {
        String ref = buildVersionedRef(jobId, payloadType);
        saveJson(ref, payload);
        return ref;
    }
}
