package com.xbk.lattice.query.deepresearch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.query.deepresearch.domain.LayeredResearchPlan;
import com.xbk.lattice.query.deepresearch.domain.ResearchLayer;
import com.xbk.lattice.query.deepresearch.domain.ResearchTask;
import com.xbk.lattice.query.deepresearch.domain.ResearchTaskType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deep Research 规划器
 *
 * 职责：把复杂问题收敛成 1-4 层、每层 1-3 个任务的研究计划
 *
 * @author xiexu
 */
@Service
public class DeepResearchPlanner {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final List<String> DEFAULT_REQUIRED_EVIDENCE_TYPES = List.of(
            "ARTICLE",
            "SOURCE",
            "GRAPH",
            "CONTRIBUTION"
    );

    /**
     * 生成分层研究计划。
     *
     * @param question 用户问题
     * @return 分层研究计划
     */
    public LayeredResearchPlan plan(String question) {
        LayeredResearchPlan plan = new LayeredResearchPlan();
        plan.setRootQuestion(question);
        List<String> splitQuestions = splitQuestion(question);
        if (splitQuestions.size() <= 1) {
            ResearchLayer layer = new ResearchLayer();
            layer.setLayerIndex(0);
            layer.getTasks().add(buildTask(
                    "task-1",
                    ResearchTaskType.FACT_LOOKUP,
                    question,
                    "给出可验证结论",
                    "root",
                    true
            ));
            plan.getLayers().add(layer);
            return plan;
        }
        ResearchLayer layer0 = new ResearchLayer();
        layer0.setLayerIndex(0);
        int taskIndex = 1;
        for (String splitQuestion : splitQuestions) {
            layer0.getTasks().add(buildTask(
                    "task-" + taskIndex,
                    inferTaskType(splitQuestion),
                    splitQuestion,
                    "回答拆解后的子问题",
                    "focused",
                    true
            ));
            taskIndex++;
        }
        plan.getLayers().add(layer0);

        ResearchLayer layer1 = new ResearchLayer();
        layer1.setLayerIndex(1);
        ResearchTask synthesisTask = buildTask(
                "task-synthesis",
                ResearchTaskType.SYNTHESIS,
                question,
                "汇总上一层研究结果并补齐未覆盖子问题",
                "synthesis",
                true
        );
        List<String> preferredUpstreamTaskIds = new ArrayList<String>();
        for (ResearchTask researchTask : layer0.getTasks()) {
            preferredUpstreamTaskIds.add(researchTask.getTaskId());
        }
        synthesisTask.setPreferredUpstreamTaskIds(preferredUpstreamTaskIds);
        layer1.getTasks().add(synthesisTask);
        plan.getLayers().add(layer1);
        return plan;
    }

    /**
     * 将模型返回的 JSON plan 归一化为 v2.6 分层研究计划。
     *
     * @param question 原始问题
     * @param rawPlanJson 模型返回 JSON
     * @return 分层研究计划
     */
    public LayeredResearchPlan parseOrRepairPlan(String question, String rawPlanJson) {
        if (rawPlanJson == null || rawPlanJson.isBlank()) {
            return plan(question);
        }
        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(rawPlanJson);
            LayeredResearchPlan parsedPlan = new LayeredResearchPlan();
            parsedPlan.setRootQuestion(resolveText(rootNode, "rootQuestion", question));
            JsonNode layersNode = rootNode.path("layers");
            if (!layersNode.isArray() || layersNode.isEmpty()) {
                return plan(question);
            }
            int fallbackLayerIndex = 0;
            for (JsonNode layerNode : layersNode) {
                ResearchLayer researchLayer = new ResearchLayer();
                researchLayer.setLayerIndex(layerNode.path("layerIndex").asInt(fallbackLayerIndex));
                JsonNode tasksNode = layerNode.path("tasks");
                if (!tasksNode.isArray() || tasksNode.isEmpty()) {
                    fallbackLayerIndex++;
                    continue;
                }
                int fallbackTaskIndex = 1;
                for (JsonNode taskNode : tasksNode) {
                    researchLayer.getTasks().add(parseOrRepairTask(
                            taskNode,
                            researchLayer.getLayerIndex(),
                            fallbackTaskIndex,
                            question
                    ));
                    fallbackTaskIndex++;
                }
                if (!researchLayer.getTasks().isEmpty()) {
                    parsedPlan.getLayers().add(researchLayer);
                }
                fallbackLayerIndex++;
            }
            if (parsedPlan.getLayers().isEmpty()) {
                return plan(question);
            }
            return parsedPlan;
        }
        catch (Exception exception) {
            return plan(question);
        }
    }

    private ResearchTask buildTask(
            String taskId,
            ResearchTaskType taskType,
            String question,
            String expectedOutput,
            String retrievalFocus,
            boolean mustResolve
    ) {
        ResearchTask task = new ResearchTask();
        task.setTaskId(taskId);
        task.setTaskType(taskType);
        task.setQuestion(question);
        task.setExpectedOutput(expectedOutput);
        task.setExpectedFactSchema(buildExpectedFactSchema(taskType, expectedOutput));
        task.setRequiredEvidenceTypes(new ArrayList<String>(DEFAULT_REQUIRED_EVIDENCE_TYPES));
        task.setRetrievalFocus(retrievalFocus);
        task.setMustResolve(mustResolve);
        return task;
    }

    private ResearchTask parseOrRepairTask(
            JsonNode taskNode,
            int layerIndex,
            int fallbackTaskIndex,
            String rootQuestion
    ) {
        String taskId = resolveText(taskNode, "taskId", "task-" + (layerIndex + 1) + "-" + fallbackTaskIndex);
        ResearchTaskType taskType = parseTaskType(taskNode.path("taskType").asText(""));
        String question = resolveText(taskNode, "question", rootQuestion);
        String expectedOutput = resolveText(taskNode, "expectedOutput", "给出可验证结论");
        ResearchTask task = buildTask(
                taskId,
                taskType,
                question,
                expectedOutput,
                resolveText(taskNode, "retrievalFocus", "focused"),
                taskNode.path("mustResolve").asBoolean(true)
        );
        List<String> expectedFactSchema = readStringArray(taskNode.path("expectedFactSchema"));
        if (!expectedFactSchema.isEmpty()) {
            task.setExpectedFactSchema(expectedFactSchema);
        }
        List<String> requiredEvidenceTypes = readStringArray(taskNode.path("requiredEvidenceTypes"));
        if (!requiredEvidenceTypes.isEmpty()) {
            task.setRequiredEvidenceTypes(requiredEvidenceTypes);
        }
        task.setPreferredUpstreamTaskIds(readStringArray(taskNode.path("preferredUpstreamTaskIds")));
        return task;
    }

    private ResearchTaskType parseTaskType(String value) {
        if (value == null || value.isBlank()) {
            return ResearchTaskType.FACT_LOOKUP;
        }
        try {
            return ResearchTaskType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException exception) {
            return ResearchTaskType.FACT_LOOKUP;
        }
    }

    private List<String> readStringArray(JsonNode jsonNode) {
        List<String> values = new ArrayList<String>();
        if (jsonNode == null || !jsonNode.isArray()) {
            return values;
        }
        for (JsonNode itemNode : jsonNode) {
            String value = itemNode.asText("");
            if (value != null && !value.isBlank()) {
                values.add(value.trim());
            }
        }
        return values;
    }

    private String resolveText(JsonNode jsonNode, String fieldName, String fallback) {
        if (jsonNode == null || fieldName == null) {
            return fallback == null ? "" : fallback;
        }
        String value = jsonNode.path(fieldName).asText("");
        if (value == null || value.isBlank()) {
            return fallback == null ? "" : fallback;
        }
        return value.trim();
    }

    private List<String> buildExpectedFactSchema(ResearchTaskType taskType, String expectedOutput) {
        List<String> expectedFactSchema = new ArrayList<String>();
        expectedFactSchema.add("subject");
        expectedFactSchema.add("predicate");
        expectedFactSchema.add("valueText");
        expectedFactSchema.add("qualifier");
        expectedFactSchema.add("claimText");
        if (taskType == ResearchTaskType.COMPARE) {
            expectedFactSchema.add("comparisonBasis");
        }
        if (taskType == ResearchTaskType.SYNTHESIS) {
            expectedFactSchema.add("resolvedFactKeys");
            expectedFactSchema.add("unresolvedGaps");
        }
        if (expectedOutput != null && !expectedOutput.isBlank()) {
            expectedFactSchema.add("expected:" + expectedOutput.trim());
        }
        return expectedFactSchema;
    }

    private ResearchTaskType inferTaskType(String question) {
        if (question == null || question.isBlank()) {
            return ResearchTaskType.FACT_LOOKUP;
        }
        String normalizedQuestion = question.trim();
        if (normalizedQuestion.contains("区别") || normalizedQuestion.contains("对比")) {
            return ResearchTaskType.COMPARE;
        }
        if (normalizedQuestion.contains("为什么") || normalizedQuestion.contains("原因")) {
            return ResearchTaskType.CAUSE;
        }
        if (normalizedQuestion.contains("如何") || normalizedQuestion.contains("策略") || normalizedQuestion.contains("方案")) {
            return ResearchTaskType.POLICY;
        }
        return ResearchTaskType.FACT_LOOKUP;
    }

    private List<String> splitQuestion(String question) {
        List<String> splitQuestions = new ArrayList<String>();
        if (question == null || question.isBlank()) {
            return splitQuestions;
        }
        String normalizedQuestion = question.trim();
        if (normalizedQuestion.contains("区别") || normalizedQuestion.contains("对比")) {
            String cleanedQuestion = extractComparisonSubject(normalizedQuestion).replace("对比", "和");
            String[] parts = cleanedQuestion.split("和|与|以及");
            for (String part : parts) {
                String trimmedPart = part == null ? "" : part.trim();
                if (!trimmedPart.isBlank()) {
                    splitQuestions.add(trimmedPart + " 的关键结论是什么");
                }
            }
        }
        if (splitQuestions.isEmpty()) {
            String lowercaseQuestion = normalizedQuestion.toLowerCase(Locale.ROOT);
            if (lowercaseQuestion.contains("为什么") && lowercaseQuestion.contains("如何")) {
                splitQuestions.add(normalizedQuestion.replace("如何", "").trim() + " 的原因");
                splitQuestions.add(normalizedQuestion.replace("为什么", "").trim() + " 的做法");
            }
        }
        if (splitQuestions.size() > 3) {
            return splitQuestions.subList(0, 3);
        }
        return splitQuestions;
    }

    /**
     * 提取对比题真正需要拆分的主体，避免把修饰语误当成子问题。
     *
     * @param question 原始问题
     * @return 对比主体
     */
    private String extractComparisonSubject(String question) {
        String normalizedQuestion = question == null ? "" : question.trim();
        int compareIndex = normalizedQuestion.indexOf("对比");
        if (compareIndex >= 0 && compareIndex < normalizedQuestion.length() - 2) {
            normalizedQuestion = normalizedQuestion.substring(compareIndex + 2).trim();
        }
        normalizedQuestion = normalizedQuestion.replace("有什么区别", "");
        normalizedQuestion = normalizedQuestion.replace("区别是什么", "");
        normalizedQuestion = normalizedQuestion.replaceAll("[？?。！!]+$", "");
        return normalizedQuestion.trim();
    }
}
