package com.xbk.lattice.api.admin;

import com.xbk.lattice.documentparse.domain.model.ParseCapability;
import com.xbk.lattice.documentparse.domain.model.ProviderDescriptor;
import com.xbk.lattice.documentparse.domain.model.ProviderFieldDescriptor;
import com.xbk.lattice.documentparse.service.DocumentParseProviderDescriptorService;
import lombok.AllArgsConstructor;
import lombok.Data;
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
     * 职责：返回管理台可用的 Provider Descriptor 列表
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminDocumentParseProviderDescriptorListResponse {

        private int count;

        private List<AdminDocumentParseProviderDescriptorResponse> items;
    }

    /**
     * Provider Descriptor 响应。
     *
     * 职责：返回管理台的单个 Provider 元数据
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminDocumentParseProviderDescriptorResponse {

        private String providerType;

        private String displayName;

        private String defaultBaseUrl;

        private String probeMode;

        private List<String> supportedCapabilities;

        private List<AdminDocumentParseProviderFieldResponse> credentialFields;

        private List<AdminDocumentParseProviderFieldResponse> configFields;
    }

    /**
     * Provider 字段响应。
     *
     * 职责：返回管理台动态表单的单个字段定义
     *
     * @author xiexu
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminDocumentParseProviderFieldResponse {

        private String fieldKey;

        private String label;

        private String inputType;

        private boolean required;

        private String defaultValue;

        private String placeholder;

        private String description;
    }
}
