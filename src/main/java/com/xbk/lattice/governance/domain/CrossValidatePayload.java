package com.xbk.lattice.governance.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 纠错交叉验证载荷
 *
 * 职责：承载 `cross-validate` 结构化输出的最小语义
 *
 * @author xiexu
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CrossValidatePayload {

    private final boolean supported;

    private final String evidence;

    /**
     * 创建纠错交叉验证载荷。
     *
     * @param supported 是否有源文件证据支持
     * @param evidence 证据摘要
     */
    @JsonCreator
    public CrossValidatePayload(
            @JsonProperty("supported") Boolean supported,
            @JsonProperty("evidence") String evidence
    ) {
        this.supported = supported != null && supported;
        this.evidence = evidence == null ? "" : evidence.trim();
    }

    /**
     * 创建默认不支持的交叉验证载荷。
     *
     * @return 默认交叉验证载荷
     */
    public static CrossValidatePayload unsupported() {
        return new CrossValidatePayload(false, "");
    }

    /**
     * 返回是否有源文件证据支持。
     *
     * @return 是否有源文件证据支持
     */
    public boolean isSupported() {
        return supported;
    }

    /**
     * 返回证据摘要。
     *
     * @return 证据摘要
     */
    public String getEvidence() {
        return evidence;
    }
}
