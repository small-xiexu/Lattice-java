package com.xbk.lattice.api.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理侧向量配置请求
 *
 * 职责：承载向量开关与 embedding profile 的后台保存参数
 *
 * @author xiexu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminVectorConfigRequest {

    private Boolean vectorEnabled;

    private Long embeddingModelProfileId;

    private String operator;
}
