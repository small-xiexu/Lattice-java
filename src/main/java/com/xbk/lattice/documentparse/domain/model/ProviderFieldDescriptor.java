package com.xbk.lattice.documentparse.domain.model;

/**
 * Provider 字段描述
 *
 * 职责：定义后台动态表单中的单个凭证或配置字段
 *
 * @author xiexu
 */
public class ProviderFieldDescriptor {

    private final String fieldKey;

    private final String label;

    private final String inputType;

    private final boolean required;

    private final String defaultValue;

    private final String placeholder;

    private final String description;

    /**
     * 创建 Provider 字段描述。
     *
     * @param fieldKey 字段键
     * @param label 展示标签
     * @param inputType 输入类型
     * @param required 是否必填
     * @param defaultValue 默认值
     * @param placeholder 占位提示
     * @param description 字段说明
     */
    public ProviderFieldDescriptor(
            String fieldKey,
            String label,
            String inputType,
            boolean required,
            String defaultValue,
            String placeholder,
            String description
    ) {
        this.fieldKey = fieldKey;
        this.label = label;
        this.inputType = inputType;
        this.required = required;
        this.defaultValue = defaultValue;
        this.placeholder = placeholder;
        this.description = description;
    }

    /**
     * 返回字段键。
     *
     * @return 字段键
     */
    public String getFieldKey() {
        return fieldKey;
    }

    /**
     * 返回展示标签。
     *
     * @return 展示标签
     */
    public String getLabel() {
        return label;
    }

    /**
     * 返回输入类型。
     *
     * @return 输入类型
     */
    public String getInputType() {
        return inputType;
    }

    /**
     * 返回是否必填。
     *
     * @return 是否必填
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * 返回默认值。
     *
     * @return 默认值
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * 返回占位提示。
     *
     * @return 占位提示
     */
    public String getPlaceholder() {
        return placeholder;
    }

    /**
     * 返回字段说明。
     *
     * @return 字段说明
     */
    public String getDescription() {
        return description;
    }
}
