package com.xbk.lattice.compiler.service;

import java.nio.file.Path;

/**
 * 编译执行请求
 *
 * 职责：承载 compile job 在进入具体编排器前的稳定执行上下文
 *
 * @author xiexu
 */
public class CompileExecutionRequest {

    private final String jobId;

    private final Path sourceDir;

    private final boolean incremental;

    private final String orchestrationMode;

    private final Long sourceId;

    private final String sourceCode;

    private final Long sourceSyncRunId;

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
        this.jobId = jobId;
        this.sourceDir = sourceDir;
        this.incremental = incremental;
        this.orchestrationMode = orchestrationMode;
        this.sourceId = sourceId;
        this.sourceCode = sourceCode;
        this.sourceSyncRunId = sourceSyncRunId;
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
}
