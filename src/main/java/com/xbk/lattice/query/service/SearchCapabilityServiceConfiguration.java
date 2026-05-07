package com.xbk.lattice.query.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 检索能力探测服务配置
 *
 * 职责：在 JDBC 能力探测实现暂不可用时提供稳定的降级 bean
 *
 * @author xiexu
 */
@Slf4j
@Configuration
public class SearchCapabilityServiceConfiguration {

    /**
     * 提供降级版检索能力探测服务，避免热重启窗口因缺少实现类导致整条依赖链装配失败。
     *
     * @return 默认禁用的检索能力探测服务
     */
    @Bean
    @ConditionalOnMissingBean(SearchCapabilityService.class)
    public SearchCapabilityService fallbackSearchCapabilityService() {
        log.warn("SearchCapabilityService implementation missing, fallback to disabled capability detection");
        return SearchCapabilityService.disabled();
    }
}
