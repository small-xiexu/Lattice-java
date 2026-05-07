package com.xbk.lattice.query.deepresearch.service;

import com.xbk.lattice.api.query.QueryRequest;
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
        return looksLikeDeepComparisonQuestion(normalizedQuestion)
                || normalizedQuestion.contains("为什么")
                || normalizedQuestion.contains("排查")
                || normalizedQuestion.contains("调用链")
                || normalizedQuestion.contains("影响");
    }

    /**
     * 判断是否为需要 Deep Research 接管的复杂对比题。
     *
     * @param normalizedQuestion 已归一化的问题
     * @return 复杂对比题返回 true
     */
    private boolean looksLikeDeepComparisonQuestion(String normalizedQuestion) {
        if (!containsComparisonIntent(normalizedQuestion)) {
            return false;
        }
        return hasDimensionListBeforeComparison(normalizedQuestion)
                || hasMultiSubjectComparisonStructure(normalizedQuestion);
    }

    /**
     * 判断是否包含对比意图。
     *
     * @param normalizedQuestion 已归一化的问题
     * @return 包含对比语义返回 true
     */
    private boolean containsComparisonIntent(String normalizedQuestion) {
        return normalizedQuestion.contains("区别")
                || normalizedQuestion.contains("差异")
                || normalizedQuestion.contains("对比")
                || normalizedQuestion.contains("比较")
                || normalizedQuestion.contains(" vs ")
                || normalizedQuestion.contains(" versus ");
    }

    /**
     * 判断对比词之前是否已经给出多个展开维度。
     *
     * @param normalizedQuestion 已归一化的问题
     * @return 存在多维列表结构返回 true
     */
    private boolean hasDimensionListBeforeComparison(String normalizedQuestion) {
        int comparisonIndex = firstComparisonIntentIndex(normalizedQuestion);
        if (comparisonIndex <= 0) {
            return false;
        }
        String prefix = normalizedQuestion.substring(0, comparisonIndex);
        return countListDelimiters(prefix) >= 2;
    }

    /**
     * 判断是否为多主体对比结构。
     *
     * @param normalizedQuestion 已归一化的问题
     * @return 多主体对比返回 true
     */
    private boolean hasMultiSubjectComparisonStructure(String normalizedQuestion) {
        if (normalizedQuestion.length() < 32) {
            return false;
        }
        return countListDelimiters(normalizedQuestion) >= 3;
    }

    /**
     * 获取首个对比意图词的位置。
     *
     * @param normalizedQuestion 已归一化的问题
     * @return 首个位置；没有则返回 -1
     */
    private int firstComparisonIntentIndex(String normalizedQuestion) {
        int firstIndex = -1;
        String[] comparisonIntents = {"区别", "差异", "对比", "比较", " vs ", " versus "};
        for (String comparisonIntent : comparisonIntents) {
            int currentIndex = normalizedQuestion.indexOf(comparisonIntent);
            if (currentIndex >= 0 && (firstIndex < 0 || currentIndex < firstIndex)) {
                firstIndex = currentIndex;
            }
        }
        return firstIndex;
    }

    /**
     * 统计文本中的列表/并列结构分隔符。
     *
     * @param text 文本
     * @return 分隔符数量
     */
    private int countListDelimiters(String text) {
        int delimiterCount = 0;
        String[] delimiters = {"、", "，", ",", "/", "与", "和", "及", " and ", "&"};
        for (String delimiter : delimiters) {
            int searchIndex = text.indexOf(delimiter);
            while (searchIndex >= 0) {
                delimiterCount++;
                searchIndex = text.indexOf(delimiter, searchIndex + delimiter.length());
            }
        }
        return delimiterCount;
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
