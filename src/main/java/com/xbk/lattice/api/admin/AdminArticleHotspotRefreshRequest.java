package com.xbk.lattice.api.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * 管理侧文章热点刷新请求。
 *
 * <p>承载热点统计刷新的阈值和候选数量上限，由 Spring MVC 从 JSON 请求体绑定。
 * Controller 在请求体为空时使用默认阈值（{@code DEFAULT_HEAT_SCORE_THRESHOLD}）。
 *
 * @author xiexu
 */
@Getter
@Setter
public class AdminArticleHotspotRefreshRequest {

    /**
     * 热度阈值。
     *
     * <p>usage stats 的 {@code heatScore >=} 此值视为热点候选。
     * 必须 {@code >= 1}。为 {@code null} 时 controller 使用默认阈值。
     * 过低导致几乎所有文章标记为热点；过高导致无文章触发。</p>
     */
    @Min(value = 1, message = "热度阈值必须大于 0")
    private Integer heatScoreThreshold;

    /**
     * 返回候选数量上限。
     *
     * <p>用于热点标记和抽检队列生成，范围 1–200。
     * 为 {@code null} 时 controller 使用默认上限。</p>
     */
    @Min(value = 1, message = "候选数量必须大于 0")
    @Max(value = 200, message = "候选数量不能超过 200")
    private Integer limit;
}
