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

    /**
     * 是否启用 Graph 编排。
     *
     * <p>默认 {@code true}。{@code false} 时编译回退到纯 service 编排，
     * 失去 Graph 的多节点并发和状态可视化能力。</p>
     */
    private boolean enabled = true;

    /**
     * 是否允许 Graph 失败时回退到 service。
     *
     * <p>默认 {@code true}（fail-open）。Graph 异常时自动降级到 service 编排，
     * 确保编译流程不中断。</p>
     */
    private boolean allowServiceFallback = true;

    /**
     * 是否持久化步骤日志。
     *
     * <p>默认 {@code true}。{@code false} 时步骤日志仅记录到内存，
     * 可能丢失审计信息。</p>
     */
    private boolean persistStepLog = true;

    /**
     * 步骤日志持久化失败时的处理模式。
     *
     * <p>默认 {@code "warn"}。{@code "warn"} 时日志写入失败仅警告，编译继续；
     * {@code "fail"} 时日志写入失败导致编译中止。</p>
     */
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
