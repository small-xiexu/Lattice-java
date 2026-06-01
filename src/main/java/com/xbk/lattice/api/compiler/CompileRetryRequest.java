package com.xbk.lattice.api.compiler;

/**
 * 编译重试请求。
 *
 * <p>承载基于 jobId 的编译重试参数，由 Spring MVC 从 JSON 请求体绑定。
 * 用于对已完成的编译任务发起重新编译。
 *
 * @author xiexu
 */
public class CompileRetryRequest {

    /**
     * 编译作业标识。
     *
     * <p>对应之前某次编译任务的 jobId，系统会根据该 jobId 找到原始编译配置
     * 并在此基础上重新执行编译流程。</p>
     */
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
