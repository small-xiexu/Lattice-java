package com.xbk.lattice.query.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.llm.service.PromptCacheWritePolicy;
import com.xbk.lattice.query.domain.ReviewIssue;
import com.xbk.lattice.query.domain.ReviewResult;
import com.xbk.lattice.query.domain.ReviewerPayload;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 审查结果解析器
 *
 * 职责：按标准 JSON、宽松文本与正则救援顺序解析审查输出
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class ReviewResultParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Pattern ISSUE_PATTERN = Pattern.compile(
            "(?:问题|Issue|缺失|missing|遗漏|incorrect)[^\\n]*?：?\\s*([^\\n]{10,200})",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 解析原始审查输出。
     *
     * @param rawResult 原始审查输出
     * @return 审查结果
     */
    public ReviewResult parse(String rawResult) {
        ReviewerPayload reviewerPayload = parsePayload(rawResult);
        if (reviewerPayload != null) {
            return toReviewResult(reviewerPayload);
        }

        List<ReviewIssue> rescuedIssues = rescueIssues(rawResult);
        if (!rescuedIssues.isEmpty()) {
            return ReviewResult.parseRescued(rescuedIssues);
        }
        return ReviewResult.parseFailed();
    }

    /**
     * 按标准 JSON 解析审查结果。
     *
     * @param rawResult 原始审查输出
     * @return reviewer payload；无法解析时返回 null
     */
    public ReviewerPayload parsePayload(String rawResult) {
        if (rawResult == null || rawResult.isBlank()) {
            return null;
        }
        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(rawResult);
            JsonNode approvedNode = rootNode.get("approved");
            if (approvedNode == null || !approvedNode.isBoolean()) {
                return null;
            }
            return parseStructuredPayload(rootNode, approvedNode.asBoolean());
        }
        catch (IOException ex) {
            return null;
        }
    }

    /**
     * 解析 prompt cache 写策略。
     *
     * @param rawResult 原始审查输出
     * @return prompt cache 写策略
     */
    public PromptCacheWritePolicy resolvePromptCacheWritePolicy(String rawResult) {
        ReviewerPayload reviewerPayload = parsePayload(rawResult);
        if (reviewerPayload == null) {
            return PromptCacheWritePolicy.SKIP_WRITE;
        }
        PromptCacheWritePolicy cacheWritePolicy = reviewerPayload.getCacheWritePolicy();
        if (cacheWritePolicy != null) {
            return cacheWritePolicy;
        }
        if (reviewerPayload.isApproved() && !reviewerPayload.isRewriteRequired() && reviewerPayload.getIssues().isEmpty()) {
            return PromptCacheWritePolicy.WRITE;
        }
        return PromptCacheWritePolicy.SKIP_WRITE;
    }

    /**
     * 解析结构化 reviewer payload。
     *
     * @param rootNode 根节点
     * @param approved 是否通过
     * @return reviewer payload
     */
    private ReviewerPayload parseStructuredPayload(JsonNode rootNode, boolean approved) {
        List<ReviewIssue> issues = readIssues(rootNode.get("issues"));
        JsonNode rewriteRequiredNode = rootNode.get("rewriteRequired");
        boolean rewriteRequired = rewriteRequiredNode != null && rewriteRequiredNode.asBoolean(false);
        List<String> rewriteHints = readStringList(rootNode.get("userFacingRewriteHints"));
        PromptCacheWritePolicy cacheWritePolicy = readCacheWritePolicy(rootNode.get("cacheWritePolicy"));
        String riskLevel = readText(rootNode, "riskLevel", "HIGH").toUpperCase(Locale.ROOT);
        return new ReviewerPayload(
                approved,
                rewriteRequired,
                riskLevel,
                issues,
                rewriteHints,
                cacheWritePolicy
        );
    }

    /**
     * 把 reviewer payload 转成审查结果。
     *
     * @param reviewerPayload reviewer payload
     * @return 审查结果
     */
    private ReviewResult toReviewResult(ReviewerPayload reviewerPayload) {
        List<ReviewIssue> issues = new ArrayList<ReviewIssue>(reviewerPayload.getIssues());
        promoteStructuredCompletenessIssues(issues, reviewerPayload.getRiskLevel());
        if (reviewerPayload.isApproved() && !reviewerPayload.isRewriteRequired() && issues.isEmpty()) {
            return ReviewResult.passed();
        }
        if (issues.isEmpty()) {
            if (reviewerPayload.getCacheWritePolicy() == PromptCacheWritePolicy.EVICT_AFTER_READ) {
                issues.add(new ReviewIssue("MEDIUM", "CACHE_EVICT_AFTER_READ", "审查要求读取后立即驱逐 prompt cache"));
            }
            if (!reviewerPayload.getUserFacingRewriteHints().isEmpty()) {
                for (String rewriteHint : reviewerPayload.getUserFacingRewriteHints()) {
                    issues.add(new ReviewIssue(reviewerPayload.getRiskLevel(), "REWRITE_REQUIRED", rewriteHint));
                }
            }
            else {
                String description = reviewerPayload.isRewriteRequired()
                        ? "审查要求重写答案，但未返回结构化 issue"
                        : "审查未通过，但未返回结构化 issue";
                issues.add(new ReviewIssue(reviewerPayload.getRiskLevel(), "REVIEW_REJECTED", description));
            }
        }
        return ReviewResult.issuesFound(issues);
    }

    /**
     * 将“结构化事实缺失/枚举不全/清单未列全”这类问题提升到至少 HIGH，
     * 避免它们在 compile review 阶段被当作可忽略的小问题放行。
     *
     * @param issues 审查问题列表
     * @param fallbackSeverity 兜底严重度
     */
    private void promoteStructuredCompletenessIssues(List<ReviewIssue> issues, String fallbackSeverity) {
        if (issues == null || issues.isEmpty()) {
            return;
        }
        for (int index = 0; index < issues.size(); index++) {
            ReviewIssue issue = issues.get(index);
            if (issue == null || !looksLikeStructuredCompletenessIssue(issue)) {
                continue;
            }
            String promotedSeverity = severityRank(issue.getSeverity()) >= severityRank("HIGH")
                    ? issue.getSeverity()
                    : "HIGH";
            issues.set(index, new ReviewIssue(
                    promotedSeverity,
                    issue.getCategory(),
                    issue.getDescription()
            ));
        }
    }

    /**
     * 判断审查问题是否属于结构化事实不完整。
     *
     * @param issue 审查问题
     * @return 命中返回 true
     */
    private boolean looksLikeStructuredCompletenessIssue(ReviewIssue issue) {
        String haystack = (safe(issue.getCategory()) + " " + safe(issue.getDescription())).toLowerCase(Locale.ROOT);
        return haystack.contains("missing_referential")
                || haystack.contains("missing")
                || haystack.contains("遗漏")
                || haystack.contains("未列出")
                || haystack.contains("未保留")
                || haystack.contains("枚举")
                || haystack.contains("列表")
                || haystack.contains("表格")
                || haystack.contains("清单")
                || haystack.contains("未覆盖")
                || haystack.contains("不完整");
    }

    /**
     * 将严重度映射为可比较等级。
     *
     * @param severity 严重度文本
     * @return 等级值
     */
    private int severityRank(String severity) {
        if (severity == null || severity.isBlank()) {
            return 3;
        }
        String normalizedSeverity = severity.trim().toUpperCase(Locale.ROOT);
        if ("LOW".equals(normalizedSeverity)) {
            return 1;
        }
        if ("MEDIUM".equals(normalizedSeverity)) {
            return 2;
        }
        return 3;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * 读取审查问题列表。
     *
     * @param issuesNode 审查问题节点
     * @return 审查问题列表
     */
    private List<ReviewIssue> readIssues(JsonNode issuesNode) {
        List<ReviewIssue> issues = new ArrayList<ReviewIssue>();
        if (issuesNode == null || !issuesNode.isArray()) {
            return issues;
        }
        for (JsonNode issueNode : issuesNode) {
            String severity = readText(issueNode, "severity", "HIGH");
            String category = readText(issueNode, "category", readText(issueNode, "type", "GENERAL"));
            String description = readText(issueNode, "description", "");
            issues.add(new ReviewIssue(severity, category, description));
        }
        return issues;
    }

    /**
     * 读取字符串数组。
     *
     * @param arrayNode 数组节点
     * @return 字符串列表
     */
    private List<String> readStringList(JsonNode arrayNode) {
        List<String> values = new ArrayList<String>();
        if (arrayNode == null || !arrayNode.isArray()) {
            return values;
        }
        for (JsonNode itemNode : arrayNode) {
            String value = itemNode == null ? null : itemNode.asText();
            if (value == null || value.isBlank()) {
                continue;
            }
            values.add(value.trim());
        }
        return values;
    }

    /**
     * 读取 prompt cache 写策略。
     *
     * @param policyNode 策略节点
     * @return prompt cache 写策略
     */
    private PromptCacheWritePolicy readCacheWritePolicy(JsonNode policyNode) {
        if (policyNode == null || policyNode.isNull()) {
            return null;
        }
        String policyValue = policyNode.asText();
        if (policyValue == null || policyValue.isBlank()) {
            return null;
        }
        try {
            return PromptCacheWritePolicy.valueOf(policyValue.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * 从格式错误文本中救援出审查问题。
     *
     * @param rawResult 原始审查输出
     * @return 审查问题列表
     */
    private List<ReviewIssue> rescueIssues(String rawResult) {
        List<ReviewIssue> reviewIssues = new ArrayList<ReviewIssue>();
        Matcher matcher = ISSUE_PATTERN.matcher(rawResult);
        while (matcher.find()) {
            String description = matcher.group(1).trim();
            reviewIssues.add(new ReviewIssue("HIGH", "PARSE_RESCUED", description));
        }
        return reviewIssues;
    }

    /**
     * 读取文本字段。
     *
     * @param jsonNode JSON 节点
     * @param fieldName 字段名
     * @param defaultValue 默认值
     * @return 文本值
     */
    private String readText(JsonNode jsonNode, String fieldName, String defaultValue) {
        JsonNode fieldNode = jsonNode.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return defaultValue;
        }
        return fieldNode.asText(defaultValue);
    }
}
