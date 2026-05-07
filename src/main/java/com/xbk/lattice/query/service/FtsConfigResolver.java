package com.xbk.lattice.query.service;

import org.springframework.stereotype.Service;

/**
 * FTS 配置解析器
 *
 * 职责：根据配置与能力探测结果解析当前生效的 ts config
 *
 * @author xiexu
 */
@Service
public class FtsConfigResolver {

    private final QuerySearchProperties querySearchProperties;

    private final SearchCapabilityService searchCapabilityService;

    /**
     * 创建 FTS 配置解析器。
     *
     * @param querySearchProperties 查询检索配置
     * @param searchCapabilityService 检索能力探测服务
     */
    public FtsConfigResolver(
            QuerySearchProperties querySearchProperties,
            SearchCapabilityService searchCapabilityService
    ) {
        this.querySearchProperties = querySearchProperties;
        this.searchCapabilityService = searchCapabilityService;
    }

    /**
     * 创建默认禁用增强能力的 FTS 配置解析器。
     */
    public FtsConfigResolver() {
        this(new QuerySearchProperties(), SearchCapabilityService.disabled());
    }

    /**
     * 解析文章检索使用的 ts config。
     *
     * @return ts config 名称
     */
    public String resolveArticleTsConfig() {
        QuerySearchProperties.FtsProperties ftsProperties = querySearchProperties.getFts();
        String fallbackTsConfig = normalizeTsConfig(ftsProperties.getFallbackTsConfig());
        if (!ftsProperties.isEnabled()) {
            return fallbackTsConfig;
        }

        String preferredTsConfig = normalizeTsConfig(ftsProperties.getPreferredTsConfig());
        if (preferredTsConfig.isBlank()) {
            return fallbackTsConfig;
        }
        if (searchCapabilityService.supportsTextSearchConfig(preferredTsConfig)) {
            return preferredTsConfig;
        }
        return fallbackTsConfig;
    }

    /**
     * 规范化 ts config 名称。
     *
     * @param tsConfig ts config 名称
     * @return 规范化后的名称
     */
    private String normalizeTsConfig(String tsConfig) {
        if (tsConfig == null || tsConfig.isBlank()) {
            return "simple";
        }
        return tsConfig.trim();
    }
}
