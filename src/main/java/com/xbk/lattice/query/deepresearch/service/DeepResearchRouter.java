package com.xbk.lattice.query.deepresearch.service;

import com.xbk.lattice.api.query.QueryRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Deep Research 路由器
 *
 * 职责：根据请求参数与问题特征决定是否走 Deep Research
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class DeepResearchRouter {

    /**
     * 判断当前请求是否应走 Deep Research。
     *
     * @param queryRequest 查询请求
     * @return 是否应走 Deep Research
     */
    public boolean shouldRoute(QueryRequest queryRequest) {
        if (queryRequest == null) {
            return false;
        }
        if (Boolean.TRUE.equals(queryRequest.getForceSimple())) {
            return false;
        }
        if (Boolean.TRUE.equals(queryRequest.getForceDeep())) {
            return true;
        }
        String question = queryRequest.getQuestion();
        if (question == null || question.isBlank()) {
            return false;
        }
        String normalizedQuestion = question.toLowerCase(Locale.ROOT);
        return normalizedQuestion.contains("区别")
                || normalizedQuestion.contains("对比")
                || normalizedQuestion.contains("为什么")
                || normalizedQuestion.contains("排查")
                || normalizedQuestion.contains("调用链")
                || normalizedQuestion.contains("影响")
                || normalizedQuestion.contains("步骤");
    }

    /**
     * 生成当前路由决策原因。
     *
     * @param queryRequest 查询请求
     * @return 路由原因
     */
    public String routeReason(QueryRequest queryRequest) {
        if (queryRequest == null) {
            return "query_request_missing";
        }
        if (Boolean.TRUE.equals(queryRequest.getForceSimple())) {
            return "force_simple";
        }
        if (Boolean.TRUE.equals(queryRequest.getForceDeep())) {
            return "force_deep";
        }
        return "complexity_rule_matched";
    }
}
