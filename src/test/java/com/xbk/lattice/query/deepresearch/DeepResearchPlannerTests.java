package com.xbk.lattice.query.deepresearch;

import com.xbk.lattice.query.deepresearch.domain.LayeredResearchPlan;
import com.xbk.lattice.query.deepresearch.domain.ResearchTask;
import com.xbk.lattice.query.deepresearch.domain.ResearchTaskType;
import com.xbk.lattice.query.deepresearch.service.DeepResearchPlanner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DeepResearchPlanner 测试
 *
 * 职责：验证 Planner 输出符合 v2.6 研究任务契约
 *
 * @author xiexu
 */
class DeepResearchPlannerTests {

    /**
     * 验证复杂对比问题会产出带 fact schema、证据类型与上游依赖的分层计划。
     */
    @Test
    void shouldBuildSchemaValidLayeredPlanForComparisonQuestion() {
        DeepResearchPlanner planner = new DeepResearchPlanner();

        LayeredResearchPlan plan = planner.plan("RoutePlanner 和 PaymentService 有什么区别");

        assertThat(plan.getLayers()).hasSize(2);
        assertThat(plan.getLayers().get(0).getTasks()).hasSize(2);
        ResearchTask firstTask = plan.getLayers().get(0).getTasks().get(0);
        assertThat(firstTask.getTaskType()).isEqualTo(ResearchTaskType.FACT_LOOKUP);
        assertThat(firstTask.isMustResolve()).isTrue();
        assertThat(firstTask.getExpectedFactSchema())
                .contains("subject", "predicate", "valueText", "qualifier", "claimText");
        assertThat(firstTask.getRequiredEvidenceTypes())
                .containsExactly("ARTICLE", "SOURCE", "GRAPH", "CONTRIBUTION");

        ResearchTask synthesisTask = plan.getLayers().get(1).getTasks().get(0);
        assertThat(synthesisTask.getTaskType()).isEqualTo(ResearchTaskType.SYNTHESIS);
        assertThat(synthesisTask.getPreferredUpstreamTaskIds()).containsExactly("task-1", "task-2");
        assertThat(synthesisTask.getExpectedFactSchema()).contains("resolvedFactKeys", "unresolvedGaps");
    }

    /**
     * 验证单问题计划也会标记 mustResolve，并携带结构化 schema。
     */
    @Test
    void shouldBuildSingleTaskPlanWithMustResolveFactSchema() {
        DeepResearchPlanner planner = new DeepResearchPlanner();

        LayeredResearchPlan plan = planner.plan("PaymentService 默认重试次数是多少");

        assertThat(plan.getLayers()).hasSize(1);
        ResearchTask researchTask = plan.getLayers().get(0).getTasks().get(0);
        assertThat(researchTask.getTaskType()).isEqualTo(ResearchTaskType.FACT_LOOKUP);
        assertThat(researchTask.isMustResolve()).isTrue();
        assertThat(researchTask.getExpectedFactSchema()).contains("claimText");
    }

    /**
     * 验证模型 JSON plan 会被修复为 schema-valid 研究计划。
     */
    @Test
    void shouldParseAndRepairLlmJsonPlan() {
        DeepResearchPlanner planner = new DeepResearchPlanner();
        String rawPlanJson = """
                {
                  "rootQuestion": "支付路由如何决策",
                  "layers": [
                    {
                      "layerIndex": 0,
                      "tasks": [
                        {
                          "taskId": "task-route-policy",
                          "taskType": "POLICY",
                          "question": "支付路由的策略是什么",
                          "mustResolve": true,
                          "preferredUpstreamTaskIds": ["task-upstream"]
                        }
                      ]
                    }
                  ]
                }
                """;

        LayeredResearchPlan plan = planner.parseOrRepairPlan("支付路由如何决策", rawPlanJson);

        assertThat(plan.getRootQuestion()).isEqualTo("支付路由如何决策");
        assertThat(plan.getLayers()).hasSize(1);
        ResearchTask researchTask = plan.getLayers().get(0).getTasks().get(0);
        assertThat(researchTask.getTaskType()).isEqualTo(ResearchTaskType.POLICY);
        assertThat(researchTask.getExpectedFactSchema())
                .contains("subject", "predicate", "valueText", "qualifier", "claimText");
        assertThat(researchTask.getRequiredEvidenceTypes())
                .containsExactly("ARTICLE", "SOURCE", "GRAPH", "CONTRIBUTION");
        assertThat(researchTask.getPreferredUpstreamTaskIds()).containsExactly("task-upstream");
    }

    /**
     * 验证非法 JSON 会回退到规则 planner，避免输出半残计划。
     */
    @Test
    void shouldFallbackToRulePlanWhenLlmJsonIsInvalid() {
        DeepResearchPlanner planner = new DeepResearchPlanner();

        LayeredResearchPlan plan = planner.parseOrRepairPlan("PaymentService 默认重试次数是多少", "{broken");

        assertThat(plan.getLayers()).hasSize(1);
        assertThat(plan.getLayers().get(0).getTasks().get(0).getExpectedFactSchema()).contains("claimText");
    }

    /**
     * 验证带修饰前缀的对比题只会拆出真正的两个主体，而不会把“目标/触发时机/实现方式”误当成任务。
     */
    @Test
    void shouldIgnoreInstructionPrefixWhenSplittingComparisonQuestion() {
        DeepResearchPlanner planner = new DeepResearchPlanner();

        LayeredResearchPlan plan = planner.plan("请按目标、触发时机与实现方式，对比库存并发控制与补偿重试策略");

        assertThat(plan.getLayers()).hasSize(2);
        assertThat(plan.getLayers().get(0).getTasks()).extracting(ResearchTask::getQuestion)
                .containsExactly("库存并发控制 的关键结论是什么", "补偿重试策略 的关键结论是什么");
    }
}
