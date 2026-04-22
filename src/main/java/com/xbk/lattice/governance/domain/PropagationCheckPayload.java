package com.xbk.lattice.governance.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 传播影响检查载荷
 *
 * 职责：承载 `check-propagation` 结构化输出的最小语义
 *
 * @author xiexu
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PropagationCheckPayload {

    private final boolean affected;

    private final String reason;

    /**
     * 创建传播影响检查载荷。
     *
     * @param affected 是否受影响
     * @param reason 原因说明
     */
    @JsonCreator
    public PropagationCheckPayload(
            @JsonProperty("affected") Boolean affected,
            @JsonProperty("reason") String reason
    ) {
        this.affected = affected != null && affected;
        this.reason = reason == null ? "" : reason.trim();
    }

    /**
     * 创建默认不受影响的检查载荷。
     *
     * @return 默认检查载荷
     */
    public static PropagationCheckPayload unaffected() {
        return new PropagationCheckPayload(false, "");
    }

    /**
     * 返回是否受影响。
     *
     * @return 是否受影响
     */
    public boolean isAffected() {
        return affected;
    }

    /**
     * 返回原因说明。
     *
     * @return 原因说明
     */
    public String getReason() {
        return reason;
    }
}
