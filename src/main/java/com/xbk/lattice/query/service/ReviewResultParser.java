package com.xbk.lattice.query.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
        ReviewResult strictResult = parseStructuredJson(rawResult);
        if (strictResult != null) {
            return strictResult;
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
     * @return 审查结果；无法解析时返回 null
     */
    private ReviewResult parseStructuredJson(String rawResult) {
        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(rawResult);
            JsonNode passNode = rootNode.get("pass");
            if (passNode == null) {
                passNode = rootNode.get("passed");
            }
            if (passNode == null || !passNode.isBoolean()) {
                return null;
            }

            boolean pass = passNode.asBoolean();
            List<ReviewIssue> issues = new ArrayList<ReviewIssue>();
            JsonNode issuesNode = rootNode.get("issues");
            if (issuesNode != null && issuesNode.isArray()) {
                for (JsonNode issueNode : issuesNode) {
                    String severity = readText(issueNode, "severity", "HIGH");
                    String category = readText(issueNode, "category", readText(issueNode, "type", "GENERAL"));
                    String description = readText(issueNode, "description", "");
                    issues.add(new ReviewIssue(severity, category, description));
                }
            }
            if (pass) {
                return ReviewResult.passed();
            }
            return ReviewResult.issuesFound(issues);
        }
        catch (IOException ex) {
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
