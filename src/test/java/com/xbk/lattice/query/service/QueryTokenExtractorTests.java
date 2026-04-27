package com.xbk.lattice.query.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QueryTokenExtractor 测试
 *
 * 职责：验证查询 token 提取逻辑能够覆盖中文语义问句
 *
 * @author xiexu
 */
class QueryTokenExtractorTests {

    /**
     * 验证纯中文问句也会提取出稳定 token，供 source / contribution 检索复用。
     */
    @Test
    void shouldExtractChineseTokensFromNaturalLanguageQuestion() {
        List<String> tokens = QueryTokenExtractor.extract("用户确认的运维口径说重试间隔是什么");

        assertThat(tokens).contains("重试", "间隔");
    }

    /**
     * 验证路径、类名与配置键会被作为高信号 token 保留下来。
     */
    @Test
    void shouldExtractPathClassAndConfigKeyTokens() {
        List<String> tokens = QueryTokenExtractor.extract(
                "RoutePlanner 在 src/main/java/payment/RoutePlanner.java 里怎么读取 payment.retry.maxAttempts"
        );

        assertThat(tokens).contains(
                "routeplanner",
                "route",
                "planner",
                "src/main/java/payment/routeplanner.java",
                "payment.retry.maxattempts"
        );
    }
}
