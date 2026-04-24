package com.xbk.lattice.query.deepresearch.service;

import com.xbk.lattice.query.citation.CitationCheckOptions;
import com.xbk.lattice.query.citation.CitationCheckReport;
import com.xbk.lattice.query.citation.CitationCheckService;
import com.xbk.lattice.query.deepresearch.domain.DeepResearchSynthesisResult;
import com.xbk.lattice.query.deepresearch.domain.EvidenceCard;
import com.xbk.lattice.query.deepresearch.domain.EvidenceFinding;
import com.xbk.lattice.query.deepresearch.domain.EvidenceLedger;
import com.xbk.lattice.query.deepresearch.domain.LayerSummary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Deep Research 综合器
 *
 * 职责：把全部层摘要与证据卡综合成最终答案，并执行最终引用核验
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class DeepResearchSynthesizer {

    private static final CitationCheckOptions CITATION_CHECK_OPTIONS = CitationCheckOptions.defaults();

    private final CitationCheckService citationCheckService;

    /**
     * 创建 Deep Research 综合器。
     *
     * @param citationCheckService 引用核验服务
     */
    public DeepResearchSynthesizer(CitationCheckService citationCheckService) {
        this.citationCheckService = citationCheckService;
    }

    /**
     * 综合最终答案。
     *
     * @param question 原始问题
     * @param layerSummaries 分层摘要
     * @param evidenceLedger 证据账本
     * @return 综合结果
     */
    public DeepResearchSynthesisResult synthesize(
            String question,
            List<LayerSummary> layerSummaries,
            EvidenceLedger evidenceLedger
    ) {
        String answerMarkdown = buildAnswerMarkdown(question, layerSummaries, evidenceLedger);
        CitationCheckReport citationCheckReport = citationCheckService.check(answerMarkdown);
        if (citationCheckService.shouldRepair(citationCheckReport, CITATION_CHECK_OPTIONS, 0)) {
            answerMarkdown = citationCheckService.repair(answerMarkdown, citationCheckReport);
            citationCheckReport = citationCheckService.check(answerMarkdown);
        }
        DeepResearchSynthesisResult result = new DeepResearchSynthesisResult();
        result.setAnswerMarkdown(answerMarkdown);
        result.setCitationCheckReport(citationCheckReport);
        result.setPartialAnswer(citationCheckReport.getCoverageRate() < CITATION_CHECK_OPTIONS.getMinCitationCoverage()
                || evidenceLedger == null
                || evidenceLedger.getCards().isEmpty());
        result.setHasConflicts(evidenceLedger != null && evidenceLedger.hasConflicts());
        result.setEvidenceCardCount(evidenceLedger == null ? 0 : evidenceLedger.cardCount());
        return result;
    }

    private String buildAnswerMarkdown(
            String question,
            List<LayerSummary> layerSummaries,
            EvidenceLedger evidenceLedger
    ) {
        StringBuilder answerBuilder = new StringBuilder();
        answerBuilder.append("# 深度研究结论").append("\n\n");
        answerBuilder.append("## 问题").append("\n");
        answerBuilder.append(question == null ? "" : question.trim()).append("\n\n");
        answerBuilder.append("## 结论").append("\n");
        boolean appendedConclusion = false;
        if (evidenceLedger != null) {
            for (EvidenceCard evidenceCard : evidenceLedger.getCards()) {
                for (EvidenceFinding finding : evidenceCard.getFindings()) {
                    answerBuilder.append("- ")
                            .append(finding.getClaim())
                            .append(" ")
                            .append(resolveCitationLiteral(finding))
                            .append("\n");
                    appendedConclusion = true;
                }
            }
        }
        if (!appendedConclusion) {
            answerBuilder.append("- 当前证据不足，暂无法形成完整结论").append("\n");
        }
        answerBuilder.append("\n");
        if (layerSummaries != null && !layerSummaries.isEmpty()) {
            answerBuilder.append("## 分层摘要").append("\n");
            for (LayerSummary layerSummary : layerSummaries) {
                answerBuilder.append("- 第 ").append(layerSummary.getLayerIndex() + 1).append(" 层：")
                        .append(layerSummary.getSummaryMarkdown())
                        .append("\n");
            }
            answerBuilder.append("\n");
        }
        if (evidenceLedger != null && evidenceLedger.hasConflicts()) {
            answerBuilder.append("## 冲突提示").append("\n");
            answerBuilder.append("- 当前证据链中存在不同来源结论不一致的情况，建议结合原始文档进一步确认。").append("\n");
        }
        return answerBuilder.toString().trim();
    }

    private String resolveCitationLiteral(EvidenceFinding finding) {
        if (finding.getSourceId() == null || finding.getSourceId().isBlank()) {
            return "";
        }
        if ("SOURCE".equalsIgnoreCase(finding.getSourceType())) {
            return "[→ " + finding.getSourceId() + "]";
        }
        return "[[" + finding.getSourceId() + "]]";
    }
}
