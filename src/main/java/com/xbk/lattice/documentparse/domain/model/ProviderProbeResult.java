package com.xbk.lattice.documentparse.domain.model;

/**
 * Provider 探测结果
 *
 * 职责：承载连接测试或连通性探测的统一结果
 *
 * @author xiexu
 */
public class ProviderProbeResult {

    private final boolean success;

    private final String providerType;

    private final Long latencyMs;

    private final String endpoint;

    private final String message;

    /**
     * 创建 Provider 探测结果。
     *
     * @param success 是否成功
     * @param providerType 供应商类型
     * @param latencyMs 耗时毫秒
     * @param endpoint 命中的接口地址
     * @param message 结果说明
     */
    public ProviderProbeResult(
            boolean success,
            String providerType,
            Long latencyMs,
            String endpoint,
            String message
    ) {
        this.success = success;
        this.providerType = providerType;
        this.latencyMs = latencyMs;
        this.endpoint = endpoint;
        this.message = message;
    }

    /**
     * 返回是否成功。
     *
     * @return 是否成功
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 返回供应商类型。
     *
     * @return 供应商类型
     */
    public String getProviderType() {
        return providerType;
    }

    /**
     * 返回耗时毫秒。
     *
     * @return 耗时毫秒
     */
    public Long getLatencyMs() {
        return latencyMs;
    }

    /**
     * 返回命中的接口地址。
     *
     * @return 命中的接口地址
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * 返回结果说明。
     *
     * @return 结果说明
     */
    public String getMessage() {
        return message;
    }
}
