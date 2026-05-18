package com.xbk.lattice.compiler.service;

import java.nio.file.Path;
import java.util.Locale;

/**
 * 编译执行请求
 *
 * 职责：承载 compile job 在进入具体编排器前的稳定执行上下文
 *
 * @author xiexu
 */
public class CompileExecutionRequest {

    public static final String REVIEW_MODE_RULE_BASED = "RULE_BASED";

    public static final String REVIEW_MODE_LLM = "LLM";

    private final String jobId;

    private final Path sourceDir;

    private final boolean incremental;

    private final String orchestrationMode;

    private final Long sourceId;

    private final String sourceCode;

    private final Long sourceSyncRunId;

    private final String reviewMode;

    /**
     * 创建编译执行请求。
     *
     * @param jobId 作业标识
     * @param sourceDir 源目录
     * @param incremental 是否增量编译
     * @param orchestrationMode 编排模式
     * @param sourceId 资料源主键
     * @param sourceCode 资料源编码
     * @param sourceSyncRunId 资料源同步运行主键
     */
    public CompileExecutionRequest(
            String jobId,
            Path sourceDir,
            boolean incremental,
            String orchestrationMode,
            Long sourceId,
            String sourceCode,
            Long sourceSyncRunId
    ) {
        this(jobId, sourceDir, incremental, orchestrationMode, sourceId, sourceCode, sourceSyncRunId, null);
    }

    /**
     * 创建编译执行请求。
     *
     * @param jobId 作业标识
     * @param sourceDir 源目录
     * @param incremental 是否增量编译
     * @param orchestrationMode 编排模式
     * @param sourceId 资料源主键
     * @param sourceCode 资料源编码
     * @param sourceSyncRunId 资料源同步运行主键
     * @param reviewMode 审查模式
     */
    public CompileExecutionRequest(
            String jobId,
            Path sourceDir,
            boolean incremental,
            String orchestrationMode,
            Long sourceId,
            String sourceCode,
            Long sourceSyncRunId,
            String reviewMode
    ) {
        this.jobId = jobId;
        this.sourceDir = sourceDir;
        this.incremental = incremental;
        this.orchestrationMode = orchestrationMode;
        this.sourceId = sourceId;
        this.sourceCode = sourceCode;
        this.sourceSyncRunId = sourceSyncRunId;
        this.reviewMode = normalizeReviewMode(reviewMode);
    }

    /**
     * 规范化审查模式。
     *
     * @param reviewMode 原始审查模式
     * @return 规范化审查模式
     */
    public static String normalizeReviewMode(String reviewMode) {
        if (reviewMode == null || reviewMode.isBlank()) {
            return REVIEW_MODE_RULE_BASED;
        }
        String normalizedReviewMode = reviewMode.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (REVIEW_MODE_LLM.equals(normalizedReviewMode)) {
            return REVIEW_MODE_LLM;
        }
        return REVIEW_MODE_RULE_BASED;
    }

    /**
     * 规范化新编译作业的审查模式。
     *
     * @param reviewMode 原始审查模式
     * @return 新编译作业审查模式
     */
    public static String normalizeNewJobReviewMode(String reviewMode) {
        if (reviewMode == null || reviewMode.isBlank()) {
            return REVIEW_MODE_LLM;
        }
        return normalizeReviewMode(reviewMode);
    }

    /**
     * 判断是否为 LLM 审查模式。
     *
     * @param reviewMode 审查模式
     * @return 是否为 LLM 审查
     */
    public static boolean isLlmReviewMode(String reviewMode) {
        return REVIEW_MODE_LLM.equals(normalizeReviewMode(reviewMode));
    }

    /**
     * 返回作业标识。
     *
     * @return 作业标识
     */
    public String getJobId() {
        return jobId;
    }

    /**
     * 返回源目录。
     *
     * @return 源目录
     */
    public Path getSourceDir() {
        return sourceDir;
    }

    /**
     * 返回是否增量编译。
     *
     * @return 是否增量编译
     */
    public boolean isIncremental() {
        return incremental;
    }

    /**
     * 返回编排模式。
     *
     * @return 编排模式
     */
    public String getOrchestrationMode() {
        return orchestrationMode;
    }

    /**
     * 返回资料源主键。
     *
     * @return 资料源主键
     */
    public Long getSourceId() {
        return sourceId;
    }

    /**
     * 返回资料源编码。
     *
     * @return 资料源编码
     */
    public String getSourceCode() {
        return sourceCode;
    }

    /**
     * 返回资料源同步运行主键。
     *
     * @return 资料源同步运行主键
     */
    public Long getSourceSyncRunId() {
        return sourceSyncRunId;
    }

    /**
     * 返回审查模式。
     *
     * @return 审查模式
     */
    public String getReviewMode() {
        return reviewMode;
    }
}
