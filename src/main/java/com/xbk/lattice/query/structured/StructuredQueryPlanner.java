package com.xbk.lattice.query.structured;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构化查询计划生成器
 *
 * 职责：从自然语言问题中提取通用字段过滤、字段投影与聚合意图
 *
 * @author xiexu
 */
@Component
public class StructuredQueryPlanner {

    private static final Pattern ASSIGNMENT_PATTERN = Pattern.compile(
            "([\\p{IsHan}A-Za-z_][\\p{IsHan}A-Za-z0-9_.\\- ]{0,80})\\s*(=|＝|为|是)\\s*([^，,；;？?\\s]+)"
    );

    private static final Pattern PROJECTION_PATTERN = Pattern.compile(
            "([\\p{IsHan}A-Za-z_][\\p{IsHan}A-Za-z0-9_.\\- ]{0,80})(?:分别)?(?:是什么|是多少|有哪些|为多少|为何|的值)"
    );

    private static final Pattern GROUP_BY_PATTERN = Pattern.compile(
            "(?:按|按照|以)\\s*([\\p{IsHan}A-Za-z_][\\p{IsHan}A-Za-z0-9_.\\-]{0,80})\\s*(?:统计|分组|汇总|各多少|各几条|各几行)"
    );

    private static final Pattern GROUP_EACH_PATTERN = Pattern.compile(
            "([\\p{IsHan}A-Za-z_][\\p{IsHan}A-Za-z0-9_.\\-]{0,80})\\s*各(?:有)?(?:多少|几条|几行)"
    );

    private static final Pattern COMPARE_PROJECTION_PATTERN = Pattern.compile(
            "(?:的|字段|列)\\s*([^？?；;:：]{1,120}?)(?:有什么差异|有何差异|有什么不同|有何不同|差异|不同|对比|比较)"
    );

    /**
     * 尝试生成结构化查询计划。
     *
     * @param question 查询问题
     * @return 查询计划
     */
    public Optional<StructuredQueryPlan> plan(String question) {
        if (!StringUtils.hasText(question)) {
            return Optional.empty();
        }
        List<Map<String, String>> compareFilters = extractCompareFilters(question);
        if (isCompareQuestion(question) && compareFilters.size() >= 2) {
            List<String> projections = extractCompareProjections(question);
            return Optional.of(new StructuredQueryPlan(
                    StructuredQueryType.ROW_COMPARE,
                    Map.of(),
                    projections,
                    null,
                    compareFilters.subList(0, 2)
            ));
        }
        Map<String, String> filters = extractFilters(question);
        String groupByField = extractGroupByField(question);
        if (StringUtils.hasText(groupByField)) {
            return Optional.of(new StructuredQueryPlan(
                    StructuredQueryType.GROUP_BY,
                    filters,
                    List.of(),
                    groupByField,
                    List.of()
            ));
        }
        if (filters.isEmpty()) {
            return Optional.empty();
        }
        StructuredQueryType queryType = isCountQuestion(question)
                ? StructuredQueryType.COUNT
                : StructuredQueryType.ROW_LOOKUP;
        List<String> projections = queryType == StructuredQueryType.COUNT
                ? List.of()
                : extractProjections(question, filters);
        return Optional.of(new StructuredQueryPlan(queryType, filters, projections));
    }

    private Map<String, String> extractFilters(String question) {
        Map<String, String> filters = new LinkedHashMap<String, String>();
        Matcher matcher = ASSIGNMENT_PATTERN.matcher(question);
        while (matcher.find()) {
            String columnName = cleanFieldCandidate(matcher.group(1));
            String value = cleanValueCandidate(matcher.group(3));
            if (!isUsableField(columnName) || !StringUtils.hasText(value) || isQuestionValue(value)) {
                continue;
            }
            filters.put(columnName, value.toLowerCase(Locale.ROOT));
        }
        return filters;
    }

    private List<Map<String, String>> extractCompareFilters(String question) {
        List<Map<String, String>> compareFilters = new ArrayList<Map<String, String>>();
        Matcher matcher = ASSIGNMENT_PATTERN.matcher(question);
        while (matcher.find()) {
            String columnName = cleanFieldCandidate(matcher.group(1));
            String value = cleanValueCandidate(matcher.group(3));
            if (!isUsableField(columnName) || !StringUtils.hasText(value) || isQuestionValue(value)) {
                continue;
            }
            compareFilters.add(Map.of(columnName, value.toLowerCase(Locale.ROOT)));
        }
        return compareFilters;
    }

    private List<String> extractProjections(String question, Map<String, String> filters) {
        List<String> projections = new ArrayList<String>();
        List<String> enumeratedFields = extractEnumeratedProjectionFields(question);
        for (String field : enumeratedFields) {
            if (shouldAddProjection(projections, filters, field)) {
                projections.add(field);
            }
        }
        if (!projections.isEmpty()) {
            return projections;
        }
        List<String> delimitedFields = extractDelimitedProjectionFields(question);
        for (String field : delimitedFields) {
            if (shouldAddProjection(projections, filters, field)) {
                projections.add(field);
            }
        }
        if (!projections.isEmpty()) {
            return projections;
        }
        Matcher matcher = PROJECTION_PATTERN.matcher(question);
        while (matcher.find()) {
            String fieldCandidate = cleanFieldCandidate(matcher.group(1));
            if (!isUsableField(fieldCandidate) || filters.containsKey(fieldCandidate)) {
                continue;
            }
            if (fieldCandidate.length() > 80) {
                continue;
            }
            projections.add(fieldCandidate);
        }
        return projections;
    }

    private List<String> extractEnumeratedProjectionFields(String question) {
        List<String> fields = new ArrayList<String>();
        String normalizedQuestion = question.replace('、', ',').replace('，', ',');
        int anchorIndex = normalizedQuestion.indexOf("分别");
        if (anchorIndex < 0) {
            anchorIndex = normalizedQuestion.indexOf("是什么");
        }
        if (anchorIndex < 0) {
            return fields;
        }
        String leftText = normalizedQuestion.substring(0, anchorIndex);
        leftText = ASSIGNMENT_PATTERN.matcher(leftText).replaceAll(" ");
        int boundaryIndex = Math.max(leftText.lastIndexOf('?'), leftText.lastIndexOf('？'));
        if (boundaryIndex >= 0 && boundaryIndex + 1 < leftText.length()) {
            leftText = leftText.substring(boundaryIndex + 1);
        }
        for (String token : leftText.split(",")) {
            String field = cleanFieldCandidate(token);
            if (isUsableField(field)) {
                fields.add(field);
            }
        }
        return fields;
    }

    private List<String> extractDelimitedProjectionFields(String question) {
        List<String> fields = new ArrayList<String>();
        String normalizedQuestion = ASSIGNMENT_PATTERN.matcher(question).replaceAll(" ");
        normalizedQuestion = normalizedQuestion.replace('、', ',').replace('，', ',');
        if (!normalizedQuestion.contains(",")) {
            return fields;
        }
        for (String token : normalizedQuestion.split(",")) {
            String field = cleanFieldCandidate(token);
            if (isUsableField(field)) {
                fields.add(field);
            }
        }
        return fields;
    }

    private boolean shouldAddProjection(List<String> projections, Map<String, String> filters, String field) {
        return isUsableField(field) && !filters.containsKey(field) && !projections.contains(field);
    }

    private String extractGroupByField(String question) {
        Matcher matcher = GROUP_BY_PATTERN.matcher(question);
        if (matcher.find()) {
            return cleanFieldCandidate(matcher.group(1));
        }
        Matcher eachMatcher = GROUP_EACH_PATTERN.matcher(question);
        if (eachMatcher.find()) {
            return cleanFieldCandidate(eachMatcher.group(1));
        }
        return "";
    }

    private List<String> extractCompareProjections(String question) {
        List<String> projections = new ArrayList<String>();
        Matcher matcher = COMPARE_PROJECTION_PATTERN.matcher(question.replace('、', ',').replace('，', ','));
        if (!matcher.find()) {
            return projections;
        }
        String projectionText = matcher.group(1);
        for (String token : projectionText.split(",")) {
            String projection = cleanFieldCandidate(token);
            if (isUsableField(projection)) {
                projections.add(projection);
            }
        }
        return projections;
    }

    private boolean isCompareQuestion(String question) {
        return question.contains("对比")
                || question.contains("比较")
                || question.contains("差异")
                || question.contains("不同");
    }

    private boolean isCountQuestion(String question) {
        String normalizedQuestion = question == null ? "" : question.trim();
        return normalizedQuestion.contains("多少条")
                || normalizedQuestion.contains("多少行")
                || normalizedQuestion.contains("多少个记录")
                || normalizedQuestion.contains("多少条记录")
                || normalizedQuestion.contains("记录数")
                || normalizedQuestion.contains("行数")
                || normalizedQuestion.contains("几条")
                || normalizedQuestion.contains("几行")
                || question.toLowerCase(Locale.ROOT).contains("count");
    }

    private String cleanFieldCandidate(String fieldCandidate) {
        if (fieldCandidate == null) {
            return "";
        }
        String cleaned = fieldCandidate.trim();
        cleaned = cleaned.replaceAll("^(这一行的?|这行的?|该行的?|这一条的?|这条的?|该条的?)", "");
        cleaned = cleaned.replaceAll(
                "^(这[一行条个]?的?|该[行条个]?的?|其中的?|请问|请列出|查询|对比|比较|问|和|以及|,)+",
                ""
        );
        cleaned = cleaned.replaceAll("(这一行|这行|该行|分别|字段|列)$", "");
        cleaned = cleaned.replace("`", "").trim();
        cleaned = cleaned.replaceAll("^[。！？?；;：:,，、]+|[。！？?；;：:,，、]+$", "");
        cleaned = lastFieldLikeSegment(cleaned);
        return cleaned;
    }

    private String lastFieldLikeSegment(String fieldCandidate) {
        if (fieldCandidate == null || !fieldCandidate.matches(".*\\s+.*")) {
            return fieldCandidate == null ? "" : fieldCandidate;
        }
        String[] segments = fieldCandidate.trim().split("\\s+");
        for (String segment : segments) {
            String normalizedSegment = segment.trim();
            if (isSchemaLikeField(normalizedSegment)) {
                return normalizedSegment;
            }
        }
        for (int index = segments.length - 1; index >= 0; index--) {
            String segment = segments[index].trim();
            if (segment.matches("[\\p{IsHan}A-Za-z_][\\p{IsHan}A-Za-z0-9_.\\-]*")) {
                return segment;
            }
        }
        return fieldCandidate;
    }

    private boolean isSchemaLikeField(String fieldCandidate) {
        if (fieldCandidate == null || fieldCandidate.isBlank()) {
            return false;
        }
        return fieldCandidate.matches("[A-Za-z_][A-Za-z0-9_.\\-]*")
                && (fieldCandidate.contains("_") || fieldCandidate.contains(".") || fieldCandidate.contains("-"));
    }

    private String cleanValueCandidate(String valueCandidate) {
        if (valueCandidate == null) {
            return "";
        }
        String cleaned = valueCandidate.replace("`", "").trim();
        int delimiterIndex = firstDelimiterIndex(cleaned);
        if (delimiterIndex > 0) {
            cleaned = cleaned.substring(0, delimiterIndex);
        }
        return cleaned.trim();
    }

    private int firstDelimiterIndex(String value) {
        int firstIndex = -1;
        String delimiters = "：，,；;？? \t\r\n";
        for (int index = 0; index < value.length(); index++) {
            if (delimiters.indexOf(value.charAt(index)) >= 0) {
                firstIndex = index;
                break;
            }
        }
        return firstIndex;
    }

    private boolean isQuestionValue(String valueCandidate) {
        if (!StringUtils.hasText(valueCandidate)) {
            return true;
        }
        return valueCandidate.contains("什么")
                || valueCandidate.contains("多少")
                || valueCandidate.contains("哪些")
                || valueCandidate.contains("几");
    }

    private boolean isUsableField(String fieldCandidate) {
        if (!StringUtils.hasText(fieldCandidate)) {
            return false;
        }
        String normalized = fieldCandidate.trim();
        if (normalized.length() > 80) {
            return false;
        }
        if (normalized.contains(" ")) {
            return false;
        }
        return normalized.matches("[\\p{IsHan}A-Za-z_][\\p{IsHan}A-Za-z0-9_.\\-]*");
    }
}
