package com.xbk.lattice.api.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 管理侧向量配置请求。
 *
 * <p>承载向量开关与 embedding profile 的后台保存参数，由 Spring MVC 从 JSON 请求体绑定。
 *
 * @author xiexu
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminVectorConfigRequest {

    /**
     * 向量检索总开关。
     *
     * <p>{@code false} 时运行期检索退化到非向量模式（纯 lexical/图谱），召回质量和排序均受影响。
     * 修改后服务端可能根据维度匹配情况将 {@code rebuildRecommended} 置为 {@code true}。
     * 为 {@code null} 时行为由服务端决定——通常等同于 {@code false}。</p>
     */
    private Boolean vectorEnabled;

    /**
     * embedding 模型配置主键。
     *
     * <p>切换模型会导致向量维度变化，现有索引全部失效，需触发重建。
     * 为 {@code null} 表示未配置 embedding 模型，向量检索不可用。</p>
     */
    private Long embeddingModelProfileId;

    /**
     * 操作人标识。
     *
     * <p>用于审计日志记录配置变更操作者。服务端应校验非空。</p>
     */
    private String operator;
}
