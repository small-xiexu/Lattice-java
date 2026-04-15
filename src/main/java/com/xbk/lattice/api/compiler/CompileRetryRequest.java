package com.xbk.lattice.api.compiler;

/**
 * 编译重试请求
 *
 * 职责：承载基于 jobId 的编译重试参数
 *
 * @author xiexu
 */
public class CompileRetryRequest {

    private String jobId;

    /**
     * 获取作业标识。
     *
     * @return 作业标识
     */
    public String getJobId() {
        return jobId;
    }

    /**
     * 设置作业标识。
     *
     * @param jobId 作业标识
     */
    public void setJobId(String jobId) {
        this.jobId = jobId;
    }
}
