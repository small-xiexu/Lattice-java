package com.xbk.lattice.governance.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * 纠错交叉验证载荷。
 *
 * <p>承载 cross-validate 结构化输出的最小语义——表示用户纠错是否有源文件证据支持。
 *
 * @author xiexu
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CrossValidatePayload {

    /**
     * 交叉验证是否支持用户纠错。
     *
     * <p>{@code false} 时纠错被拒绝进入主链，仅记录到审计而不触发内容修正。</p>
     */
    private final boolean supported;
    /**
     * 支撑或否定纠错的证据摘要。
     *
     * <p>空字符串代表未获得可用证据。</p>
     */
    private final String evidence;

    @JsonCreator
    public CrossValidatePayload(
            @JsonProperty("supported") Boolean supported,
            @JsonProperty("evidence") String evidence
    ) {
        this.supported = supported != null && supported;
        this.evidence = evidence == null ? "" : evidence.trim();
    }

    /** 创建默认不支持的交叉验证载荷。 */
    public static CrossValidatePayload unsupported() {
        return new CrossValidatePayload(false, "");
    }
}
