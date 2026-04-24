package com.xbk.lattice.query.deepresearch.store;

import com.xbk.lattice.query.deepresearch.domain.EvidenceCard;
import com.xbk.lattice.query.deepresearch.domain.EvidenceLedger;
import com.xbk.lattice.query.deepresearch.domain.LayerSummary;
import com.xbk.lattice.query.deepresearch.domain.LayeredResearchPlan;

import java.util.List;

/**
 * Deep Research 工作集存储
 *
 * 职责：外置存放深度研究图中的大对象载荷
 *
 * @author xiexu
 */
public interface DeepResearchWorkingSetStore {

    /**
     * 保存研究计划。
     *
     * @param queryId 查询标识
     * @param plan 研究计划
     * @return 工作集引用
     */
    String savePlan(String queryId, LayeredResearchPlan plan);

    /**
     * 读取研究计划。
     *
     * @param ref 工作集引用
     * @return 研究计划
     */
    LayeredResearchPlan loadPlan(String ref);

    /**
     * 保存分层摘要。
     *
     * @param queryId 查询标识
     * @param layerIndex 层序号
     * @param layerSummary 分层摘要
     * @return 工作集引用
     */
    String saveLayerSummary(String queryId, int layerIndex, LayerSummary layerSummary);

    /**
     * 读取分层摘要。
     *
     * @param ref 工作集引用
     * @return 分层摘要
     */
    LayerSummary loadLayerSummary(String ref);

    /**
     * 保存证据卡列表。
     *
     * @param queryId 查询标识
     * @param slotKey 槽位键
     * @param evidenceCards 证据卡列表
     * @return 工作集引用
     */
    String saveEvidenceCards(String queryId, String slotKey, List<EvidenceCard> evidenceCards);

    /**
     * 读取证据卡列表。
     *
     * @param ref 工作集引用
     * @return 证据卡列表
     */
    List<EvidenceCard> loadEvidenceCards(String ref);

    /**
     * 保存证据账本。
     *
     * @param queryId 查询标识
     * @param evidenceLedger 证据账本
     * @return 工作集引用
     */
    String saveEvidenceLedger(String queryId, EvidenceLedger evidenceLedger);

    /**
     * 读取证据账本。
     *
     * @param ref 工作集引用
     * @return 证据账本
     */
    EvidenceLedger loadEvidenceLedger(String ref);

    /**
     * 保存 Deep Research 审计对象。
     *
     * @param queryId 查询标识
     * @param audit 审计对象
     * @return 工作集引用
     */
    String saveDeepResearchAudit(String queryId, Object audit);

    /**
     * 读取 Deep Research 审计对象。
     *
     * @param ref 工作集引用
     * @return 审计对象
     */
    Object loadDeepResearchAudit(String ref);

    /**
     * 清理指定查询的全部工作集。
     *
     * @param queryId 查询标识
     */
    void deleteByQueryId(String queryId);
}
