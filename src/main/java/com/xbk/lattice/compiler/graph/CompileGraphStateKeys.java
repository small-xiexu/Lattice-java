package com.xbk.lattice.compiler.graph;

/**
 * 编译图状态键常量
 *
 * 职责：统一定义 CompileGraphState 与 OverAllState 之间的字段映射键
 *
 * @author xiexu
 */
public final class CompileGraphStateKeys {

    public static final String JOB_ID = "jobId";

    public static final String SOURCE_DIR = "sourceDir";

    public static final String SOURCE_ID = "sourceId";

    public static final String SOURCE_CODE = "sourceCode";

    public static final String SOURCE_SYNC_RUN_ID = "sourceSyncRunId";

    public static final String COMPILE_MODE = "compileMode";

    public static final String ORCHESTRATION_MODE = "orchestrationMode";

    public static final String RAW_SOURCES_REF = "rawSourcesRef";

    public static final String GROUPED_SOURCES_REF = "groupedSourcesRef";

    public static final String SOURCE_BATCHES_REF = "sourceBatchesRef";

    public static final String ANALYZED_CONCEPTS_REF = "analyzedConceptsRef";

    public static final String MERGED_CONCEPTS_REF = "mergedConceptsRef";

    public static final String ENHANCEMENT_CONCEPTS_REF = "enhancementConceptsRef";

    public static final String CONCEPTS_TO_CREATE_REF = "conceptsToCreateRef";

    public static final String DRAFT_ARTICLES_REF = "draftArticlesRef";

    public static final String REVIEWED_ARTICLES_REF = "reviewedArticlesRef";

    public static final String REVIEW_PARTITION_REF = "reviewPartitionRef";

    public static final String ACCEPTED_ARTICLES_REF = "acceptedArticlesRef";

    public static final String NEEDS_HUMAN_REVIEW_ARTICLES_REF = "needsHumanReviewArticlesRef";

    public static final String SOURCE_FILE_IDS_BY_PATH = "sourceFileIdsByPath";

    public static final String PERSISTED_ARTICLE_IDS = "persistedArticleIds";

    public static final String CONCEPT_COUNT = "conceptCount";

    public static final String PENDING_REVIEW_COUNT = "pendingReviewCount";

    public static final String ACCEPTED_COUNT = "acceptedCount";

    public static final String NEEDS_HUMAN_REVIEW_COUNT = "needsHumanReviewCount";

    public static final String PERSISTED_COUNT = "persistedCount";

    public static final String HAS_ENHANCEMENTS = "hasEnhancements";

    public static final String HAS_CREATES = "hasCreates";

    public static final String NOTHING_TO_DO = "nothingToDo";

    public static final String AUTO_FIX_ENABLED = "autoFixEnabled";

    public static final String ALLOW_PERSIST_NEEDS_HUMAN_REVIEW = "allowPersistNeedsHumanReview";

    public static final String COMPILE_ROUTE = "compileRoute";

    public static final String REVIEW_ROUTE = "reviewRoute";

    public static final String FIX_ROUTE = "fixRoute";

    public static final String LLM_BINDING_SNAPSHOT_REF = "llmBindingSnapshotRef";

    public static final String FIX_ATTEMPT_COUNT = "fixAttemptCount";

    public static final String MAX_FIX_ROUNDS = "maxFixRounds";

    public static final String SYNTHESIS_REQUIRED = "synthesisRequired";

    public static final String SNAPSHOT_REQUIRED = "snapshotRequired";

    public static final String STEP_LOG_FAILURE_MODE = "stepLogFailureMode";

    public static final String STEP_SUMMARIES = "stepSummaries";

    public static final String ERRORS = "errors";

    private CompileGraphStateKeys() {
    }
}
