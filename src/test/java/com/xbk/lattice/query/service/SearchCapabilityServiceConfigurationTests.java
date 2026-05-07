package com.xbk.lattice.query.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SearchCapabilityServiceConfiguration 测试
 *
 * 职责：验证 JDBC 探测实现缺失时仍会装配稳定的 fallback bean
 *
 * @author xiexu
 */
class SearchCapabilityServiceConfigurationTests {

    private final ApplicationContextRunner applicationContextRunner = new ApplicationContextRunner();

    /**
     * 验证缺少具体实现时会注册 disabled fallback。
     */
    @Test
    void shouldRegisterDisabledFallbackWhenImplementationMissing() {
        applicationContextRunner
                .withUserConfiguration(SearchCapabilityServiceConfiguration.class)
                .run(context -> {
            assertThat(context).hasSingleBean(SearchCapabilityService.class);
            SearchCapabilityService searchCapabilityService = context.getBean(SearchCapabilityService.class);
            assertThat(searchCapabilityService.supportsTextSearchConfig("jiebacfg")).isFalse();
            assertThat(searchCapabilityService.supportsVectorType()).isFalse();
            assertThat(searchCapabilityService.hasArticleVectorIndex()).isFalse();
        });
    }

    /**
     * 验证存在业务实现时不会覆盖已有 bean。
     */
    @Test
    void shouldNotOverrideExistingImplementation() {
        applicationContextRunner
                .withUserConfiguration(
                        FixedSearchCapabilityServiceConfiguration.class,
                        SearchCapabilityServiceConfiguration.class
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(SearchCapabilityService.class);
                    SearchCapabilityService searchCapabilityService = context.getBean(SearchCapabilityService.class);
                    assertThat(searchCapabilityService.supportsTextSearchConfig("jiebacfg")).isTrue();
                    assertThat(searchCapabilityService.supportsVectorType()).isTrue();
                    assertThat(searchCapabilityService.hasArticleVectorIndex()).isTrue();
                });
    }

    /**
     * 固定检索能力探测配置。
     *
     * 职责：用于验证 fallback 不会覆盖已有业务实现
     *
     * @author xiexu
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class FixedSearchCapabilityServiceConfiguration {

        /**
         * 返回固定可用的检索能力探测服务。
         *
         * @return 检索能力探测服务
         */
        @Bean
        SearchCapabilityService fixedSearchCapabilityService() {
            return new SearchCapabilityService() {

                /**
                 * 返回文本搜索配置可用。
                 *
                 * @param configName 配置名
                 * @return true
                 */
                @Override
                public boolean supportsTextSearchConfig(String configName) {
                    return true;
                }

                /**
                 * 返回 vector 类型可用。
                 *
                 * @return true
                 */
                @Override
                public boolean supportsVectorType() {
                    return true;
                }

                /**
                 * 返回文章向量索引表可用。
                 *
                 * @return true
                 */
                @Override
                public boolean hasArticleVectorIndex() {
                    return true;
                }
            };
        }
    }
}
