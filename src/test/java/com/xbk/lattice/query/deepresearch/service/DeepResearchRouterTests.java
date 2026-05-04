package com.xbk.lattice.query.deepresearch.service;

import com.xbk.lattice.api.query.QueryRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DeepResearchRouter 测试
 *
 * 职责：验证 Deep Research 只接管真正复杂问题，不抢直接事实题。
 *
 * @author xiexu
 */
class DeepResearchRouterTests {

    /**
     * 验证直接问步骤完成后的返回状态时，不会因为“步骤”一词误走 Deep Research。
     */
    @Test
    void shouldNotRouteDirectStatusQuestionWithStepWordToDeepResearch() {
        DeepResearchRouter deepResearchRouter = new DeepResearchRouter();

        QueryRequest queryRequest = new QueryRequest();
        queryRequest.setQuestion("某个接入流程成功后会返回什么状态？");

        assertThat(deepResearchRouter.shouldRoute(queryRequest)).isFalse();
    }

    /**
     * 验证普通事实差异题不会因为“区别”一词误走 Deep Research。
     */
    @Test
    void shouldNotRouteDirectDifferenceQuestionToDeepResearch() {
        DeepResearchRouter deepResearchRouter = new DeepResearchRouter();

        QueryRequest queryRequest = new QueryRequest();
        queryRequest.setQuestion("这个模块有哪些接入方式？旧版和 V2 有什么区别？");

        assertThat(deepResearchRouter.shouldRoute(queryRequest)).isFalse();
    }

    /**
     * 验证显式强制 Deep Research 仍然生效。
     */
    @Test
    void shouldRespectForceDeepFlag() {
        DeepResearchRouter deepResearchRouter = new DeepResearchRouter();

        QueryRequest queryRequest = new QueryRequest();
        queryRequest.setQuestion("某个接入流程成功后会返回什么状态？");
        queryRequest.setForceDeep(true);

        assertThat(deepResearchRouter.shouldRoute(queryRequest)).isTrue();
    }

    /**
     * 验证排查、调用链、影响类复杂问题仍然会进入 Deep Research。
     */
    @Test
    void shouldRouteComplexInvestigationQuestion() {
        DeepResearchRouter deepResearchRouter = new DeepResearchRouter();

        QueryRequest queryRequest = new QueryRequest();
        queryRequest.setQuestion("支付失败排查调用链有哪些影响？");

        assertThat(deepResearchRouter.shouldRoute(queryRequest)).isTrue();
    }

    /**
     * 验证带明确维度的复杂对比题仍然会进入 Deep Research。
     */
    @Test
    void shouldRouteDimensionedComparisonQuestion() {
        DeepResearchRouter deepResearchRouter = new DeepResearchRouter();

        QueryRequest queryRequest = new QueryRequest();
        queryRequest.setQuestion("请按成本、风险与收益，比较方案 A 和方案 B");

        assertThat(deepResearchRouter.shouldRoute(queryRequest)).isTrue();
    }
}
