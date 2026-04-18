package com.xbk.lattice.api.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理侧 Query 检索配置请求
 *
 * 职责：承载并行召回与加权 RRF 的后台保存参数
 *
 * @author xiexu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminQueryRetrievalConfigRequest {

    private Boolean parallelEnabled;

    private Double ftsWeight;

    private Double sourceWeight;

    private Double contributionWeight;

    private Double articleVectorWeight;

    private Double chunkVectorWeight;

    private Integer rrfK;
}
