package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.compiler.model.RawSource;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 动态分组节点
 *
 * 职责：根据显式规则、顶层目录和默认组对源文件分组
 *
 * @author xiexu
 */
public class GroupNode {

    private final CompilerProperties compilerProperties;

    /**
     * 创建动态分组节点。
     *
     * @param compilerProperties 编译配置
     */
    public GroupNode(CompilerProperties compilerProperties) {
        this.compilerProperties = compilerProperties;
    }

    /**
     * 对源文件集合进行分组。
     *
     * @param rawSources 源文件集合
     * @return 分组结果
     */
    public Map<String, List<RawSource>> group(List<RawSource> rawSources) {
        Map<String, List<RawSource>> groupedSources = new LinkedHashMap<String, List<RawSource>>();
        for (RawSource rawSource : rawSources) {
            String groupKey = resolveGroupKey(rawSource.getRelativePath());
            groupedSources.computeIfAbsent(groupKey, key -> new ArrayList<RawSource>()).add(rawSource);
        }
        return groupedSources;
    }

    /**
     * 解析单个文件的分组键。
     *
     * @param relativePath 相对路径
     * @return 分组键
     */
    private String resolveGroupKey(String relativePath) {
        String explicitGroupKey = matchExplicitRule(relativePath);
        if (explicitGroupKey != null) {
            return explicitGroupKey;
        }

        Path path = Paths.get(relativePath);
        if (path.getNameCount() > 1) {
            return path.getName(0).toString();
        }
        return compilerProperties.getDefaultGroup();
    }

    /**
     * 匹配显式规则。
     *
     * @param relativePath 相对路径
     * @return 显式规则命中的分组键
     */
    private String matchExplicitRule(String relativePath) {
        Path path = Paths.get(relativePath);
        for (CompilerProperties.GroupingRule groupingRule : compilerProperties.getGroupingRules()) {
            PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + groupingRule.getPattern());
            if (pathMatcher.matches(path)) {
                return groupingRule.getGroupKey();
            }
        }
        return null;
    }
}
