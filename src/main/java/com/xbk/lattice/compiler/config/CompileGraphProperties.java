package com.xbk.lattice.compiler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 编译图配置
 *
 * 职责：承载 Graph 相关开关、步骤日志与失败策略参数
 *
 * @author xiexu
 */
@ConfigurationProperties(prefix = "lattice.compiler.graph")
public class CompileGraphProperties {

    private boolean enabled = true;

    private boolean allowServiceFallback = true;

    private boolean persistStepLog = true;

    private String stepLogFailureMode = "warn";

    /**
     * 是否启用 Graph。
     *
     * @return 是否启用 Graph
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用 Graph。
     *
     * @param enabled 是否启用 Graph
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 是否允许回退到 service。
     *
     * @return 是否允许回退到 service
     */
    public boolean isAllowServiceFallback() {
        return allowServiceFallback;
    }

    /**
     * 设置是否允许回退到 service。
     *
     * @param allowServiceFallback 是否允许回退到 service
     */
    public void setAllowServiceFallback(boolean allowServiceFallback) {
        this.allowServiceFallback = allowServiceFallback;
    }

    /**
     * 是否持久化步骤日志。
     *
     * @return 是否持久化步骤日志
     */
    public boolean isPersistStepLog() {
        return persistStepLog;
    }

    /**
     * 设置是否持久化步骤日志。
     *
     * @param persistStepLog 是否持久化步骤日志
     */
    public void setPersistStepLog(boolean persistStepLog) {
        this.persistStepLog = persistStepLog;
    }

    /**
     * 获取步骤日志失败模式。
     *
     * @return 步骤日志失败模式
     */
    public String getStepLogFailureMode() {
        return stepLogFailureMode;
    }

    /**
     * 设置步骤日志失败模式。
     *
     * @param stepLogFailureMode 步骤日志失败模式
     */
    public void setStepLogFailureMode(String stepLogFailureMode) {
        this.stepLogFailureMode = stepLogFailureMode;
    }
}
