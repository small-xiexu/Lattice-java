package com.xbk.lattice.governance.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * 传播影响检查载荷。
 *
 * <p>承载 check-propagation 结构化输出的最小语义——表示文章变更是否影响依赖它的下游内容。
 *
 * @author xiexu
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PropagationCheckPayload {

    /**
     * 当前变更是否会影响依赖/引用该文章的下游内容。
     *
     * <p>{@code true} 时触发下游内容的重编译或标记审查。</p>
     */
    private final boolean affected;
    /**
     * 影响原因或不影响原因。
     *
     * <p>为空代表模型或规则没有给出补充解释。</p>
     */
    private final String reason;

    @JsonCreator
    public PropagationCheckPayload(
            @JsonProperty("affected") Boolean affected,
            @JsonProperty("reason") String reason
    ) {
        this.affected = affected != null && affected;
        this.reason = reason == null ? "" : reason.trim();
    }

    /** 创建默认不受影响的检查载荷。 */
    public static PropagationCheckPayload unaffected() {
        return new PropagationCheckPayload(false, "");
    }
}
