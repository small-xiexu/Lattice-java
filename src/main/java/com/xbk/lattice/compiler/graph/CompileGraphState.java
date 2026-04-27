package com.xbk.lattice.compiler.graph;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 编译图状态
 *
 * 职责：承载 StateGraph 编译执行过程中的轻量状态、路由标记与工作集引用
 *
 * @author xiexu
 */
@Data
public class CompileGraphState {

    private String jobId;

    private String sourceDir;

    private Long sourceId;

    private String sourceCode;

    private Long sourceSyncRunId;

    private String traceId;

    private String spanId;

    private String rootTraceId;

    private String compileMode;

    private String orchestrationMode;

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

    private java.util.Map<String, Long> sourceFileIdsByPath = new java.util.LinkedHashMap<String, Long>();

    private List<String> persistedArticleIds = new ArrayList<String>();

    private int conceptCount;

    private int pendingReviewCount;

    private int acceptedCount;

    private int needsHumanReviewCount;

    private int persistedCount;

    private boolean hasEnhancements;

    private boolean hasCreates;

    private boolean nothingToDo;

    private boolean autoFixEnabled;

    private boolean allowPersistNeedsHumanReview;

    private String humanReviewSeverityThreshold;

    private String compileRoute;

    private String reviewRoute;

    private String fixRoute;

    private String llmBindingSnapshotRef;

    private String astExtractReportRef;

    private int fixAttemptCount;

    private int maxFixRounds;

    private boolean synthesisRequired;

    private boolean snapshotRequired;

    private String stepLogFailureMode;

    private int graphEntityUpsertCount;

    private int graphFactUpsertCount;

    private int graphRelationUpsertCount;

    private List<String> stepSummaries = new ArrayList<String>();

    private List<String> errors = new ArrayList<String>();
}
