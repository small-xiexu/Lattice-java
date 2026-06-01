package com.xbk.lattice.api.admin;

import lombok.Getter;
import lombok.Setter;

/**
 * 管理侧向量索引重建请求。
 *
 * <p>承载向量索引重建模式与操作人，由 Spring MVC 从 JSON 请求体绑定（{@code required=false}）。
 *
 * @author xiexu
 */
@Getter
@Setter
public class AdminVectorIndexRebuildRequest {

    /**
     * 是否先清空旧向量索引再重建。
     *
     * <p>{@code true} 时先删除全部现有向量索引再逐条重建，期间存在索引空窗期——
     * 线上检索暂时无向量通道，退回纯 lexical/图谱模式。
     * {@code false} 时增量追加，旧索引保留，但切换模型后旧维度向量残留可能导致混合维度索引。</p>
     */
    private boolean truncateFirst;

    /**
     * 操作人标识。
     *
     * <p>用于审计日志记录重建操作者。</p>
     */
    private String operator;
}
