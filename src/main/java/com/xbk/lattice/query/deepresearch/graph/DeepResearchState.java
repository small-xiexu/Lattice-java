package com.xbk.lattice.query.deepresearch.graph;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Deep Research 图状态。
 *
 * <p>承载深度研究图执行过程中的轻量状态字段与工作集引用——由 Graph 框架通过 setter 注入。
 * 各 Ref 字段是工作集/审计对象的引用键，非大对象本体。
 *
 * @author xiexu
 */
@Getter
@Setter
public class DeepResearchState {

    // ── 运行上下文 ──
    /** 查询会话标识。 */
    private String queryId;
    /** 用户原始问题。 */
    private String question;
    /** LLM 作用域类型。 */
    private String llmScopeType;
    /** LLM 作用域标识。 */
    private String llmScopeId;
    /** 路由原因（记录为何进入 deep research 分支）。 */
    private String routeReason;

    // ── 计划与执行 ──
    /** 分层研究计划引用（工作集键）。 */
    private String planRef;
    /** 各任务执行结果引用列表（累积）。 */
    private List<String> taskResultRefs = new ArrayList<String>();
    /** 证据账本引用。 */
    private String ledgerRef;
    /** 当前执行层序号（从 0 开始）。 */
    private int currentLayerIndex;
    /** 各层摘要引用列表（累积）。 */
    private List<String> layerSummaryRefs = new ArrayList<String>();

    // ── 综合与投影 ──
    /** 内部答案草稿引用。 */
    private String internalAnswerDraftRef;
    /** 投影结果引用。 */
    private String projectionRef;
    /** 引用检查报告引用。 */
    private String citationCheckReportRef;
    /** 答案审计引用。 */
    private String answerAuditRef;

    // ── 执行控制 ──
    /** LLM 调用预算剩余次数。 */
    private int llmCallBudgetRemaining;
    /** 是否已超时。 */
    private boolean timedOut;
    /** 是否为部分答案。 */
    private boolean partialAnswer;
    /** 是否存在事实冲突。 */
    private boolean hasConflicts;
    /** 产生的证据卡总数。 */
    private int evidenceCardCount;
    /** 投影修复重试次数。 */
    private int projectionRetryCount;
}
