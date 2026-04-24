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
import com.xbk.lattice.query.deepresearch.store.DeepResearchWorkingSetStore;
import com.xbk.lattice.query.graph.QueryWorkingSetStore;
import org.springframework.context.annotation.Profile;
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
@Profile("jdbc")
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
        deepResearchWorkingSetStore.saveEvidenceCards(
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
        LayerSummary layerSummary = new LayerSummary();
        layerSummary.setLayerIndex(researchLayer.getLayerIndex());
        layerSummary.setSummaryMarkdown(buildLayerSummaryMarkdown(layerCards));
        for (ResearchTask researchTask : researchLayer.getTasks()) {
            layerSummary.getTaskIds().add(researchTask.getTaskId());
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
        state.setFinalResponseRef(ledgerRef);
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
        state.setDraftAnswerRef(queryWorkingSetStore.saveAnswer(state.getQueryId(), synthesisResult.getAnswerMarkdown()));
        CitationCheckReport citationCheckReport = synthesisResult.getCitationCheckReport();
        state.setCitationCheckReportRef(
                queryWorkingSetStore.saveCitationCheckReport(state.getQueryId(), citationCheckReport)
        );
        state.setPartialAnswer(synthesisResult.isPartialAnswer() || state.isTimedOut());
        state.setHasConflicts(synthesisResult.isHasConflicts());
        state.setEvidenceCardCount(synthesisResult.getEvidenceCardCount());
        DeepResearchExecutionContext executionContext = deepResearchExecutionRegistry.get(state.getQueryId());
        if (executionContext != null) {
            state.setLlmCallBudgetRemaining(executionContext.remainingLlmCalls());
            state.setTimedOut(executionContext.isTimedOut());
        }
        return deepResearchStateMapper.toDeltaMap(state);
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
        String ref = queryId + ":evidence-cards:" + taskSlotKey(layerIndex, taskId);
        List<EvidenceCard> cards = deepResearchWorkingSetStore.loadEvidenceCards(ref);
        if (!cards.isEmpty()) {
            return cards;
        }
        return deepResearchWorkingSetStore.loadEvidenceCards(queryId + ":evidence-cards-" + taskSlotKey(layerIndex, taskId));
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
            if (!layerCard.getFindings().isEmpty()) {
                summaryBuilder.append(layerCard.getTaskId())
                        .append("：")
                        .append(layerCard.getFindings().get(0).getClaim())
                        .append("；");
            }
            else if (!layerCard.getGaps().isEmpty()) {
                summaryBuilder.append(layerCard.getTaskId())
                        .append("：证据缺口=")
                        .append(String.join(",", layerCard.getGaps()))
                        .append("；");
            }
        }
        return summaryBuilder.toString();
    }
}
