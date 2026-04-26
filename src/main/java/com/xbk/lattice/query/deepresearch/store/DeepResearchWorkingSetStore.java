package com.xbk.lattice.query.deepresearch.store;

import com.xbk.lattice.query.deepresearch.domain.EvidenceCard;
import com.xbk.lattice.query.deepresearch.domain.EvidenceLedger;
import com.xbk.lattice.query.deepresearch.domain.LayerSummary;
import com.xbk.lattice.query.deepresearch.domain.LayeredResearchPlan;
import com.xbk.lattice.query.evidence.domain.AnswerProjectionBundle;
import com.xbk.lattice.query.evidence.domain.ProjectionCandidate;

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
     * 保存任务研究结果列表。
     *
     * @param queryId 查询标识
     * @param slotKey 槽位键
     * @param evidenceCards 任务研究结果列表
     * @return 工作集引用
     */
    String saveTaskResults(String queryId, String slotKey, List<EvidenceCard> evidenceCards);

    /**
     * 读取任务研究结果列表。
     *
     * @param ref 工作集引用
     * @return 任务研究结果列表
     */
    List<EvidenceCard> loadTaskResults(String ref);

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
     * 保存投影候选列表。
     *
     * @param queryId 查询标识
     * @param projectionCandidates 投影候选
     * @return 工作集引用
     */
    String saveProjectionCandidates(String queryId, List<ProjectionCandidate> projectionCandidates);

    /**
     * 读取投影候选列表。
     *
     * @param ref 工作集引用
     * @return 投影候选
     */
    List<ProjectionCandidate> loadProjectionCandidates(String ref);

    /**
     * 保存答案投影包。
     *
     * @param queryId 查询标识
     * @param answerProjectionBundle 答案投影包
     * @return 工作集引用
     */
    String saveAnswerProjectionBundle(String queryId, AnswerProjectionBundle answerProjectionBundle);

    /**
     * 读取答案投影包。
     *
     * @param ref 工作集引用
     * @return 答案投影包
     */
    AnswerProjectionBundle loadAnswerProjectionBundle(String ref);

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
