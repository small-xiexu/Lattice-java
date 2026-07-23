package com.xbk.lattice.api.web;

import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * App SPA 资源解析器
 *
 * 职责：优先返回真实静态资源，并仅对无扩展名的 App 路由回退到入口页
 *
 * @author xiexu
 */
public class AppSpaResourceResolver extends PathResourceResolver {

    private static final String INDEX_RESOURCE_PATH = "index.html";
    private static final String ASSET_PATH_PREFIX = "assets/";

    /**
     * 解析请求资源，无扩展名的非资源路径回退到 SPA 入口。
     *
     * @param resourcePath 相对资源路径
     * @param location 资源根目录
     * @return 可读资源，不可解析时返回 null
     * @throws IOException 资源访问异常
     */
    @Override
    protected Resource getResource(String resourcePath, Resource location) throws IOException {
        Resource requestedResource = location.createRelative(resourcePath);
        if (requestedResource.isReadable() && checkResource(requestedResource, location)) {
            return requestedResource;
        }
        if (!isSpaRoute(resourcePath)) {
            return null;
        }
        Resource indexResource = location.createRelative(INDEX_RESOURCE_PATH);
        if (indexResource.isReadable() && checkResource(indexResource, location)) {
            return indexResource;
        }
        return null;
    }

    /**
     * 判断路径是否允许回退到 SPA 入口。
     *
     * @param resourcePath 相对资源路径
     * @return 无扩展名且非 assets 路径时返回 true
     */
    private boolean isSpaRoute(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return true;
        }
        return !resourcePath.startsWith(ASSET_PATH_PREFIX) && !resourcePath.contains(".");
    }
}
