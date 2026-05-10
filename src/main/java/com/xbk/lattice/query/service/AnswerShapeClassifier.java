package com.xbk.lattice.query.service;

import com.xbk.lattice.query.evidence.domain.AnswerShape;
import org.springframework.stereotype.Service;

/**
 * 答案形态分类器
 *
 * 职责：基于通用问法识别 query 期望的结构化答案形态
 *
 * @author xiexu
 */
@Service
public class AnswerShapeClassifier {

    private final QuerySemanticRules querySemanticRules;

    /**
     * 创建答案形态分类器（无参回退构造器）。
     */
    public AnswerShapeClassifier() {
        this(null);
    }

    /**
     * 创建答案形态分类器。
     *
     * @param querySemanticRules 查询语义规则
     */
    public AnswerShapeClassifier(QuerySemanticRules querySemanticRules) {
        this.querySemanticRules = querySemanticRules == null ? new QuerySemanticRules() : querySemanticRules;
    }

    /**
     * 识别答案形态。
     *
     * 优先级：COMPARE > SEQUENCE > POLICY > ENUM > STATUS > GENERAL。
     * 理由：对比和顺序是最明确的结构信号；规则/约束比列表信号更具体
     * （"哪些"在非枚举问法中也很常见）；状态/结论信号较弱，作为最低非通用形态。
     *
     * @param question 查询问题
     * @return 答案形态
     */
    public AnswerShape classify(String question) {
        if (question == null || question.isBlank()) {
            return AnswerShape.GENERAL;
        }
        if (querySemanticRules.containsAnyComparisonSignal(question)) {
            return AnswerShape.COMPARE;
        }
        if (querySemanticRules.containsAnySequenceSignal(question)) {
            return AnswerShape.SEQUENCE;
        }
        if (querySemanticRules.containsAnyPolicySignal(question)) {
            return AnswerShape.POLICY;
        }
        if (querySemanticRules.containsAnyEnumSignal(question)) {
            return AnswerShape.ENUM;
        }
        if (querySemanticRules.containsAnyStatusSignal(question)) {
            return AnswerShape.STATUS;
        }
        return AnswerShape.GENERAL;
    }
}
