package com.xbk.lattice.compiler.graph;

import lombok.Getter;
import lombok.Setter;

import java.util.*;

/**
 * 编译图状态。
 *
 * <p>承载 StateGraph 编译执行过程中的轻量状态、路由标记与工作集引用——由 Graph 框架通过 setter 注入。
 * 各 Ref 字段是编译 graph 工作集引用，非大对象本体。
 *
 * @author xiexu
 */
@Getter
@Setter
public class CompileGraphState {

    // ── 编译任务标识 ──
    /** 编译作业标识。 */
    private String jobId;
    /** 源目录。 */
    private String sourceDir;
    /** 资料源主键。 */
    private Long sourceId;
    /** 资料源编码。 */
    private String sourceCode;
    /** 关联的 source sync run 主键。 */
    private Long sourceSyncRunId;
    /** traceId。 */
    private String traceId;
    /** spanId。 */
    private String spanId;
    /** 根 traceId。 */
    private String rootTraceId;

    // ── 编译模式 ──
    /** 编译模式（full / incremental）。 */
    private String compileMode;
    /** 编排模式。 */
    private String orchestrationMode;
    /** 审查模式。 */
    private String reviewMode;
    /** 内容画像（DOCUMENT / CODE_LIGHT）。 */
    private String contentProfile;

    // ── 阶段工作集引用 ──
    private String rawSourcesRef;
    private String groupedSourcesRef;
    private String sourceBatchesRef;
    private String analyzedConceptsRef;
    private String mergedConceptsRef;
    private String enhancementConceptsRef;
    private String conceptsToCreateRef;
    private String draftArticlesRef;
    private String reviewedArticlesRef;
    private String reviewPartitionRef;
    private String acceptedArticlesRef;
    private String needsHumanReviewArticlesRef;

    // ── 持久化与映射 ──
    /** 源文件路径到持久化 ID 的映射。 */
    private java.util.Map<String, Long> sourceFileIdsByPath = new LinkedHashMap<String, Long>();
    /** 已持久化的文章 ID 列表（累积）。 */
    private List<String> persistedArticleIds = new ArrayList<String>();

    // ── 审查计数 ──
    private int conceptCount;
    private int pendingReviewCount;
    private int acceptedCount;
    private int needsHumanReviewCount;
    private int persistedCount;

    // ── 审查标记 ──
    private boolean hasEnhancements;
    private boolean hasCreates;
    private boolean nothingToDo;
    private boolean autoFixEnabled;
    private boolean allowPersistNeedsHumanReview;
    private String humanReviewSeverityThreshold;

    // ── 路由 ──
    private String compileRoute;
    private String reviewRoute;
    private String fixRoute;
    private String llmBindingSnapshotRef;
    private String astExtractReportRef;

    // ── 修复轮次 ──
    private int fixAttemptCount;
    private int maxFixRounds;

    // ── 其他标记 ──
    private boolean synthesisRequired;
    private boolean snapshotRequired;
    private String stepLogFailureMode;

    // ── AST 图谱入库计数 ──
    private int graphEntityUpsertCount;
    private int graphFactUpsertCount;
    private int graphRelationUpsertCount;

    // ── 步骤与错误日志 ──
    /** 步骤摘要列表（累积）。 */
    private List<String> stepSummaries = new ArrayList<String>();
    /** 错误列表（累积）。 */
    private List<String> errors = new ArrayList<String>();
}
