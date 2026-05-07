package com.xbk.lattice.compiler.service;

import com.xbk.lattice.query.domain.ReviewIssue;
import com.xbk.lattice.query.domain.ReviewResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则型文章审查器
 *
 * 职责：在未启用真实 LLM 审查时，提供可触发失败分支的本地规则审查
 *
 * @author xiexu
 */
@Component
public class RuleBasedArticleReviewer {

    /**
     * 执行规则审查。
     *
     * @param articleContent 文章内容
     * @param sourceContents 源文件正文
     * @return 审查结果
     */
    public ReviewResult review(String articleContent, String sourceContents) {
        List<ReviewIssue> issues = new ArrayList<ReviewIssue>();
        String normalizedArticle = articleContent == null ? "" : articleContent.trim();
        String normalizedSources = sourceContents == null ? "" : sourceContents.trim();

        if (normalizedArticle.isEmpty()) {
            issues.add(new ReviewIssue("HIGH", "EMPTY_ARTICLE", "文章内容为空"));
        }
        if (!normalizedArticle.contains("sources:")) {
            issues.add(new ReviewIssue("HIGH", "MISSING_SOURCES", "frontmatter 缺少 sources 字段"));
        }
        if (!normalizedArticle.contains("review_status:")) {
            issues.add(new ReviewIssue("MEDIUM", "MISSING_REVIEW_STATUS", "frontmatter 缺少 review_status 字段"));
        }
        if (normalizedArticle.contains("TODO") || normalizedArticle.contains("TBD")) {
            issues.add(new ReviewIssue("HIGH", "PLACEHOLDER_CONTENT", "文章仍包含 TODO/TBD 占位内容"));
        }
        if (!normalizedArticle.contains("# ")) {
            issues.add(new ReviewIssue("MEDIUM", "MISSING_TITLE", "正文缺少一级标题"));
        }
        if (normalizedSources.isEmpty()) {
            issues.add(new ReviewIssue("HIGH", "EMPTY_SOURCES", "源文件正文为空，无法验证引用"));
        }

        if (issues.isEmpty()) {
            return ReviewResult.passed();
        }
        return ReviewResult.issuesFound(issues);
    }
}
