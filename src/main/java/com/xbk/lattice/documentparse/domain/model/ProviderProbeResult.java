package com.xbk.lattice.documentparse.domain.model;

import lombok.Getter;

/**
 * Provider 探测结果。
 *
 * <p>承载连接测试或连通性探测的统一结果——含成功标志、供应商类型、延迟和端点信息。
 *
 * @author xiexu
 */
@Getter
public class ProviderProbeResult {

    /** 探测是否成功。 */
    private final boolean success;

    /** 供应商类型。 */
    private final String providerType;

    /**
     * 探测延迟（毫秒）。
     *
     * <p>为 null 表示探测失败未获得延迟数据。</p>
     */
    private final Long latencyMs;

    /** 实际命中的 API 端点 URL。 */
    private final String endpoint;

    /**
     * 结果说明。
     *
     * <p>失败时含错误原因，用于前端展示诊断信息。</p>
     */
    private final String message;

    public ProviderProbeResult(
            boolean success, String providerType, Long latencyMs, String endpoint, String message
    ) {
        this.success = success;
        this.providerType = providerType;
        this.latencyMs = latencyMs;
        this.endpoint = endpoint;
        this.message = message;
    }
}
