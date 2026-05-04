package com.xbk.lattice.compiler.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DocumentSectionSelector 测试
 *
 * 职责：验证 Markdown 章节读取与相关片段选择行为
 *
 * @author xiexu
 */
class DocumentSectionSelectorTests {

    /**
     * 验证读取父标题章节时，会保留其下的子标题内容，直到遇到同级或更高层标题。
     */
    @Test
    void shouldKeepChildHeadingsInsideParentSection() {
        DocumentSectionSelector selector = new DocumentSectionSelector();
        String content = """
                # 总览
                迁移背景
                ## 关键修正
                - 修正一
                - 修正二
                # 其他章节
                其他内容
                """;

        DocumentSectionSelector.DocumentSection section = selector.readSection(content, "总览");

        assertThat(section.getContent()).contains("# 总览");
        assertThat(section.getContent()).contains("## 关键修正");
        assertThat(section.getContent()).contains("修正一");
        assertThat(section.getContent()).doesNotContain("# 其他章节");
    }
}
