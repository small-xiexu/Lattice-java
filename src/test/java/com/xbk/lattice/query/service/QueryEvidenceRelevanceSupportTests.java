package com.xbk.lattice.query.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 查询证据相关性支持测试
 *
 * 职责：验证融合命中进入生成上下文前的相关性过滤
 *
 * @author xiexu
 */
class QueryEvidenceRelevanceSupportTests {

    /**
     * 验证 TRANS-JOB 运维题只保留命中强领域锚点的证据。
     */
    @Test
    void shouldFilterOperationalMaintenanceHitsByDomainAnchors() {
        List<QueryArticleHit> filteredHits = QueryEvidenceRelevanceSupport.filterRelevantHits(
                "TRANS-JOB 对买一赠一日常维护需要执行哪些动作？",
                List.of(
                        new QueryArticleHit(
                                1L,
                                "legacy-default--支付与卡券重试现状梳理",
                                "支付与卡券重试现状梳理",
                                "支付与卡券重试现状梳理",
                                "trans-job 补偿任务覆盖 pay_changelog 和 pay_changelog_coupon。",
                                "{\"description\":\"支付与卡券重试，包含 trans-job 定时补偿\"}",
                                List.of("支付与卡券重试现状梳理.md"),
                                4.0D
                        ),
                        new QueryArticleHit(
                                2L,
                                "legacy-default--买一赠一渠道梳理",
                                "买一赠一渠道梳理",
                                "买一赠一渠道梳理",
                                "买一赠一逆向流程需要关注银行权益侧、银联侧和 coupon 服务侧。",
                                "{\"description\":\"买一赠一渠道与逆向链路\"}",
                                List.of("买一赠一渠道梳理.md"),
                                3.0D
                        ),
                        new QueryArticleHit(
                                3L,
                                "legacy-default--第6章-ai大模型应用最佳实践",
                                "第6章-ai大模型应用最佳实践",
                                "第6章：AI大模型应用最佳实践",
                                "问题拆解技巧要求把复杂任务拆成多个子问题，并逐步执行。",
                                "{\"description\":\"AI 大模型 prompt 最佳实践\"}",
                                List.of("第6章：AI大模型应用最佳实践.pdf"),
                                2.0D
                        )
                )
        );

        assertThat(filteredHits)
                .extracting(QueryArticleHit::getArticleKey)
                .containsExactly(
                        "legacy-default--支付与卡券重试现状梳理",
                        "legacy-default--买一赠一渠道梳理"
                );
    }
}
