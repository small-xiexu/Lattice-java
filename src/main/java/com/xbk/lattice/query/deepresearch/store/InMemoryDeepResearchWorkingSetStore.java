package com.xbk.lattice.query.deepresearch.store;

import com.xbk.lattice.query.deepresearch.domain.EvidenceCard;
import com.xbk.lattice.query.deepresearch.domain.EvidenceLedger;
import com.xbk.lattice.query.deepresearch.domain.LayerSummary;
import com.xbk.lattice.query.deepresearch.domain.LayeredResearchPlan;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版 Deep Research 工作集存储
 *
 * 职责：为 Deep Research 图提供进程内工作集外置存储
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class InMemoryDeepResearchWorkingSetStore implements DeepResearchWorkingSetStore {

    private final Map<String, Object> store = new ConcurrentHashMap<String, Object>();

    /**
     * 保存研究计划。
     *
     * @param queryId 查询标识
     * @param plan 研究计划
     * @return 工作集引用
     */
    @Override
    public String savePlan(String queryId, LayeredResearchPlan plan) {
        String ref = buildRef(queryId, "plan");
        store.put(ref, plan);
        return ref;
    }

    /**
     * 读取研究计划。
     *
     * @param ref 工作集引用
     * @return 研究计划
     */
    @Override
    public LayeredResearchPlan loadPlan(String ref) {
        Object value = store.get(ref);
        if (value instanceof LayeredResearchPlan) {
            return (LayeredResearchPlan) value;
        }
        return null;
    }

    /**
     * 保存分层摘要。
     *
     * @param queryId 查询标识
     * @param layerIndex 层序号
     * @param layerSummary 分层摘要
     * @return 工作集引用
     */
    @Override
    public String saveLayerSummary(String queryId, int layerIndex, LayerSummary layerSummary) {
        String ref = buildRef(queryId, "layer-summary-" + layerIndex);
        store.put(ref, layerSummary);
        return ref;
    }

    /**
     * 读取分层摘要。
     *
     * @param ref 工作集引用
     * @return 分层摘要
     */
    @Override
    public LayerSummary loadLayerSummary(String ref) {
        Object value = store.get(ref);
        if (value instanceof LayerSummary) {
            return (LayerSummary) value;
        }
        return null;
    }

    /**
     * 保存证据卡列表。
     *
     * @param queryId 查询标识
     * @param slotKey 槽位键
     * @param evidenceCards 证据卡列表
     * @return 工作集引用
     */
    @Override
    public String saveEvidenceCards(String queryId, String slotKey, List<EvidenceCard> evidenceCards) {
        String ref = buildRef(queryId, "evidence-cards-" + slotKey);
        store.put(ref, new ArrayList<EvidenceCard>(evidenceCards));
        return ref;
    }

    /**
     * 读取证据卡列表。
     *
     * @param ref 工作集引用
     * @return 证据卡列表
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<EvidenceCard> loadEvidenceCards(String ref) {
        Object value = store.get(ref);
        if (!(value instanceof List<?>)) {
            return List.of();
        }
        return new ArrayList<EvidenceCard>((List<EvidenceCard>) value);
    }

    /**
     * 保存证据账本。
     *
     * @param queryId 查询标识
     * @param evidenceLedger 证据账本
     * @return 工作集引用
     */
    @Override
    public String saveEvidenceLedger(String queryId, EvidenceLedger evidenceLedger) {
        String ref = buildRef(queryId, "evidence-ledger");
        store.put(ref, evidenceLedger);
        return ref;
    }

    /**
     * 读取证据账本。
     *
     * @param ref 工作集引用
     * @return 证据账本
     */
    @Override
    public EvidenceLedger loadEvidenceLedger(String ref) {
        Object value = store.get(ref);
        if (value instanceof EvidenceLedger) {
            return (EvidenceLedger) value;
        }
        return null;
    }

    /**
     * 保存 Deep Research 审计对象。
     *
     * @param queryId 查询标识
     * @param audit 审计对象
     * @return 工作集引用
     */
    @Override
    public String saveDeepResearchAudit(String queryId, Object audit) {
        String ref = buildRef(queryId, "audit");
        store.put(ref, audit);
        return ref;
    }

    /**
     * 读取 Deep Research 审计对象。
     *
     * @param ref 工作集引用
     * @return 审计对象
     */
    @Override
    public Object loadDeepResearchAudit(String ref) {
        return store.get(ref);
    }

    /**
     * 清理指定查询的全部工作集。
     *
     * @param queryId 查询标识
     */
    @Override
    public void deleteByQueryId(String queryId) {
        String keyPrefix = queryId + ":";
        for (String key : store.keySet()) {
            if (key.startsWith(keyPrefix)) {
                store.remove(key);
            }
        }
    }

    private String buildRef(String queryId, String suffix) {
        return queryId + ":" + suffix;
    }
}
