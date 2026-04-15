package com.xbk.lattice.api.admin;

/**
 * 管理侧编译作业请求
 *
 * 职责：承载 admin compile job 提交参数
 *
 * @author xiexu
 */
public class AdminCompileJobRequest {

    private String sourceDir;

    private boolean incremental;

    private Boolean async = Boolean.TRUE;

    private String orchestrationMode;

    /**
     * 获取源目录。
     *
     * @return 源目录
     */
    public String getSourceDir() {
        return sourceDir;
    }

    /**
     * 设置源目录。
     *
     * @param sourceDir 源目录
     */
    public void setSourceDir(String sourceDir) {
        this.sourceDir = sourceDir;
    }

    /**
     * 获取是否增量编译。
     *
     * @return 是否增量编译
     */
    public boolean isIncremental() {
        return incremental;
    }

    /**
     * 设置是否增量编译。
     *
     * @param incremental 是否增量编译
     */
    public void setIncremental(boolean incremental) {
        this.incremental = incremental;
    }

    /**
     * 获取是否异步执行。
     *
     * @return 是否异步执行
     */
    public boolean isAsync() {
        return async == null || async.booleanValue();
    }

    /**
     * 设置是否异步执行。
     *
     * @param async 是否异步执行
     */
    public void setAsync(Boolean async) {
        this.async = async;
    }

    /**
     * 获取编排模式。
     *
     * @return 编排模式
     */
    public String getOrchestrationMode() {
        return orchestrationMode;
    }

    /**
     * 设置编排模式。
     *
     * @param orchestrationMode 编排模式
     */
    public void setOrchestrationMode(String orchestrationMode) {
        this.orchestrationMode = orchestrationMode;
    }
}
