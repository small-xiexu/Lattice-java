package com.xbk.lattice.documentparse.domain.model;

import java.util.List;
import java.util.Set;

/**
 * Provider 描述元数据
 *
 * 职责：向后台动态表单暴露供应商默认值、支持能力和字段定义
 *
 * @author xiexu
 */
public class ProviderDescriptor {

    private final String providerType;

    private final String displayName;

    private final String defaultBaseUrl;

    private final Set<ParseCapability> supportedCapabilities;

    private final List<ProviderFieldDescriptor> credentialFields;

    private final List<ProviderFieldDescriptor> configFields;

    private final String probeMode;

    /**
     * 创建 Provider 描述元数据。
     *
     * @param providerType 供应商类型
     * @param displayName 展示名称
     * @param defaultBaseUrl 默认基础地址
     * @param supportedCapabilities 支持能力
     * @param credentialFields 凭证字段
     * @param configFields 配置字段
     * @param probeMode 连接探测模式
     */
    public ProviderDescriptor(
            String providerType,
            String displayName,
            String defaultBaseUrl,
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

    /**
     * 返回供应商类型。
     *
     * @return 供应商类型
     */
    public String getProviderType() {
        return providerType;
    }

    /**
     * 返回展示名称。
     *
     * @return 展示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 返回默认基础地址。
     *
     * @return 默认基础地址
     */
    public String getDefaultBaseUrl() {
        return defaultBaseUrl;
    }

    /**
     * 返回支持能力集合。
     *
     * @return 支持能力集合
     */
    public Set<ParseCapability> getSupportedCapabilities() {
        return supportedCapabilities;
    }

    /**
     * 返回凭证字段定义。
     *
     * @return 凭证字段定义
     */
    public List<ProviderFieldDescriptor> getCredentialFields() {
        return credentialFields;
    }

    /**
     * 返回扩展配置字段定义。
     *
     * @return 扩展配置字段定义
     */
    public List<ProviderFieldDescriptor> getConfigFields() {
        return configFields;
    }

    /**
     * 返回连接探测模式。
     *
     * @return 连接探测模式
     */
    public String getProbeMode() {
        return probeMode;
    }
}
