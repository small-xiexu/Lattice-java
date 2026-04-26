package com.xbk.lattice.query.deepresearch.graph;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Deep Research 图状态
 *
 * 职责：承载深度研究图执行过程中的轻量状态字段与工作集引用
 *
 * @author xiexu
 */
@Data
public class DeepResearchState {

    private String queryId;

    private String question;

    private String llmScopeType;

    private String llmScopeId;

    private String routeReason;

    private String planRef;

    private List<String> taskResultRefs = new ArrayList<String>();

    private String ledgerRef;

    private int currentLayerIndex;

    private List<String> layerSummaryRefs = new ArrayList<String>();

    private String internalAnswerDraftRef;

    private String projectionRef;

    private String citationCheckReportRef;

    private String answerAuditRef;

    private int llmCallBudgetRemaining;

    private boolean timedOut;

    private boolean partialAnswer;

    private boolean hasConflicts;

    private int evidenceCardCount;

    private int projectionRetryCount;
}
