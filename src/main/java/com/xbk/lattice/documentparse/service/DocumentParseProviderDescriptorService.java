package com.xbk.lattice.documentparse.service;

import com.xbk.lattice.documentparse.domain.model.ParseCapability;
import com.xbk.lattice.documentparse.domain.model.ProviderConnection;
import com.xbk.lattice.documentparse.domain.model.ProviderDescriptor;
import com.xbk.lattice.documentparse.domain.model.ProviderFieldDescriptor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 文档解析 Provider Descriptor 服务
 *
 * 职责：维护系统内置的供应商元数据，驱动动态连接表单与默认值
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class DocumentParseProviderDescriptorService {

    private final Map<String, ProviderDescriptor> descriptors;

    /**
     * 创建文档解析 Provider Descriptor 服务。
     */
    public DocumentParseProviderDescriptorService() {
        this.descriptors = buildDescriptorMap();
    }

    /**
     * 返回全部 Provider Descriptor。
     *
     * @return Provider Descriptor 列表
     */
    public List<ProviderDescriptor> listDescriptors() {
        return List.copyOf(descriptors.values());
    }

    /**
     * 按类型查询 Provider Descriptor。
     *
     * @param providerType 供应商类型
     * @return Provider Descriptor
     */
    public Optional<ProviderDescriptor> findDescriptor(String providerType) {
        String normalizedProviderType = normalizeProviderType(providerType);
        if (!StringUtils.hasText(normalizedProviderType)) {
            return Optional.empty();
        }
        return Optional.ofNullable(descriptors.get(normalizedProviderType));
    }

    /**
     * 规范化并校验 Provider 类型。
     *
     * @param providerType 供应商类型
     * @return 规范化后的 Provider 类型
     */
    public String requireProviderType(String providerType) {
        String normalizedProviderType = normalizeProviderType(providerType);
        if (!descriptors.containsKey(normalizedProviderType)) {
            throw new IllegalArgumentException("不支持的providerType: " + providerType);
        }
        return normalizedProviderType;
    }

    /**
     * 构造内置 Provider 描述映射。
     *
     * @return Provider 描述映射
     */
    private Map<String, ProviderDescriptor> buildDescriptorMap() {
        Map<String, ProviderDescriptor> descriptorMap = new LinkedHashMap<String, ProviderDescriptor>();
        descriptorMap.put(
                ProviderConnection.PROVIDER_TENCENT_OCR,
                new ProviderDescriptor(
                        ProviderConnection.PROVIDER_TENCENT_OCR,
                        "腾讯 OCR",
                        "",
                        supportedOcrCapabilities(),
                        Arrays.asList(
                                textField("secretId", "Secret ID", true, "", "请输入 Secret ID", "腾讯 OCR Secret ID"),
                                passwordField(
                                        "secretKey",
                                        "Secret Key",
                                        true,
                                        "",
                                        "请输入 Secret Key",
                                        "腾讯 OCR Secret Key"
                                )
                        ),
                        Collections.singletonList(
                                textField(
                                        "endpointPath",
                                        "接口路径",
                                        true,
                                        "/ocr/v1/general-basic",
                                        "例如 /ocr/v1/general-basic",
                                        "JSON Body OCR 接口路径"
                                )
                        ),
                        "json_body_sync"
                )
        );
        descriptorMap.put(
                ProviderConnection.PROVIDER_ALIYUN_OCR,
                new ProviderDescriptor(
                        ProviderConnection.PROVIDER_ALIYUN_OCR,
                        "阿里云 OCR",
                        "",
                        supportedOcrCapabilities(),
                        Arrays.asList(
                                textField(
                                        "accessKeyId",
                                        "Access Key ID",
                                        true,
                                        "",
                                        "请输入 Access Key ID",
                                        "阿里云 Access Key ID"
                                ),
                                passwordField(
                                        "accessKeySecret",
                                        "Access Key Secret",
                                        true,
                                        "",
                                        "请输入 Access Key Secret",
                                        "阿里云 Access Key Secret"
                                )
                        ),
                        Collections.singletonList(
                                textField(
                                        "endpointPath",
                                        "接口路径",
                                        true,
                                        "/ocr/v1/general",
                                        "例如 /ocr/v1/general",
                                        "JSON Body OCR 接口路径"
                                )
                        ),
                        "json_body_sync"
                )
        );
        descriptorMap.put(
                ProviderConnection.PROVIDER_GOOGLE_DOCUMENT_AI,
                new ProviderDescriptor(
                        ProviderConnection.PROVIDER_GOOGLE_DOCUMENT_AI,
                        "Google Document AI",
                        "",
                        supportedOcrCapabilities(),
                        Arrays.asList(
                                passwordField(
                                        "bearerToken",
                                        "Bearer Token",
                                        true,
                                        "",
                                        "请输入 Bearer Token",
                                        "Google Document AI 调用 Token"
                                ),
                                textField(
                                        "projectId",
                                        "Project ID",
                                        false,
                                        "",
                                        "请输入 Project ID",
                                        "如需项目级别 Header 可在此填写"
                                )
                        ),
                        Collections.singletonList(
                                textField(
                                        "endpointPath",
                                        "接口路径",
                                        true,
                                        "/v1/documents:process",
                                        "例如 /v1/documents:process",
                                        "JSON Body OCR 接口路径"
                                )
                        ),
                        "json_body_sync"
                )
        );
        descriptorMap.put(
                ProviderConnection.PROVIDER_TEXTIN_XPARSE,
                new ProviderDescriptor(
                        ProviderConnection.PROVIDER_TEXTIN_XPARSE,
                        "TextIn xParse",
                        "https://api.textin.com",
                        supportedOcrCapabilities(),
                        Arrays.asList(
                                textField("appId", "App ID", true, "", "请输入 App ID", "TextIn xParse App ID"),
                                passwordField(
                                        "secretCode",
                                        "Secret Code",
                                        true,
                                        "",
                                        "请输入 Secret Code",
                                        "TextIn xParse Secret Code"
                                )
                        ),
                        Arrays.asList(
                                textField(
                                        "endpointPath",
                                        "接口路径",
                                        true,
                                        "/api/v1/xparse/parse/sync",
                                        "例如 /api/v1/xparse/parse/sync",
                                        "TextIn 同步解析接口路径"
                                ),
                                textareaField(
                                        "parseConfigJson",
                                        "解析配置 JSON",
                                        false,
                                        "{}",
                                        "例如 {\"parse_mode\":\"scan\"}",
                                        "提交给 TextIn 的额外解析配置"
                                )
                        ),
                        "textin_multipart_sync"
                )
        );
        return descriptorMap;
    }

    /**
     * 构造 OCR 支持能力集合。
     *
     * @return OCR 支持能力集合
     */
    private LinkedHashSet<ParseCapability> supportedOcrCapabilities() {
        LinkedHashSet<ParseCapability> capabilities = new LinkedHashSet<ParseCapability>();
        capabilities.add(ParseCapability.IMAGE_OCR);
        capabilities.add(ParseCapability.SCANNED_PDF_OCR);
        return capabilities;
    }

    /**
     * 构造文本字段描述。
     *
     * @param fieldKey 字段键
     * @param label 展示标签
     * @param required 是否必填
     * @param defaultValue 默认值
     * @param placeholder 占位提示
     * @param description 字段说明
     * @return 字段描述
     */
    private ProviderFieldDescriptor textField(
            String fieldKey,
            String label,
            boolean required,
            String defaultValue,
            String placeholder,
            String description
    ) {
        return new ProviderFieldDescriptor(
                fieldKey,
                label,
                "text",
                required,
                defaultValue,
                placeholder,
                description
        );
    }

    /**
     * 构造密码字段描述。
     *
     * @param fieldKey 字段键
     * @param label 展示标签
     * @param required 是否必填
     * @param defaultValue 默认值
     * @param placeholder 占位提示
     * @param description 字段说明
     * @return 字段描述
     */
    private ProviderFieldDescriptor passwordField(
            String fieldKey,
            String label,
            boolean required,
            String defaultValue,
            String placeholder,
            String description
    ) {
        return new ProviderFieldDescriptor(
                fieldKey,
                label,
                "password",
                required,
                defaultValue,
                placeholder,
                description
        );
    }

    /**
     * 构造多行文本字段描述。
     *
     * @param fieldKey 字段键
     * @param label 展示标签
     * @param required 是否必填
     * @param defaultValue 默认值
     * @param placeholder 占位提示
     * @param description 字段说明
     * @return 字段描述
     */
    private ProviderFieldDescriptor textareaField(
            String fieldKey,
            String label,
            boolean required,
            String defaultValue,
            String placeholder,
            String description
    ) {
        return new ProviderFieldDescriptor(
                fieldKey,
                label,
                "textarea",
                required,
                defaultValue,
                placeholder,
                description
        );
    }

    /**
     * 规范化 Provider 类型。
     *
     * @param providerType 供应商类型
     * @return 规范化后的 Provider 类型
     */
    private String normalizeProviderType(String providerType) {
        if (!StringUtils.hasText(providerType)) {
            return "";
        }
        return providerType.trim().toLowerCase(Locale.ROOT);
    }
}
