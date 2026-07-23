package com.xbk.lattice.api.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.time.Duration;

/**
 * App Web MVC 配置
 *
 * 职责：配置 App SPA 深链接回退与静态资源缓存策略
 *
 * @author xiexu
 */
@Configuration
public class AppWebMvcConfiguration implements WebMvcConfigurer {

    private static final String APP_RESOURCE_LOCATION = "classpath:/static/app/";
    private static final String APP_ASSET_LOCATION = "classpath:/static/app/assets/";
    private static final Duration IMMUTABLE_CACHE_DURATION = Duration.ofDays(365);

    /**
     * 注册 App 静态资源和 SPA 路由处理器。
     *
     * @param registry 资源处理器注册表
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        CacheControl assetCacheControl = CacheControl.maxAge(IMMUTABLE_CACHE_DURATION)
                .cachePublic()
                .immutable();
        registry.addResourceHandler("/app/assets/**")
                .addResourceLocations(APP_ASSET_LOCATION)
                .setCacheControl(assetCacheControl)
                .resourceChain(true)
                .addResolver(new PathResourceResolver());

        registry.addResourceHandler("/app", "/app/", "/app/**")
                .addResourceLocations(APP_RESOURCE_LOCATION)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new AppSpaResourceResolver());
    }
}
