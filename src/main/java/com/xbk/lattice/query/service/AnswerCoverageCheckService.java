package com.xbk.lattice.query.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.infra.persistence.FactCardRecord;
import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.evidence.domain.FactCardType;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 答案覆盖校验服务
 *
 * 职责：基于通用结构化证据卡校验最终答案是否覆盖枚举、对照、顺序、状态和规则要点
 *
 * @author xiexu
 */
@Service
public class AnswerCoverageCheckService extends AnswerCoverageEvaluationSupport {

    /**
     * 校验结构化证据卡在答案中的覆盖程度。
     *
     * @param question 原始问题
     * @param answerShape 答案形态
     * @param factCards 命中的事实证据卡
     * @param answerMarkdown 生成后的 Markdown 答案
     * @return 答案覆盖校验结果
     */
    public AnswerCoverageCheckResult check(
            String question,
            AnswerShape answerShape,
            List<FactCardRecord> factCards,
            String answerMarkdown
    ) {
        AnswerShape effectiveShape = answerShape == null ? AnswerShape.GENERAL : answerShape;
        if (effectiveShape == AnswerShape.GENERAL) {
            return AnswerCoverageCheckResult.notApplicable();
        }
        List<CoverageRequirement> requirements = collectRequirements(question, effectiveShape, safeFactCards(factCards));
        if (requirements.isEmpty()) {
            return AnswerCoverageCheckResult.missing(List.of("未命中可校验的结构化证据卡要点"));
        }
        CoverageEvaluationSummary evaluationSummary = evaluateRequirements(effectiveShape, requirements, answerMarkdown);
        if (evaluationSummary.getMissingItems().isEmpty()) {
            return AnswerCoverageCheckResult.covered();
        }
        if (evaluationSummary.getCoveredCount() == 0) {
            return AnswerCoverageCheckResult.missing(evaluationSummary.getMissingItems());
        }
        return AnswerCoverageCheckResult.partial(evaluationSummary.getMissingItems());
    }
}
