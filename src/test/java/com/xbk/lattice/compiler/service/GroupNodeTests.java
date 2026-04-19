package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.compiler.node.GroupNode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GroupNode 测试
 *
 * 职责：验证显式规则、顶层目录回退与根目录文件分组
 *
 * @author xiexu
 */
class GroupNodeTests {

    /**
     * 验证显式规则优先，其次顶层目录回退，根目录文件按文件名分组。
     */
    @Test
    void shouldGroupSourcesByConfiguredRulesThenFallbacks() {
        CompilerProperties properties = new CompilerProperties();
        CompilerProperties.GroupingRule docsRule = new CompilerProperties.GroupingRule();
        docsRule.setPattern("docs/**");
        docsRule.setGroupKey("knowledge-docs");
        properties.setGroupingRules(Arrays.asList(docsRule));
        properties.setDefaultGroup("defaultGroup");

        GroupNode groupNode = new GroupNode(properties);
        List<RawSource> rawSources = Arrays.asList(
                RawSource.text("docs/guide.md", "guide", "md", 5L),
                RawSource.text("payment/App.java", "java", "java", 4L),
                RawSource.text("README.md", "root", "md", 4L)
        );

        Map<String, List<RawSource>> grouped = groupNode.group(rawSources);

        assertThat(grouped).containsKeys("knowledge-docs", "payment", "README");
        assertThat(grouped.get("knowledge-docs")).hasSize(1);
        assertThat(grouped.get("payment")).hasSize(1);
        assertThat(grouped.get("README")).hasSize(1);
    }
}
