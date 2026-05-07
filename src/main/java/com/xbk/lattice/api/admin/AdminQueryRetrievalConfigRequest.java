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

    private Boolean rewriteEnabled;

    private Boolean intentAwareVectorEnabled;

    private Double ftsWeight;

    private Double refkeyWeight;

    private Double articleChunkWeight;

    private Double sourceWeight;

    private Double sourceChunkWeight;

    private Double factCardWeight;

    private Double contributionWeight;

    private Double graphWeight;

    private Double articleVectorWeight;

    private Double chunkVectorWeight;

    private Integer rrfK;
}
