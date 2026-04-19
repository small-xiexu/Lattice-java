package com.xbk.lattice.query.domain;

/**
 * 审查问题
 *
 * 职责：承载单条审查问题的类别、严重度与描述
 *
 * @author xiexu
 */
public class ReviewIssue {

    private final String severity;

    private final String category;

    private final String description;

    /**
     * 创建审查问题。
     *
     * @param severity 严重度
     * @param category 类别
     * @param description 描述
     */
    public ReviewIssue(String severity, String category, String description) {
        this.severity = severity;
        this.category = category;
        this.description = description;
    }

    /**
     * 获取严重度。
     *
     * @return 严重度
     */
    public String getSeverity() {
        return severity;
    }

    /**
     * 获取类别。
     *
     * @return 类别
     */
    public String getCategory() {
        return category;
    }

    /**
     * 获取描述。
     *
     * @return 描述
     */
    public String getDescription() {
        return description;
    }
}
