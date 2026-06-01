package com.xbk.lattice.api.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 管理侧 Query 检索配置请求。
 *
 * <p>承载并行召回开关与 RRF 权重参数的后台保存值，由 Spring MVC 从 JSON 请求体绑定。
 * 修改任一枚举或权重值会立即影响下一次检索的通道选择与排序结果。
 *
 * @author xiexu
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminQueryRetrievalConfigRequest {

    /**
     * 并行召回开关。
     *
     * <p>{@code true} 时多个检索通道并行执行，降低延迟但增加数据库连接池压力。
     * {@code false} 时串行执行，延迟叠加但资源消耗更可控。
     * 为 {@code null} 时行为由服务端决定。</p>
     */
    private Boolean parallelEnabled;

    /**
     * 查询改写开关。
     *
     * <p>{@code true} 时对用户原始 query 做 LLM 改写/扩展后再检索，
     * 召回结果集与原始 query 可能存在语义偏移。
     * {@code false} 时使用原始 query 直接检索，召回更贴近用户输入。</p>
     */
    private Boolean rewriteEnabled;

    /**
     * 意图感知向量通道开关。
     *
     * <p>{@code true} 时根据 query 意图动态选择向量通道组合，
     * 策略准确性依赖意图识别模型。
     * {@code false} 时使用固定通道配置，行为确定但可能漏召回。</p>
     */
    private Boolean intentAwareVectorEnabled;

    /**
     * 全文检索（FTS）通道在 RRF 融合时的权重。
     *
     * <p>{@code 0} 表示关闭全文检索通道。值越大在最终排序中占比越高。</p>
     */
    private Double ftsWeight;

    /**
     * RefKey 引用键通道在 RRF 融合时的权重。
     *
     * <p>{@code 0} 表示关闭 RefKey 通道。</p>
     */
    private Double refkeyWeight;

    /**
     * 文章分块 lexical 通道在 RRF 融合时的权重。
     *
     * <p>{@code 0} 表示关闭文章分块 lexical 通道。</p>
     */
    private Double articleChunkWeight;

    /**
     * Source（知识源）通道在 RRF 融合时的权重。
     *
     * <p>{@code 0} 表示关闭 Source 通道。</p>
     */
    private Double sourceWeight;

    /**
     * Source 分块 lexical 通道在 RRF 融合时的权重。
     *
     * <p>{@code 0} 表示关闭 Source 分块通道。</p>
     */
    private Double sourceChunkWeight;

    /**
     * Fact Card lexical 通道在 RRF 融合时的权重。
     *
     * <p>{@code 0} 表示关闭 Fact Card 通道。</p>
     */
    private Double factCardWeight;

    /**
     * Contribution（贡献度）通道在 RRF 融合时的权重。
     *
     * <p>{@code 0} 表示关闭 Contribution 通道。</p>
     */
    private Double contributionWeight;

    /**
     * Graph（知识图谱）通道在 RRF 融合时的权重。
     *
     * <p>{@code 0} 表示关闭知识图谱通道。</p>
     */
    private Double graphWeight;

    /**
     * 文章级别向量通道在 RRF 融合时的权重。
     *
     * <p>{@code 0} 表示关闭文章向量通道。</p>
     */
    private Double articleVectorWeight;

    /**
     * 分块级别向量通道在 RRF 融合时的权重。
     *
     * <p>{@code 0} 表示关闭分块向量通道。</p>
     */
    private Double chunkVectorWeight;

    /**
     * RRF（Reciprocal Rank Fusion）算法的 K 参数。
     *
     * <p>控制排名平滑度：值越大排名越平滑但区分度越低，值越小区分度越高但排名断层风险越大。
     * 过小（如 1）导致只有 top-1 获得有效区分度；过大（如 120+）排名趋同失去区分。
     * 必须 {@code > 0}，服务端应对此做校验。</p>
     */
    private Integer rrfK;
}
