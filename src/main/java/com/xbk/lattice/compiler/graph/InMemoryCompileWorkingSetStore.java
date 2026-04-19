package com.xbk.lattice.compiler.graph;

import com.xbk.lattice.compiler.domain.AnalyzedConcept;
import com.xbk.lattice.compiler.domain.MergedConcept;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.compiler.domain.SourceBatch;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内存版编译工作集存储
 *
 * 职责：在 Phase 1 以内存容器保存编译图跨节点载荷
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class InMemoryCompileWorkingSetStore implements CompileWorkingSetStore {

    private final Map<String, Object> payloadStore = new ConcurrentHashMap<String, Object>();

    private final Map<String, AtomicInteger> versionStore = new ConcurrentHashMap<String, AtomicInteger>();

    @Override
    public String saveRawSources(String jobId, List<RawSource> rawSources) {
        return savePayload(jobId, "raw_sources", new ArrayList<RawSource>(rawSources));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RawSource> loadRawSources(String ref) {
        return copyList(readPayload(ref));
    }

    @Override
    public String saveGroupedSources(String jobId, Map<String, List<RawSource>> groupedSources) {
        return savePayload(jobId, "grouped_sources", deepCopyRawSourceMap(groupedSources));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, List<RawSource>> loadGroupedSources(String ref) {
        return deepCopyRawSourceMap(readPayload(ref));
    }

    @Override
    public String saveSourceBatches(String jobId, Map<String, List<SourceBatch>> sourceBatches) {
        return savePayload(jobId, "source_batches", deepCopySourceBatchMap(sourceBatches));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, List<SourceBatch>> loadSourceBatches(String ref) {
        return deepCopySourceBatchMap(readPayload(ref));
    }

    @Override
    public String saveAnalyzedConcepts(String jobId, List<AnalyzedConcept> analyzedConcepts) {
        return savePayload(jobId, "analyzed_concepts", new ArrayList<AnalyzedConcept>(analyzedConcepts));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<AnalyzedConcept> loadAnalyzedConcepts(String ref) {
        return copyList(readPayload(ref));
    }

    @Override
    public String saveMergedConcepts(String jobId, List<MergedConcept> mergedConcepts) {
        return savePayload(jobId, "merged_concepts", new ArrayList<MergedConcept>(mergedConcepts));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<MergedConcept> loadMergedConcepts(String ref) {
        return copyList(readPayload(ref));
    }

    @Override
    public String saveEnhancementConcepts(String jobId, Map<String, List<MergedConcept>> enhancementConcepts) {
        return savePayload(jobId, "enhancement_concepts", deepCopyMergedConceptMap(enhancementConcepts));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, List<MergedConcept>> loadEnhancementConcepts(String ref) {
        return deepCopyMergedConceptMap(readPayload(ref));
    }

    @Override
    public String saveConceptsToCreate(String jobId, List<MergedConcept> conceptsToCreate) {
        return savePayload(jobId, "concepts_to_create", new ArrayList<MergedConcept>(conceptsToCreate));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<MergedConcept> loadConceptsToCreate(String ref) {
        return copyList(readPayload(ref));
    }

    @Override
    public String saveDraftArticles(String jobId, List<ArticleRecord> draftArticles) {
        return savePayload(jobId, "draft_articles", new ArrayList<ArticleRecord>(draftArticles));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ArticleRecord> loadDraftArticles(String ref) {
        return copyList(readPayload(ref));
    }

    @Override
    public String saveReviewedArticles(String jobId, List<ArticleReviewEnvelope> reviewedArticles) {
        return savePayload(jobId, "reviewed_articles", new ArrayList<ArticleReviewEnvelope>(reviewedArticles));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ArticleReviewEnvelope> loadReviewedArticles(String ref) {
        return copyList(readPayload(ref));
    }

    @Override
    public String saveReviewPartition(String jobId, ReviewPartition reviewPartition) {
        ReviewPartition copiedPartition = new ReviewPartition();
        copiedPartition.setAccepted(new ArrayList<ArticleReviewEnvelope>(reviewPartition.getAccepted()));
        copiedPartition.setFixable(new ArrayList<ArticleReviewEnvelope>(reviewPartition.getFixable()));
        copiedPartition.setNeedsHumanReview(new ArrayList<ArticleReviewEnvelope>(reviewPartition.getNeedsHumanReview()));
        return savePayload(jobId, "review_partition", copiedPartition);
    }

    @Override
    public ReviewPartition loadReviewPartition(String ref) {
        ReviewPartition reviewPartition = readPayload(ref);
        ReviewPartition copiedPartition = new ReviewPartition();
        copiedPartition.setAccepted(new ArrayList<ArticleReviewEnvelope>(reviewPartition.getAccepted()));
        copiedPartition.setFixable(new ArrayList<ArticleReviewEnvelope>(reviewPartition.getFixable()));
        copiedPartition.setNeedsHumanReview(new ArrayList<ArticleReviewEnvelope>(reviewPartition.getNeedsHumanReview()));
        return copiedPartition;
    }

    @Override
    public String saveAcceptedArticles(String jobId, List<ArticleReviewEnvelope> acceptedArticles) {
        return savePayload(jobId, "accepted_articles", new ArrayList<ArticleReviewEnvelope>(acceptedArticles));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ArticleReviewEnvelope> loadAcceptedArticles(String ref) {
        return copyList(readPayload(ref));
    }

    @Override
    public String saveNeedsHumanReviewArticles(String jobId, List<ArticleReviewEnvelope> needsHumanReviewArticles) {
        return savePayload(
                jobId,
                "needs_human_review_articles",
                new ArrayList<ArticleReviewEnvelope>(needsHumanReviewArticles)
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ArticleReviewEnvelope> loadNeedsHumanReviewArticles(String ref) {
        return copyList(readPayload(ref));
    }

    @Override
    public void deleteByJobId(String jobId) {
        List<String> refs = new ArrayList<String>();
        for (String ref : payloadStore.keySet()) {
            if (ref.startsWith(jobId + ":")) {
                refs.add(ref);
            }
        }
        for (String ref : refs) {
            payloadStore.remove(ref);
        }
        versionStore.remove(jobId + ":raw_sources");
        versionStore.remove(jobId + ":grouped_sources");
        versionStore.remove(jobId + ":source_batches");
        versionStore.remove(jobId + ":analyzed_concepts");
        versionStore.remove(jobId + ":merged_concepts");
        versionStore.remove(jobId + ":enhancement_concepts");
        versionStore.remove(jobId + ":concepts_to_create");
        versionStore.remove(jobId + ":draft_articles");
        versionStore.remove(jobId + ":reviewed_articles");
        versionStore.remove(jobId + ":review_partition");
        versionStore.remove(jobId + ":accepted_articles");
        versionStore.remove(jobId + ":needs_human_review_articles");
    }

    private String savePayload(String jobId, String payloadType, Object payload) {
        String versionKey = jobId + ":" + payloadType;
        int version = versionStore.computeIfAbsent(versionKey, key -> new AtomicInteger(0)).incrementAndGet();
        String ref = versionKey + ":" + version;
        payloadStore.put(ref, payload);
        return ref;
    }

    @SuppressWarnings("unchecked")
    private <T> T readPayload(String ref) {
        Object payload = payloadStore.get(ref);
        if (payload == null) {
            throw new IllegalArgumentException("compile working set ref not found: " + ref);
        }
        return (T) payload;
    }

    private <T> List<T> copyList(List<T> values) {
        return new ArrayList<T>(values);
    }

    private Map<String, List<RawSource>> deepCopyRawSourceMap(Map<String, List<RawSource>> values) {
        Map<String, List<RawSource>> copied = new LinkedHashMap<String, List<RawSource>>();
        for (Map.Entry<String, List<RawSource>> entry : values.entrySet()) {
            copied.put(entry.getKey(), new ArrayList<RawSource>(entry.getValue()));
        }
        return copied;
    }

    private Map<String, List<SourceBatch>> deepCopySourceBatchMap(Map<String, List<SourceBatch>> values) {
        Map<String, List<SourceBatch>> copied = new LinkedHashMap<String, List<SourceBatch>>();
        for (Map.Entry<String, List<SourceBatch>> entry : values.entrySet()) {
            copied.put(entry.getKey(), new ArrayList<SourceBatch>(entry.getValue()));
        }
        return copied;
    }

    private Map<String, List<MergedConcept>> deepCopyMergedConceptMap(Map<String, List<MergedConcept>> values) {
        Map<String, List<MergedConcept>> copied = new LinkedHashMap<String, List<MergedConcept>>();
        for (Map.Entry<String, List<MergedConcept>> entry : values.entrySet()) {
            copied.put(entry.getKey(), new ArrayList<MergedConcept>(entry.getValue()));
        }
        return copied;
    }
}
