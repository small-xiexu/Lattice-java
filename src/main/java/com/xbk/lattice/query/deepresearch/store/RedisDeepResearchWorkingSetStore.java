package com.xbk.lattice.query.deepresearch.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.infra.redis.AbstractRedisJsonStore;
import com.xbk.lattice.query.deepresearch.domain.DeepResearchAuditSnapshot;
import com.xbk.lattice.query.deepresearch.domain.EvidenceCard;
import com.xbk.lattice.query.deepresearch.domain.EvidenceLedger;
import com.xbk.lattice.query.deepresearch.domain.LayerSummary;
import com.xbk.lattice.query.deepresearch.domain.LayeredResearchPlan;
import com.xbk.lattice.query.evidence.domain.AnswerProjectionBundle;
import com.xbk.lattice.query.evidence.domain.ProjectionCandidate;
import com.xbk.lattice.query.service.RedisKeyValueStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis 版 Deep Research 工作集存储
 *
 * 职责：将 Deep Research 的计划、分层摘要、证据与投影工作集保留到 Redis
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
@ConditionalOnProperty(prefix = "lattice.deep-research.working-set", name = "store", havingValue = "redis")
public class RedisDeepResearchWorkingSetStore extends AbstractRedisJsonStore implements DeepResearchWorkingSetStore {

    private static final TypeReference<List<EvidenceCard>> EVIDENCE_CARDS_TYPE = new TypeReference<List<EvidenceCard>>() {
    };

    private static final TypeReference<List<ProjectionCandidate>> PROJECTION_CANDIDATES_TYPE =
            new TypeReference<List<ProjectionCandidate>>() {
            };

    /**
     * 创建 Redis 版 Deep Research 工作集存储。
     *
     * @param redisKeyValueStore Redis 键值存储
     * @param objectMapper JSON 映射器
     * @param properties Deep Research 工作集配置
     */
    public RedisDeepResearchWorkingSetStore(
            RedisKeyValueStore redisKeyValueStore,
            ObjectMapper objectMapper,
            DeepResearchWorkingSetProperties properties
    ) {
        super(redisKeyValueStore, objectMapper, properties.getKeyPrefix(), properties.getTtlSeconds());
    }

    @Override
    public String savePlan(String queryId, LayeredResearchPlan plan) {
        String ref = buildRef(queryId, "plan");
        saveJson(ref, plan);
        return ref;
    }

    @Override
    public LayeredResearchPlan loadPlan(String ref) {
        return loadJson(ref, LayeredResearchPlan.class);
    }

    @Override
    public String saveLayerSummary(String queryId, int layerIndex, LayerSummary layerSummary) {
        String ref = buildRef(queryId, "layer-summary-" + layerIndex);
        saveJson(ref, layerSummary);
        return ref;
    }

    @Override
    public LayerSummary loadLayerSummary(String ref) {
        return loadJson(ref, LayerSummary.class);
    }

    @Override
    public String saveTaskResults(String queryId, String slotKey, List<EvidenceCard> evidenceCards) {
        String ref = buildRef(queryId, "task-results-" + slotKey);
        saveJson(ref, evidenceCards == null ? List.of() : evidenceCards);
        return ref;
    }

    @Override
    public List<EvidenceCard> loadTaskResults(String ref) {
        List<EvidenceCard> evidenceCards = loadJson(ref, EVIDENCE_CARDS_TYPE);
        return evidenceCards == null ? List.of() : evidenceCards;
    }

    @Override
    public String saveEvidenceLedger(String queryId, EvidenceLedger evidenceLedger) {
        String ref = buildRef(queryId, "evidence-ledger");
        saveJson(ref, evidenceLedger);
        return ref;
    }

    @Override
    public EvidenceLedger loadEvidenceLedger(String ref) {
        return loadJson(ref, EvidenceLedger.class);
    }

    @Override
    public String saveProjectionCandidates(String queryId, List<ProjectionCandidate> projectionCandidates) {
        String ref = buildRef(queryId, "projection-candidates");
        saveJson(ref, projectionCandidates == null ? List.of() : projectionCandidates);
        return ref;
    }

    @Override
    public List<ProjectionCandidate> loadProjectionCandidates(String ref) {
        List<ProjectionCandidate> projectionCandidates = loadJson(ref, PROJECTION_CANDIDATES_TYPE);
        return projectionCandidates == null ? List.of() : projectionCandidates;
    }

    @Override
    public String saveAnswerProjectionBundle(String queryId, AnswerProjectionBundle answerProjectionBundle) {
        String ref = buildRef(queryId, "answer-projection-bundle");
        saveJson(ref, answerProjectionBundle);
        return ref;
    }

    @Override
    public AnswerProjectionBundle loadAnswerProjectionBundle(String ref) {
        return loadJson(ref, AnswerProjectionBundle.class);
    }

    @Override
    public String saveDeepResearchAudit(String queryId, Object audit) {
        String ref = buildRef(queryId, "audit");
        saveJson(ref, audit);
        return ref;
    }

    @Override
    public Object loadDeepResearchAudit(String ref) {
        return loadJson(ref, DeepResearchAuditSnapshot.class);
    }

    @Override
    public void deleteByQueryId(String queryId) {
        deleteByOwnerPrefix(queryId + ":");
    }

    private String buildRef(String queryId, String suffix) {
        return queryId + ":" + suffix;
    }
}
