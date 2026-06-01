package com.xbk.lattice.query.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * 审查问题。
 *
 * <p>承载单条审查问题的类别、严重度与描述——由 LLM reviewer 输出后反序列化。
 *
 * @author xiexu
 */
@Getter
public class ReviewIssue {

    /** 严重度（如 HIGH / MEDIUM / LOW）。驱动 auto-fix 优先级和人工复核阈值。 */
    private final String severity;
    /** 问题类别（如 accuracy / completeness / citation）。 */
    private final String category;
    /** 问题描述。 */
    private final String description;

    @JsonCreator
    public ReviewIssue(
            @JsonProperty("severity") String severity,
            @JsonProperty("category") String category,
            @JsonProperty("description") String description
    ) {
        this.severity = severity;
        this.category = category;
        this.description = description;
    }
}
