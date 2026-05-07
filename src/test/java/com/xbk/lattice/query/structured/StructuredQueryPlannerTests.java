package com.xbk.lattice.query.structured;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 结构化查询计划生成器测试
 *
 * 职责：验证通用字段过滤与投影解析
 *
 * @author xiexu
 */
class StructuredQueryPlannerTests {

    private final StructuredQueryPlanner structuredQueryPlanner = new StructuredQueryPlanner();

    /**
     * 验证字段等值过滤与多个投影字段可解析。
     */
    @Test
    void shouldPlanFieldLookupWithProjectionFields() {
        StructuredQueryPlan plan = structuredQueryPlanner.plan("id=100 这一行的 name、remark 分别是什么？")
                .orElseThrow();

        assertThat(plan.getQueryType()).isEqualTo(StructuredQueryType.ROW_LOOKUP);
        assertThat(plan.getFilters()).containsEntry("id", "100");
        assertThat(plan.getProjections()).containsExactly("name", "remark");
    }

    /**
     * 验证下划线字段过滤与自然语言投影字段可作为普通结构化计划解析。
     */
    @Test
    void shouldPlanUnderscoreFieldLookupWithNaturalProjectionFields() {
        StructuredQueryPlan plan = structuredQueryPlanner.plan("record_id=100 这一行的 记录名称、备注 分别是什么？")
                .orElseThrow();

        assertThat(plan.getQueryType()).isEqualTo(StructuredQueryType.ROW_LOOKUP);
        assertThat(plan.getFilters()).containsEntry("record_id", "100");
        assertThat(plan.getProjections()).containsExactly("记录名称", "备注");
    }

    /**
     * 验证 count 类问题可解析为聚合计划。
     */
    @Test
    void shouldPlanCountQuery() {
        StructuredQueryPlan plan = structuredQueryPlanner.plan("status=done 有多少条？").orElseThrow();

        assertThat(plan.getQueryType()).isEqualTo(StructuredQueryType.COUNT);
        assertThat(plan.getFilters()).containsEntry("status", "done");
        assertThat(plan.getProjections()).isEmpty();
    }

    /**
     * 验证字段值问答中的“多少”不会误判为结构化计数。
     */
    @Test
    void shouldNotTreatValueQuestionAsCountQuery() {
        StructuredQueryPlan plan = structuredQueryPlanner.plan("order_id=100 的 分成金额 是多少？").orElseThrow();

        assertThat(plan.getQueryType()).isEqualTo(StructuredQueryType.ROW_LOOKUP);
        assertThat(plan.getFilters()).containsEntry("order_id", "100");
    }

    /**
     * 验证按字段分组统计可解析为聚合计划。
     */
    @Test
    void shouldPlanGroupByQuery() {
        StructuredQueryPlan plan = structuredQueryPlanner.plan("按 status 统计各多少").orElseThrow();

        assertThat(plan.getQueryType()).isEqualTo(StructuredQueryType.GROUP_BY);
        assertThat(plan.getGroupByField()).isEqualTo("status");
    }

    /**
     * 验证两行对比可解析为两个过滤条件和投影字段。
     */
    @Test
    void shouldPlanRowCompareQuery() {
        StructuredQueryPlan plan = structuredQueryPlanner.plan("id=100 和 id=101 的 name、remark 对比").orElseThrow();

        assertThat(plan.getQueryType()).isEqualTo(StructuredQueryType.ROW_COMPARE);
        assertThat(plan.getCompareFilters()).hasSize(2);
        assertThat(plan.getCompareFilters().get(0)).containsEntry("id", "100");
        assertThat(plan.getCompareFilters().get(1)).containsEntry("id", "101");
        assertThat(plan.getProjections()).containsExactly("name", "remark");
    }

    /**
     * 验证前置对比、冒号与“有什么差异”句式可解析为通用两行对比。
     */
    @Test
    void shouldPlanPrefixedRowCompareQuery() {
        StructuredQueryPlan plan = structuredQueryPlanner
                .plan("对比 record_code=A100 和 record_code=A101：两行的 owner、status 有什么差异？")
                .orElseThrow();

        assertThat(plan.getQueryType()).isEqualTo(StructuredQueryType.ROW_COMPARE);
        assertThat(plan.getCompareFilters()).hasSize(2);
        assertThat(plan.getCompareFilters().get(0)).containsEntry("record_code", "a100");
        assertThat(plan.getCompareFilters().get(1)).containsEntry("record_code", "a101");
        assertThat(plan.getProjections()).containsExactly("owner", "status");
    }

    /**
     * 验证多条件过滤和“请给”枚举投影可按通用字段语法解析。
     */
    @Test
    void shouldPlanMultiFilterLookupWithDelimitedGiveProjections() {
        StructuredQueryPlan plan = structuredQueryPlanner
                .plan("record_id=A100 的 line_no=10 是什么记录？请给 title、type、sql_text。")
                .orElseThrow();

        assertThat(plan.getQueryType()).isEqualTo(StructuredQueryType.ROW_LOOKUP);
        assertThat(plan.getFilters()).containsEntry("record_id", "a100");
        assertThat(plan.getFilters()).containsEntry("line_no", "10");
        assertThat(plan.getProjections()).containsExactly("title", "type", "sql_text");
    }

    /**
     * 验证投影字段后的自然语言描述不会覆盖 schema 风格字段名。
     */
    @Test
    void shouldKeepSchemaLikeProjectionBeforeDescriptiveTail() {
        StructuredQueryPlan plan = structuredQueryPlanner
                .plan("record_id=A100 的 line_no=10 是什么记录？请给 title、type、sql_text 里查询的主对象。")
                .orElseThrow();

        assertThat(plan.getQueryType()).isEqualTo(StructuredQueryType.ROW_LOOKUP);
        assertThat(plan.getFilters()).containsEntry("record_id", "a100");
        assertThat(plan.getFilters()).containsEntry("line_no", "10");
        assertThat(plan.getProjections()).containsExactly("title", "type", "sql_text");
    }
}
