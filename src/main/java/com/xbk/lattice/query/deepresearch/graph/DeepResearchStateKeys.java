package com.xbk.lattice.query.deepresearch.graph;

/**
 * Deep Research 状态键
 *
 * 职责：集中管理 DeepResearchState 对应的 Graph Map 键名
 *
 * @author xiexu
 */
public final class DeepResearchStateKeys {

    public static final String QUERY_ID = "queryId";

    public static final String QUESTION = "question";

    public static final String LLM_SCOPE_TYPE = "llmScopeType";

    public static final String LLM_SCOPE_ID = "llmScopeId";

    public static final String ROUTE_REASON = "routeReason";

    public static final String PLAN_REF = "planRef";

    public static final String CURRENT_LAYER_INDEX = "currentLayerIndex";

    public static final String LAYER_SUMMARY_REFS = "layerSummaryRefs";

    public static final String DRAFT_ANSWER_REF = "draftAnswerRef";

    public static final String CITATION_CHECK_REPORT_REF = "citationCheckReportRef";

    public static final String FINAL_RESPONSE_REF = "finalResponseRef";

    public static final String LLM_CALL_BUDGET_REMAINING = "llmCallBudgetRemaining";

    public static final String TIMED_OUT = "timedOut";

    public static final String PARTIAL_ANSWER = "partialAnswer";

    public static final String HAS_CONFLICTS = "hasConflicts";

    public static final String EVIDENCE_CARD_COUNT = "evidenceCardCount";

    private DeepResearchStateKeys() {
    }
}
