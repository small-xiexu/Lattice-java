package com.xbk.lattice.api.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理侧 Compile 审查配置请求
 *
 * 职责：承载 compile review 后台保存参数
 *
 * @author xiexu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminCompileReviewConfigRequest {

    private Boolean autoFixEnabled;

    private Integer maxFixRounds;

    private Boolean allowPersistNeedsHumanReview;

    private String humanReviewSeverityThreshold;

    private String operator;
}
