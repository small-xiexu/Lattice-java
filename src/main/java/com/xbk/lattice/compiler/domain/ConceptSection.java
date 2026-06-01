package com.xbk.lattice.compiler.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 概念章节。
 *
 * <p>承载单个概念下的最小 section 结构。heading+contentLines+sourceRefs 三元组决定相等性，
 * 用于跨批次合并时的章节去重。
 *
 * @author xiexu
 */
@Getter
public class ConceptSection {

    /** 小节标题。 */
    private final String heading;

    /** 小节内容行列表。 */
    private final List<String> contentLines;

    /** 小节来源引用列表。 */
    private final List<String> sourceRefs;

    public ConceptSection(String heading, List<String> contentLines) {
        this(heading, contentLines, Collections.<String>emptyList());
    }

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

    @Override
    public int hashCode() {
        return Objects.hash(heading, contentLines, sourceRefs);
    }
}
