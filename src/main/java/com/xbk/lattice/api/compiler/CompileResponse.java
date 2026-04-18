package com.xbk.lattice.api.compiler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 编译响应
 *
 * 职责：表示最小编译接口的返回结果
 *
 * @author xiexu
 */
public class CompileResponse {

    private final int persistedCount;

    private final String jobId;

    /**
     * 创建编译响应。
     *
     * @param persistedCount 落盘数量
     */
    public CompileResponse(int persistedCount) {
        this(persistedCount, null);
    }

    /**
     * 创建编译响应。
     *
     * @param persistedCount 落盘数量
     * @param jobId 作业标识
     */
    @JsonCreator
    public CompileResponse(
            @JsonProperty("persistedCount") int persistedCount,
            @JsonProperty("jobId") String jobId
    ) {
        this.persistedCount = persistedCount;
        this.jobId = jobId;
    }

    /**
     * 获取落盘数量。
     *
     * @return 落盘数量
     */
    public int getPersistedCount() {
        return persistedCount;
    }

    /**
     * 获取作业标识。
     *
     * @return 作业标识
     */
    public String getJobId() {
        return jobId;
    }
}
