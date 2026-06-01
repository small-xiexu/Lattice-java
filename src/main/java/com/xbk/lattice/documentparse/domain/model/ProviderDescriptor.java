package com.xbk.lattice.documentparse.domain.model;

import lombok.Getter;

import java.util.List;
import java.util.Set;

/**
 * Provider 描述元数据。
 *
 * <p>向后台动态表单暴露供应商默认值、支持能力和字段定义——前端据此生成
 * 凭证配置表单和扩展配置表单。
 *
 * @author xiexu
 */
@Getter
public class ProviderDescriptor {

    /** 供应商类型标识（如 tencent_ocr / aliyun_ocr）。 */
    private final String providerType;

    /** 供应商展示名称。 */
    private final String displayName;

    /** 默认 API 端点 URL。 */
    private final String defaultBaseUrl;

    /** 支持的能力集合（{@link ParseCapability}）。决定路由时可选此 Provider 的文档类型。 */
    private final Set<ParseCapability> supportedCapabilities;

    /**
     * 凭证字段定义列表。
     *
     * <p>前端据此动态生成凭证配置表单（如 API Key、Token 等输入框）。</p>
     */
    private final List<ProviderFieldDescriptor> credentialFields;

    /**
     * 扩展配置字段定义列表。
     *
     * <p>前端据此动态生成扩展配置表单（如超时、重试次数等参数）。</p>
     */
    private final List<ProviderFieldDescriptor> configFields;

    /** 连接探测模式（定义连接测试的探测方式）。 */
    private final String probeMode;

    public ProviderDescriptor(
            String providerType, String displayName, String defaultBaseUrl,
            Set<ParseCapability> supportedCapabilities,
            List<ProviderFieldDescriptor> credentialFields,
            List<ProviderFieldDescriptor> configFields,
            String probeMode
    ) {
        this.providerType = providerType;
        this.displayName = displayName;
        this.defaultBaseUrl = defaultBaseUrl;
        this.supportedCapabilities = supportedCapabilities;
        this.credentialFields = credentialFields;
        this.configFields = configFields;
        this.probeMode = probeMode;
    }
}
