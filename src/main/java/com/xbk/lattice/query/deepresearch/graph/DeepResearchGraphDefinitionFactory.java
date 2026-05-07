package com.xbk.lattice.query.deepresearch.graph;

import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.xbk.lattice.query.citation.CitationCheckReport;
import com.xbk.lattice.query.deepresearch.domain.DeepResearchSynthesisResult;
import com.xbk.lattice.query.deepresearch.domain.EvidenceCard;
import com.xbk.lattice.query.deepresearch.domain.EvidenceLedger;
import com.xbk.lattice.query.deepresearch.domain.LayerSummary;
import com.xbk.lattice.query.deepresearch.domain.LayeredResearchPlan;
import com.xbk.lattice.query.deepresearch.domain.ResearchLayer;
import com.xbk.lattice.query.deepresearch.domain.ResearchTask;
import com.xbk.lattice.query.deepresearch.service.DeepResearchExecutionContext;
import com.xbk.lattice.query.deepresearch.service.DeepResearchExecutionRegistry;
import com.xbk.lattice.query.deepresearch.service.DeepResearchResearcherService;
import com.xbk.lattice.query.deepresearch.service.DeepResearchSynthesizer;
import com.xbk.lattice.query.evidence.domain.AnswerProjectionBundle;
import com.xbk.lattice.query.evidence.domain.FactFinding;
import com.xbk.lattice.query.deepresearch.store.DeepResearchWorkingSetStore;
import com.xbk.lattice.query.graph.QueryWorkingSetStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deep Research 图定义工厂
 *
 * 职责：按分层计划动态生成 Deep Research 的 StateGraph 定义
 *
 * @author xiexu
 */
@Component
public class DeepResearchGraphDefinitionFactory {

    private final DeepResearchStateMapper deepResearchStateMapper;

    private final DeepResearchWorkingSetStore deepResearchWorkingSetStore;

    private final QueryWorkingSetStore queryWorkingSetStore;

    private final DeepResearchExecutionRegistry deepResearchExecutionRegistry;

    private final DeepResearchResearcherService deepResearchResearcherService;

    private final DeepResearchSynthesizer deepResearchSynthesizer;

    /**
     * 创建 Deep Research 图定义工厂。
     *
     * @param deepResearchStateMapper 状态映射器
     * @param deepResearchWorkingSetStore 工作集存储
     * @param queryWorkingSetStore Query 工作集存储
     * @param deepResearchExecutionRegistry 执行上下文注册表
     * @param deepResearchResearcherService 研究员服务
     * @param deepResearchSynthesizer 综合器
     */
    public DeepResearchGraphDefinitionFactory(
            DeepResearchStateMapper deepResearchStateMapper,
            DeepResearchWorkingSetStore deepResearchWorkingSetStore,
            QueryWorkingSetStore queryWorkingSetStore,
            DeepResearchExecutionRegistry deepResearchExecutionRegistry,
            DeepResearchResearcherService deepResearchResearcherService,
            DeepResearchSynthesizer deepResearchSynthesizer
    ) {
        this.deepResearchStateMapper = deepResearchStateMapper;
        this.deepResearchWorkingSetStore = deepResearchWorkingSetStore;
        this.queryWorkingSetStore = queryWorkingSetStore;
        this.deepResearchExecutionRegistry = deepResearchExecutionRegistry;
        this.deepResearchResearcherService = deepResearchResearcherService;
        this.deepResearchSynthesizer = deepResearchSynthesizer;
    }

    /**
     * 基于研究计划构建动态图。
     *
     * @param plan 研究计划
     * @return 图定义
     * @throws Exception 构建异常
     */
    public StateGraph build(LayeredResearchPlan plan) throws Exception {
        StateGraph stateGraph = new StateGraph();
        stateGraph.addNode("initialize_plan", AsyncNodeAction.node_async(this::initializePlan));
        String previousNode = "initialize_plan";
        for (ResearchLayer researchLayer : plan.getLayers()) {
            List<String> taskNodeIds = new ArrayList<String>();
            for (ResearchTask researchTask : researchLayer.getTasks()) {
                String taskNodeId = buildTaskNodeId(researchLayer.getLayerIndex(), researchTask.getTaskId());
                taskNodeIds.add(taskNodeId);
                stateGraph.addNode(
                        taskNodeId,
                        AsyncNodeAction.node_async(state -> executeResearchTask(state, researchLayer, researchTask))
                );
                stateGraph.addEdge(previousNode, taskNodeId);
            }
            String summarizeNodeId = buildSummarizeNodeId(researchLayer.getLayerIndex());
            stateGraph.addNode(
                    summarizeNodeId,
                    AsyncNodeAction.node_async(state -> summarizeLayer(state, researchLayer))
            );
            stateGraph.addEdge(taskNodeIds, summarizeNodeId);
            previousNode = summarizeNodeId;
        }
        stateGraph.addNode("synthesize_answer", AsyncNodeAction.node_async(this::synthesizeAnswer));
        stateGraph.addEdge(StateGraph.START, "initialize_plan");
        stateGraph.addEdge(previousNode, "synthesize_answer");
        stateGraph.addEdge("synthesize_answer", StateGraph.END);
        return stateGraph;
    }

    private Map<String, Object> initializePlan(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        DeepResearchState state = deepResearchStateMapper.fromMap(overAllState.data());
        state.setCurrentLayerIndex(0);
        return deepResearchStateMapper.toDeltaMap(state);
    }

    private Map<String, Object> executeResearchTask(
            com.alibaba.cloud.ai.graph.OverAllState overAllState,
            ResearchLayer researchLayer,
            ResearchTask researchTask
    ) {
        DeepResearchState state = deepResearchStateMapper.fromMap(overAllState.data());
        DeepResearchExecutionContext executionContext = deepResearchExecutionRegistry.get(state.getQueryId());
        LayerSummary previousLayerSummary = loadPreviousLayerSummary(state, researchLayer.getLayerIndex());
        List<EvidenceCard> preferredCards = loadPreferredCards(state.getQueryId(), researchLayer.getLayerIndex(), researchTask);
        EvidenceCard evidenceCard = deepResearchResearcherService.research(
                state.getQueryId(),
                researchTask,
                researchLayer.getLayerIndex(),
                previousLayerSummary,
                preferredCards,
                executionContext
        );
        deepResearchWorkingSetStore.saveTaskResults(
                state.getQueryId(),
                taskSlotKey(researchLayer.getLayerIndex(), researchTask.getTaskId()),
                List.of(evidenceCard)
        );
        return Map.of();
    }

    private Map<String, Object> summarizeLayer(
            com.alibaba.cloud.ai.graph.OverAllState overAllState,
            ResearchLayer researchLayer
    ) {
        DeepResearchState state = deepResearchStateMapper.fromMap(overAllState.data());
        EvidenceLedger evidenceLedger = loadEvidenceLedger(state.getQueryId());
        List<EvidenceCard> layerCards = new ArrayList<EvidenceCard>();
        for (ResearchTask researchTask : researchLayer.getTasks()) {
            layerCards.addAll(loadTaskCards(state.getQueryId(), researchLayer.getLayerIndex(), researchTask.getTaskId()));
        }
        evidenceLedger.addCards(layerCards);
        String ledgerRef = deepResearchWorkingSetStore.saveEvidenceLedger(state.getQueryId(), evidenceLedger);
        state.setLedgerRef(ledgerRef);
        LayerSummary layerSummary = new LayerSummary();
        layerSummary.setLayerIndex(researchLayer.getLayerIndex());
        layerSummary.setSummaryMarkdown(buildLayerSummaryMarkdown(layerCards));
        for (ResearchTask researchTask : researchLayer.getTasks()) {
            layerSummary.getTaskIds().add(researchTask.getTaskId());
            String taskResultRef = taskResultRef(state.getQueryId(), researchLayer.getLayerIndex(), researchTask.getTaskId());
            if (!state.getTaskResultRefs().contains(taskResultRef)) {
                state.getTaskResultRefs().add(taskResultRef);
            }
        }
        for (EvidenceCard evidenceCard : layerCards) {
            layerSummary.getEvidenceIds().add(evidenceCard.getEvidenceId());
            layerSummary.setGapCount(layerSummary.getGapCount() + evidenceCard.getGaps().size());
        }
        String layerSummaryRef = deepResearchWorkingSetStore.saveLayerSummary(
                state.getQueryId(),
                researchLayer.getLayerIndex(),
                layerSummary
        );
        state.getLayerSummaryRefs().add(layerSummaryRef);
        state.setCurrentLayerIndex(researchLayer.getLayerIndex() + 1);
        state.setEvidenceCardCount(evidenceLedger.cardCount());
        state.setHasConflicts(evidenceLedger.hasConflicts());
        DeepResearchExecutionContext executionContext = deepResearchExecutionRegistry.get(state.getQueryId());
        if (executionContext != null) {
            state.setLlmCallBudgetRemaining(executionContext.remainingLlmCalls());
            state.setTimedOut(executionContext.isTimedOut());
            state.setPartialAnswer(executionContext.isTimedOut());
        }
        return deepResearchStateMapper.toDeltaMap(state);
    }

    private Map<String, Object> synthesizeAnswer(com.alibaba.cloud.ai.graph.OverAllState overAllState) {
        DeepResearchState state = deepResearchStateMapper.fromMap(overAllState.data());
        EvidenceLedger evidenceLedger = loadEvidenceLedger(state.getQueryId());
        List<LayerSummary> layerSummaries = loadLayerSummaries(state.getLayerSummaryRefs());
        DeepResearchSynthesisResult synthesisResult = deepResearchSynthesizer.synthesize(
                state.getQuestion(),
                layerSummaries,
                evidenceLedger
        );
        String internalDraftMarkdown = "";
        if (synthesisResult != null && synthesisResult.getInternalAnswerDraft() != null) {
            internalDraftMarkdown = synthesisResult.getInternalAnswerDraft().getDraftMarkdown();
        }
        else if (synthesisResult != null) {
            internalDraftMarkdown = synthesisResult.getAnswerMarkdown();
        }
        if (internalDraftMarkdown == null) {
            internalDraftMarkdown = "";
        }
        AnswerProjectionBundle answerProjectionBundle = resolveAnswerProjectionBundle(synthesisResult);
        state.setInternalAnswerDraftRef(queryWorkingSetStore.saveAnswer(state.getQueryId(), internalDraftMarkdown));
        state.setProjectionRef(deepResearchWorkingSetStore.saveAnswerProjectionBundle(
                state.getQueryId(),
                answerProjectionBundle
        ));
        CitationCheckReport citationCheckReport = synthesisResult == null ? null : synthesisResult.getCitationCheckReport();
        if (citationCheckReport != null) {
            state.setCitationCheckReportRef(
                    queryWorkingSetStore.saveCitationCheckReport(state.getQueryId(), citationCheckReport)
            );
        }
        state.setPartialAnswer(synthesisResult == null
                || synthesisResult.isPartialAnswer()
                || hasNoProjection(answerProjectionBundle)
                || state.isTimedOut());
        state.setHasConflicts(synthesisResult != null && synthesisResult.isHasConflicts());
        state.setEvidenceCardCount(synthesisResult == null ? 0 : synthesisResult.getEvidenceCardCount());
        DeepResearchExecutionContext executionContext = deepResearchExecutionRegistry.get(state.getQueryId());
        if (executionContext != null) {
            state.setLlmCallBudgetRemaining(executionContext.remainingLlmCalls());
            state.setTimedOut(executionContext.isTimedOut());
        }
        return deepResearchStateMapper.toDeltaMap(state);
    }

    /**
     * 解析安全出站 projection bundle，禁止内部 ev#N 泄漏到最终答案。
     *
     * @param synthesisResult 综合结果
     * @return 可出站的投影包
     */
    private AnswerProjectionBundle resolveAnswerProjectionBundle(DeepResearchSynthesisResult synthesisResult) {
        if (synthesisResult == null || synthesisResult.getAnswerProjectionBundle() == null) {
            return insufficientProjectionBundle();
        }
        AnswerProjectionBundle answerProjectionBundle = synthesisResult.getAnswerProjectionBundle();
        String answerMarkdown = answerProjectionBundle.getAnswerMarkdown();
        if (answerMarkdown == null || answerMarkdown.isBlank() || containsInternalEvidenceId(answerMarkdown)) {
            return insufficientProjectionBundle();
        }
        return answerProjectionBundle;
    }

    /**
     * 判断投影包是否没有任何可见 projection。
     *
     * @param answerProjectionBundle 投影包
     * @return 没有 projection 时返回 true
     */
    private boolean hasNoProjection(AnswerProjectionBundle answerProjectionBundle) {
        return answerProjectionBundle == null
                || answerProjectionBundle.getProjections() == null
                || answerProjectionBundle.getProjections().isEmpty();
    }

    /**
     * 判断答案中是否仍包含内部证据号。
     *
     * @param answerMarkdown 答案 Markdown
     * @return 包含内部 ev#N 时返回 true
     */
    private boolean containsInternalEvidenceId(String answerMarkdown) {
        return answerMarkdown != null && answerMarkdown.matches("(?s).*\\bev#\\d+\\b.*");
    }

    /**
     * 构造 projection 失败时的安全出站答案。
     *
     * @return 安全投影包
     */
    private AnswerProjectionBundle insufficientProjectionBundle() {
        return new AnswerProjectionBundle(
                "当前证据不足，无法生成可核验引用版答案",
                List.of()
        );
    }

    private LayerSummary loadPreviousLayerSummary(DeepResearchState state, int layerIndex) {
        if (layerIndex <= 0 || state.getLayerSummaryRefs().size() < layerIndex) {
            return null;
        }
        return deepResearchWorkingSetStore.loadLayerSummary(state.getLayerSummaryRefs().get(layerIndex - 1));
    }

    private List<LayerSummary> loadLayerSummaries(List<String> refs) {
        List<LayerSummary> layerSummaries = new ArrayList<LayerSummary>();
        for (String ref : refs) {
            LayerSummary layerSummary = deepResearchWorkingSetStore.loadLayerSummary(ref);
            if (layerSummary != null) {
                layerSummaries.add(layerSummary);
            }
        }
        return layerSummaries;
    }

    private List<EvidenceCard> loadPreferredCards(String queryId, int layerIndex, ResearchTask researchTask) {
        List<EvidenceCard> preferredCards = new ArrayList<EvidenceCard>();
        if (layerIndex <= 0 || researchTask.getPreferredUpstreamTaskIds().isEmpty()) {
            return preferredCards;
        }
        for (String preferredTaskId : researchTask.getPreferredUpstreamTaskIds()) {
            preferredCards.addAll(loadTaskCards(queryId, layerIndex - 1, preferredTaskId));
        }
        return preferredCards;
    }

    private List<EvidenceCard> loadTaskCards(String queryId, int layerIndex, String taskId) {
        return deepResearchWorkingSetStore.loadTaskResults(taskResultRef(queryId, layerIndex, taskId));
    }

    private String taskResultRef(String queryId, int layerIndex, String taskId) {
        return queryId + ":task-results-" + taskSlotKey(layerIndex, taskId);
    }

    private EvidenceLedger loadEvidenceLedger(String queryId) {
        EvidenceLedger evidenceLedger = deepResearchWorkingSetStore.loadEvidenceLedger(queryId + ":evidence-ledger");
        if (evidenceLedger != null) {
            return evidenceLedger;
        }
        return new EvidenceLedger();
    }

    private String buildTaskNodeId(int layerIndex, String taskId) {
        return "research_layer_" + layerIndex + "_" + taskId;
    }

    private String buildSummarizeNodeId(int layerIndex) {
        return "summarize_layer_" + layerIndex;
    }

    private String taskSlotKey(int layerIndex, String taskId) {
        return "layer-" + layerIndex + "-" + taskId;
    }

    private String buildLayerSummaryMarkdown(List<EvidenceCard> layerCards) {
        if (layerCards.isEmpty()) {
            return "当前层未取得有效证据";
        }
        StringBuilder summaryBuilder = new StringBuilder();
        for (EvidenceCard layerCard : layerCards) {
            if (layerCard.getFactFindings() != null && !layerCard.getFactFindings().isEmpty()) {
                summaryBuilder.append(layerCard.getTaskId())
                        .append("：")
                        .append(resolveFindingClaim(layerCard.getFactFindings().get(0)))
                        .append("；");
            }
            else if (!layerCard.getGaps().isEmpty()) {
                summaryBuilder.append(layerCard.getTaskId())
                        .append("：证据缺口=")
                        .append(String.join(",", layerCard.getGaps()))
                        .append("；");
            }
        }
        if (summaryBuilder.length() == 0) {
            return "当前层复用上一层证据做综合，不新增独立 finding";
        }
        return summaryBuilder.toString();
    }

    private String resolveFindingClaim(FactFinding factFinding) {
        if (factFinding == null || factFinding.getClaimText() == null || factFinding.getClaimText().isBlank()) {
            return "";
        }
        return factFinding.getClaimText().trim();
    }
}
