package com.xbk.lattice.api.admin;

import com.xbk.lattice.documentparse.domain.model.ParseCapability;
import com.xbk.lattice.documentparse.domain.model.ProviderDescriptor;
import com.xbk.lattice.documentparse.domain.model.ProviderFieldDescriptor;
import com.xbk.lattice.documentparse.service.DocumentParseProviderDescriptorService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理侧文档解析 Provider Descriptor 控制器
 *
 * 职责：向管理台返回全部内置 Provider Descriptor 元数据
 *
 * @author xiexu
 */
@RestController
@RequestMapping("/api/v1/admin/document-parse/providers")
public class AdminDocumentParseProviderDescriptorController {

    private final DocumentParseProviderDescriptorService documentParseProviderDescriptorService;

    /**
     * 创建管理侧文档解析 Provider Descriptor 控制器。
     *
     * @param documentParseProviderDescriptorService Provider Descriptor 服务
     */
    public AdminDocumentParseProviderDescriptorController(
            DocumentParseProviderDescriptorService documentParseProviderDescriptorService
    ) {
        this.documentParseProviderDescriptorService = documentParseProviderDescriptorService;
    }

    /**
     * 返回全部 Provider Descriptor。
     *
     * @return Provider Descriptor 列表
     */
    @GetMapping
    public AdminDocumentParseProviderDescriptorListResponse listProviders() {
        List<AdminDocumentParseProviderDescriptorResponse> items =
                new ArrayList<AdminDocumentParseProviderDescriptorResponse>();
        for (ProviderDescriptor descriptor : documentParseProviderDescriptorService.listDescriptors()) {
            items.add(toResponse(descriptor));
        }
        return new AdminDocumentParseProviderDescriptorListResponse(items.size(), items);
    }

    /**
     * 把 Provider Descriptor 映射为响应。
     *
     * @param descriptor Provider Descriptor
     * @return 响应
     */
    private AdminDocumentParseProviderDescriptorResponse toResponse(ProviderDescriptor descriptor) {
        List<String> supportedCapabilities = new ArrayList<String>();
        for (ParseCapability parseCapability : descriptor.getSupportedCapabilities()) {
            supportedCapabilities.add(parseCapability.name());
        }
        List<AdminDocumentParseProviderFieldResponse> credentialFields =
                new ArrayList<AdminDocumentParseProviderFieldResponse>();
        for (ProviderFieldDescriptor providerFieldDescriptor : descriptor.getCredentialFields()) {
            credentialFields.add(toFieldResponse(providerFieldDescriptor));
        }
        List<AdminDocumentParseProviderFieldResponse> configFields =
                new ArrayList<AdminDocumentParseProviderFieldResponse>();
        for (ProviderFieldDescriptor providerFieldDescriptor : descriptor.getConfigFields()) {
            configFields.add(toFieldResponse(providerFieldDescriptor));
        }
        return new AdminDocumentParseProviderDescriptorResponse(
                descriptor.getProviderType(),
                descriptor.getDisplayName(),
                descriptor.getDefaultBaseUrl(),
                descriptor.getProbeMode(),
                supportedCapabilities,
                credentialFields,
                configFields
        );
    }

    /**
     * 把字段描述映射为响应。
     *
     * @param descriptor 字段描述
     * @return 响应
     */
    private AdminDocumentParseProviderFieldResponse toFieldResponse(ProviderFieldDescriptor descriptor) {
        return new AdminDocumentParseProviderFieldResponse(
                descriptor.getFieldKey(),
                descriptor.getLabel(),
                descriptor.getInputType(),
                descriptor.isRequired(),
                descriptor.getDefaultValue(),
                descriptor.getPlaceholder(),
                descriptor.getDescription()
        );
    }

    /**
     * Provider Descriptor 列表响应。
     *
     * <p>返回管理台可用的 Provider Descriptor 列表，由 {@code listProviders()} 组装。
     *
     * @author xiexu
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminDocumentParseProviderDescriptorListResponse {

        /** 当前返回的 Provider 数量（等于 {@code items.size()}）。 */
        private int count;

        /** Provider Descriptor 列表。 */
        private List<AdminDocumentParseProviderDescriptorResponse> items;
    }

    /**
     * Provider Descriptor 响应。
     *
     * <p>返回管理台的单个 Provider 元数据——含支持的文件类型、探测模式和动态表单字段定义。
     *
     * @author xiexu
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminDocumentParseProviderDescriptorResponse {

        /** Provider 类型标识（如 {@code openai} / {@code local}）。 */
        private String providerType;

        /** Provider 展示名称。 */
        private String displayName;

        /** 默认 API 端点 URL。 */
        private String defaultBaseUrl;

        /** 探测模式（定义连接测试的探测方式）。 */
        private String probeMode;

        /** 支持的文件类型列表（取自 {@code ParseCapability} 枚举）。 */
        private List<String> supportedCapabilities;

        /**
         * 凭证字段定义列表。
         *
         * <p>前端据此动态生成凭证配置表单（如 API Key、Token 等输入框）。</p>
         */
        private List<AdminDocumentParseProviderFieldResponse> credentialFields;

        /**
         * 配置字段定义列表。
         *
         * <p>前端据此动态生成扩展配置表单（如超时、重试次数等参数）。</p>
         */
        private List<AdminDocumentParseProviderFieldResponse> configFields;
    }

    /**
     * Provider 字段响应。
     *
     * <p>返回管理台动态表单的单个字段定义——含输入类型、是否必填、默认值和提示文案，
     * 由 {@code ProviderFieldDescriptor} 映射而来。
     *
     * @author xiexu
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminDocumentParseProviderFieldResponse {

        /** 字段键（表单字段 name 属性）。 */
        private String fieldKey;

        /** 字段展示标签。 */
        private String label;

        /**
         * 输入控件类型（如 {@code text} / {@code password} / {@code select}）。
         *
         * <p>{@code password} 类型时前端应对输入值做脱敏展示。</p>
         */
        private String inputType;

        /** 是否必填。 */
        private boolean required;

        /** 默认值。可为空。 */
        private String defaultValue;

        /** 占位提示文案。可为空。 */
        private String placeholder;

        /** 字段描述说明。可为空。 */
        private String description;
    }
}
