package com.xbk.lattice.api.admin;

import lombok.Getter;
import lombok.Setter;

/**
 * 管理侧文章纠错请求。
 *
 * <p>承载单篇文章的纠错摘要，由 Spring MVC 从 JSON 请求体绑定。
 *
 * @author xiexu
 */
@Getter
@Setter
public class AdminArticleCorrectionRequest {

    /**
     * 纠错摘要文本。
     *
     * <p>描述文章存在的事实错误、表述问题及修正建议。
     * 可能被持久化到审计记录中，用于后续追溯纠错原因和内容。</p>
     */
    private String correctionSummary;
}
