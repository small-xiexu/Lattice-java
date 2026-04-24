package com.xbk.lattice.query.deepresearch.service;

import com.xbk.lattice.query.deepresearch.domain.LayeredResearchPlan;
import com.xbk.lattice.query.deepresearch.domain.ResearchLayer;
import com.xbk.lattice.query.deepresearch.domain.ResearchTask;
import org.springframework.context.annotation.Profile;
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
@Profile("jdbc")
public class DeepResearchPlanner {

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
            layer.getTasks().add(buildTask("task-1", question, "给出可验证结论", "root"));
            plan.getLayers().add(layer);
            return plan;
        }
        ResearchLayer layer0 = new ResearchLayer();
        layer0.setLayerIndex(0);
        int taskIndex = 1;
        for (String splitQuestion : splitQuestions) {
            layer0.getTasks().add(buildTask("task-" + taskIndex, splitQuestion, "回答拆解后的子问题", "focused"));
            taskIndex++;
        }
        plan.getLayers().add(layer0);

        ResearchLayer layer1 = new ResearchLayer();
        layer1.setLayerIndex(1);
        ResearchTask synthesisTask = buildTask("task-synthesis", question, "汇总上一层研究结果并补齐未覆盖子问题", "synthesis");
        List<String> preferredUpstreamTaskIds = new ArrayList<String>();
        for (ResearchTask researchTask : layer0.getTasks()) {
            preferredUpstreamTaskIds.add(researchTask.getTaskId());
        }
        synthesisTask.setPreferredUpstreamTaskIds(preferredUpstreamTaskIds);
        layer1.getTasks().add(synthesisTask);
        plan.getLayers().add(layer1);
        return plan;
    }

    private ResearchTask buildTask(String taskId, String question, String expectedOutput, String retrievalFocus) {
        ResearchTask task = new ResearchTask();
        task.setTaskId(taskId);
        task.setQuestion(question);
        task.setExpectedOutput(expectedOutput);
        task.setRetrievalFocus(retrievalFocus);
        return task;
    }

    private List<String> splitQuestion(String question) {
        List<String> splitQuestions = new ArrayList<String>();
        if (question == null || question.isBlank()) {
            return splitQuestions;
        }
        String normalizedQuestion = question.trim();
        if (normalizedQuestion.contains("区别") || normalizedQuestion.contains("对比")) {
            String cleanedQuestion = normalizedQuestion.replace("有什么区别", "")
                    .replace("区别是什么", "")
                    .replace("对比", "和");
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
}
