package com.xbk.lattice.compiler.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 概念章节
 *
 * 职责：承载单个概念下的最小 section 结构
 *
 * @author xiexu
 */
public class ConceptSection {

    private final String heading;

    private final List<String> contentLines;

    private final List<String> sourceRefs;

    /**
     * 创建概念章节。
     *
     * @param heading 小节标题
     * @param contentLines 小节内容行
     */
    public ConceptSection(String heading, List<String> contentLines) {
        this(heading, contentLines, Collections.<String>emptyList());
    }

    /**
     * 创建概念章节。
     *
     * @param heading 小节标题
     * @param contentLines 小节内容行
     * @param sourceRefs 小节来源引用
     */
    @JsonCreator
    public ConceptSection(
            @JsonProperty("heading") String heading,
            @JsonProperty("contentLines") List<String> contentLines,
            @JsonProperty("sourceRefs") List<String> sourceRefs
    ) {
        this.heading = heading;
        this.contentLines = contentLines;
        this.sourceRefs = sourceRefs;
    }

    /**
     * 获取小节标题。
     *
     * @return 小节标题
     */
    public String getHeading() {
        return heading;
    }

    /**
     * 获取小节内容行。
     *
     * @return 小节内容行
     */
    public List<String> getContentLines() {
        return contentLines;
    }

    /**
     * 获取小节来源引用。
     *
     * @return 小节来源引用
     */
    public List<String> getSourceRefs() {
        return sourceRefs;
    }

    /**
     * 比较两个章节是否相等。
     *
     * @param other 另一个对象
     * @return 是否相等
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConceptSection)) {
            return false;
        }
        ConceptSection that = (ConceptSection) other;
        return Objects.equals(heading, that.heading)
                && Objects.equals(contentLines, that.contentLines)
                && Objects.equals(sourceRefs, that.sourceRefs);
    }

    /**
     * 计算章节哈希值。
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        return Objects.hash(heading, contentLines, sourceRefs);
    }
}
