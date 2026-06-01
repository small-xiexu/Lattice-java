package com.xbk.lattice.documentparse.domain.model;

import lombok.Getter;

/**
 * Provider 字段描述。
 *
 * <p>定义后台动态表单中的单个凭证或配置字段——含输入类型、必填约束、默认值和提示文案。
 * 由前端据此渲染表单控件。
 *
 * @author xiexu
 */
@Getter
public class ProviderFieldDescriptor {

    /** 字段键（表单 name 属性）。 */
    private final String fieldKey;

    /** 字段展示标签。 */
    private final String label;

    /**
     * 输入控件类型（如 {@code text} / {@code password} / {@code select}）。
     *
     * <p>{@code password} 类型时前端应对输入值做脱敏展示。</p>
     */
    private final String inputType;

    /** 是否必填。 */
    private final boolean required;

    /** 默认值。可为空。 */
    private final String defaultValue;

    /** 占位提示文案。可为空。 */
    private final String placeholder;

    /** 字段描述说明。可为空。 */
    private final String description;

    public ProviderFieldDescriptor(
            String fieldKey, String label, String inputType, boolean required,
            String defaultValue, String placeholder, String description
    ) {
        this.fieldKey = fieldKey;
        this.label = label;
        this.inputType = inputType;
        this.required = required;
        this.defaultValue = defaultValue;
        this.placeholder = placeholder;
        this.description = description;
    }
}
