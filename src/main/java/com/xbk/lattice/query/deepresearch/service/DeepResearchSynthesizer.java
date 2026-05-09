package com.xbk.lattice.query.deepresearch.service;

import com.xbk.lattice.query.citation.CitationCheckOptions;
import com.xbk.lattice.query.citation.CitationCheckReport;
import com.xbk.lattice.query.citation.CitationCheckService;
import com.xbk.lattice.query.deepresearch.domain.DeepResearchSynthesisResult;
import com.xbk.lattice.query.deepresearch.domain.EvidenceLedger;
import com.xbk.lattice.query.deepresearch.domain.InternalAnswerDraft;
import com.xbk.lattice.query.deepresearch.domain.LayerSummary;
import com.xbk.lattice.query.deepresearch.projector.DeepResearchProjector;
import com.xbk.lattice.query.evidence.domain.AnswerProjectionBundle;
import com.xbk.lattice.query.evidence.domain.FactFinding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deep Research 综合器
 *
 * 职责：把全部层摘要与证据卡综合成最终答案，并执行最终引用核验
 *
 * @author xiexu
 */
@Service
public class DeepResearchSynthesizer {

    private static final CitationCheckOptions CITATION_CHECK_OPTIONS = CitationCheckOptions.defaults();

    private final CitationCheckService citationCheckService;

    private final DeepResearchProjector deepResearchProjector;

    /**
     * 创建 Deep Research 综合器。
     *
     * @param citationCheckService 引用核验服务
     */
    public DeepResearchSynthesizer(CitationCheckService citationCheckService) {
        this(citationCheckService, new DeepResearchProjector());
    }

    /**
     * 创建 Deep Research 综合器。
     *
     * @param citationCheckService 引用核验服务
     * @param deepResearchProjector 答案投影器
     */
    @Autowired
    public DeepResearchSynthesizer(
            CitationCheckService citationCheckService,
            DeepResearchProjector deepResearchProjector
    ) {
        this.citationCheckService = citationCheckService;
        this.deepResearchProjector = deepResearchProjector;
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
        InternalAnswerDraft internalAnswerDraft = buildInternalAnswerDraft(question, layerSummaries, evidenceLedger);
        AnswerProjectionBundle answerProjectionBundle = deepResearchProjector.project(internalAnswerDraft, evidenceLedger);
        answerProjectionBundle = sanitizeAnswerProjectionBundle(answerProjectionBundle);
        String answerMarkdown = answerProjectionBundle.getAnswerMarkdown();
        CitationCheckReport citationCheckReport = citationCheckService.check(answerMarkdown, answerProjectionBundle);
        if (citationCheckService.shouldRepair(citationCheckReport, CITATION_CHECK_OPTIONS, 0)) {
            answerMarkdown = citationCheckService.repair(answerMarkdown, citationCheckReport);
            answerProjectionBundle = citationCheckService.repairProjectionBundle(
                    answerProjectionBundle,
                    citationCheckReport,
                    answerMarkdown
            );
            answerProjectionBundle = sanitizeAnswerProjectionBundle(answerProjectionBundle);
            answerMarkdown = answerProjectionBundle.getAnswerMarkdown();
            citationCheckReport = citationCheckService.check(answerMarkdown, answerProjectionBundle);
        }
        DeepResearchSynthesisResult result = new DeepResearchSynthesisResult();
        result.setInternalAnswerDraft(internalAnswerDraft);
        result.setAnswerMarkdown(answerMarkdown);
        result.setCitationCheckReport(citationCheckReport);
        result.setAnswerProjectionBundle(answerProjectionBundle);
        result.setPartialAnswer(citationCheckReport.getCoverageRate() < CITATION_CHECK_OPTIONS.getMinCitationCoverage()
                || answerProjectionBundle.getProjections().isEmpty()
                || evidenceLedger == null
                || evidenceLedger.findingCount() <= 0);
        result.setHasConflicts(evidenceLedger != null && (evidenceLedger.hasConflicts() || containsConflictSignals(evidenceLedger)));
        result.setEvidenceCardCount(evidenceLedger == null ? 0 : evidenceLedger.cardCount());
        return result;
    }

    /**
     * 清洗出站投影答案，避免 Deep Research 内部摘要与文档元数据泄漏到 HTTP 正文。
     *
     * @param answerProjectionBundle 原始投影包
     * @return 清洗后的投影包
     */
    private AnswerProjectionBundle sanitizeAnswerProjectionBundle(AnswerProjectionBundle answerProjectionBundle) {
        if (answerProjectionBundle == null || answerProjectionBundle.getAnswerMarkdown() == null) {
            return answerProjectionBundle;
        }
        String sanitizedMarkdown = sanitizeUserFacingMarkdown(answerProjectionBundle.getAnswerMarkdown());
        if (sanitizedMarkdown.equals(answerProjectionBundle.getAnswerMarkdown())) {
            return answerProjectionBundle;
        }
        return new AnswerProjectionBundle(
                sanitizedMarkdown,
                answerProjectionBundle.getProjections() == null ? List.of() : answerProjectionBundle.getProjections()
        );
    }

    /**
     * 清洗用户可见 Markdown。
     *
     * @param answerMarkdown 原始答案
     * @return 清洗后的答案
     */
    private String sanitizeUserFacingMarkdown(String answerMarkdown) {
        if (answerMarkdown == null || answerMarkdown.isBlank()) {
            return "";
        }
        List<String> keptLines = new ArrayList<String>();
        boolean skippingInternalSection = false;
        boolean skippingFrontMatter = false;
        String[] lines = answerMarkdown.replace("\r\n", "\n").split("\n");
        for (String line : lines) {
            String trimmedLine = line == null ? "" : line.trim();
            if ("---".equals(trimmedLine)) {
                skippingFrontMatter = !skippingFrontMatter;
                continue;
            }
            if (skippingFrontMatter) {
                continue;
            }
            if (isInternalDeepResearchHeading(trimmedLine)) {
                skippingInternalSection = true;
                continue;
            }
            if (skippingInternalSection && trimmedLine.startsWith("## ")) {
                skippingInternalSection = false;
            }
            if (skippingInternalSection || looksLikeMetadataLeak(trimmedLine)) {
                continue;
            }
            keptLines.add(line);
        }
        String sanitizedMarkdown = String.join("\n", keptLines)
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return sanitizedMarkdown.isBlank() ? "当前证据不足，无法生成可核验引用版答案" : sanitizedMarkdown;
    }

    /**
     * 判断标题是否属于内部 Deep Research 执行说明。
     *
     * @param trimmedLine 已裁剪行
     * @return 是内部标题时返回 true
     */
    private boolean isInternalDeepResearchHeading(String trimmedLine) {
        return "## Layers".equals(trimmedLine) || "## Missing Facts".equals(trimmedLine);
    }

    /**
     * 判断一行文本是否像从 prompt 或文档 front matter 泄漏出的元数据。
     *
     * @param trimmedLine 已裁剪行
     * @return 像元数据泄漏时返回 true
     */
    private boolean looksLikeMetadataLeak(String trimmedLine) {
        if (trimmedLine == null || trimmedLine.isBlank()) {
            return false;
        }
        String normalizedLine = trimmedLine;
        while (normalizedLine.startsWith("-") || normalizedLine.startsWith("*")) {
            normalizedLine = normalizedLine.substring(1).trim();
        }
        String lowerLine = normalizedLine.toLowerCase(Locale.ROOT);
        if (lowerLine.startsWith("metadata:")
                || lowerLine.startsWith("metadatajson:")
                || lowerLine.startsWith("metadata_json:")
                || lowerLine.startsWith("sourcepaths:")
                || lowerLine.startsWith("source_paths:")
                || lowerLine.startsWith("articlekey:")
                || lowerLine.startsWith("article_key:")
                || lowerLine.startsWith("conceptid:")
                || lowerLine.startsWith("concept_id:")
                || lowerLine.startsWith("compiledat:")
                || lowerLine.startsWith("compiled_at:")
                || lowerLine.startsWith("reviewstatus:")
                || lowerLine.startsWith("review_status:")
                || lowerLine.startsWith("文档元数据")) {
            return true;
        }
        return normalizedLine.startsWith("{")
                && (normalizedLine.contains("\"metadata\"")
                || normalizedLine.contains("\"sourcePaths\"")
                || normalizedLine.contains("\"articleKey\"")
                || normalizedLine.contains("\"conceptId\""));
    }

    /**
     * 构造内部答案草稿。
     *
     * @param question 原始问题
     * @param layerSummaries 分层摘要
     * @param evidenceLedger 证据账本
     * @return 内部答案草稿
     */
    public InternalAnswerDraft buildInternalAnswerDraft(
            String question,
            List<LayerSummary> layerSummaries,
            EvidenceLedger evidenceLedger
    ) {
        InternalAnswerDraft internalAnswerDraft = new InternalAnswerDraft();
        StringBuilder answerBuilder = new StringBuilder();
        answerBuilder.append("# Deep Research").append("\n\n");
        answerBuilder.append("## Question").append("\n");
        answerBuilder.append(question == null ? "" : question.trim()).append("\n\n");
        answerBuilder.append("## Findings").append("\n");
        populateFactState(internalAnswerDraft, evidenceLedger);
        Map<String, List<FactFinding>> comparisonFindingsByTopic = collectComparisonFindingsByTopic(evidenceLedger);
        boolean appendedConclusion = false;
        if (!comparisonFindingsByTopic.isEmpty()) {
            appendedConclusion = appendStructuredComparisonConclusion(answerBuilder, comparisonFindingsByTopic);
        }
        if (!appendedConclusion && evidenceLedger != null) {
            for (Map.Entry<String, List<FactFinding>> entry : evidenceLedger.getFindingsByFactKey().entrySet()) {
                for (FactFinding finding : entry.getValue()) {
                    answerBuilder.append("- ")
                            .append(resolveClaimText(finding))
                            .append(resolveAnchorLiterals(finding))
                            .append("\n");
                    appendedConclusion = true;
                }
            }
        }
        if (!appendedConclusion) {
            answerBuilder.append("- INSUFFICIENT_EVIDENCE").append("\n");
        }
        answerBuilder.append("\n");
        if (layerSummaries != null && !layerSummaries.isEmpty()) {
            answerBuilder.append("## Layers").append("\n");
            for (LayerSummary layerSummary : layerSummaries) {
                answerBuilder.append("- Layer ").append(layerSummary.getLayerIndex() + 1).append(": ")
                        .append(resolveLayerSummaryMarkdown(layerSummary))
                        .append("\n");
            }
            answerBuilder.append("\n");
        }
        if (evidenceLedger != null && evidenceLedger.hasConflicts()) {
            answerBuilder.append("## Conflicts").append("\n");
            answerBuilder.append("- CONFLICTING_EVIDENCE").append("\n");
        }
        if (!internalAnswerDraft.getMissingFactKeys().isEmpty()) {
            answerBuilder.append("\n## Missing Facts").append("\n");
            for (String missingFactKey : internalAnswerDraft.getMissingFactKeys()) {
                answerBuilder.append("- ").append(missingFactKey).append("\n");
            }
        }
        internalAnswerDraft.setDraftMarkdown(answerBuilder.toString().trim());
        return internalAnswerDraft;
    }

    /**
     * 回填 resolved / missing / conflicting fact 状态，供最终草稿与审计复用。
     *
     * @param internalAnswerDraft 内部草稿
     * @param evidenceLedger 证据账本
     */
    private void populateFactState(InternalAnswerDraft internalAnswerDraft, EvidenceLedger evidenceLedger) {
        if (internalAnswerDraft == null || evidenceLedger == null) {
            return;
        }
        for (String factKey : evidenceLedger.getFindingsByFactKey().keySet()) {
            internalAnswerDraft.getResolvedFactKeys().add(factKey);
        }
        for (Map.Entry<String, Boolean> entry : evidenceLedger.getCoverageState().entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) {
                internalAnswerDraft.getMissingFactKeys().add(entry.getKey());
            }
        }
        internalAnswerDraft.getConflictingFactKeys().addAll(evidenceLedger.getConflicts().keySet());
    }

    /**
     * 为对比题优先输出按主体收敛后的结论，避免最终答案只剩碎片化 finding 罗列。
     *
     * @param answerBuilder 答案构建器
     * @param comparisonFindingsByTopic 主体到 finding 的映射
     * @return 是否成功输出
     */
    private boolean appendStructuredComparisonConclusion(
            StringBuilder answerBuilder,
            Map<String, List<FactFinding>> comparisonFindingsByTopic
    ) {
        if (answerBuilder == null || comparisonFindingsByTopic == null || comparisonFindingsByTopic.size() < 2) {
            return false;
        }
        answerBuilder.append("\n");
        for (Map.Entry<String, List<FactFinding>> entry : comparisonFindingsByTopic.entrySet()) {
            String topic = entry.getKey();
            List<FactFinding> factFindings = entry.getValue();
            if (topic == null || topic.isBlank() || factFindings == null || factFindings.isEmpty()) {
                continue;
            }
            answerBuilder.append("### ").append(topic).append("\n");
            for (FactFinding factFinding : distinctFindings(factFindings, 2)) {
                answerBuilder.append("- ")
                        .append(resolveClaimText(factFinding))
                        .append(resolveAnchorLiterals(factFinding))
                        .append("\n");
            }
            answerBuilder.append("\n");
        }
        return true;
    }

    /**
     * 收集对比题中每个主体对应的 finding 集合。
     *
     * @param evidenceLedger 证据账本
     * @return 主体到 finding 的映射
     */
    private Map<String, List<FactFinding>> collectComparisonFindingsByTopic(EvidenceLedger evidenceLedger) {
        Map<String, List<FactFinding>> comparisonFindingsByTopic = new LinkedHashMap<String, List<FactFinding>>();
        if (evidenceLedger == null || evidenceLedger.getCards() == null) {
            return comparisonFindingsByTopic;
        }
        for (com.xbk.lattice.query.deepresearch.domain.EvidenceCard evidenceCard : evidenceLedger.getCards()) {
            if (evidenceCard == null
                    || looksLikeSynthesisTask(evidenceCard)
                    || evidenceCard.getFactFindings() == null
                    || evidenceCard.getFactFindings().isEmpty()) {
                continue;
            }
            String topic = resolveComparisonTopic(evidenceCard.getScope());
            if (topic.isBlank()) {
                continue;
            }
            List<FactFinding> topicFindings = comparisonFindingsByTopic.computeIfAbsent(
                    topic,
                    key -> new ArrayList<FactFinding>()
            );
            for (FactFinding factFinding : evidenceCard.getFactFindings()) {
                if (factFinding == null) {
                    continue;
                }
                topicFindings.add(factFinding);
            }
        }
        return comparisonFindingsByTopic;
    }

    /**
     * 从 task scope 中还原主体名称。
     *
     * @param scope task scope
     * @return 主体名称
     */
    private String resolveComparisonTopic(String scope) {
        if (scope == null || scope.isBlank()) {
            return "";
        }
        String topic = scope.trim();
        topic = topic.replace("的关键结论是什么", "");
        topic = topic.replaceAll("[？?。]+$", "");
        return topic.trim();
    }

    /**
     * 拼接去重后的 claim 文本，避免同主体结论完全重复。
     *
     * @param factFindings finding 列表
     * @param limit 最多拼接条数
     * @return 合并后的结论
     */
    private String joinDistinctClaimTexts(List<FactFinding> factFindings, int limit) {
        List<String> claimTexts = new ArrayList<String>();
        if (factFindings == null || factFindings.isEmpty()) {
            return "";
        }
        for (FactFinding factFinding : factFindings) {
            String claimText = resolveClaimText(factFinding);
            if (claimText.isBlank() || claimTexts.contains(claimText)) {
                continue;
            }
            claimTexts.add(claimText);
            if (claimTexts.size() >= limit) {
                break;
            }
        }
        return String.join("；", claimTexts);
    }

    /**
     * 返回去重后的 finding 列表，避免同一主体/维度下重复展示相同 claim。
     *
     * @param factFindings finding 列表
     * @param limit 最多返回数量
     * @return 去重后的 finding
     */
    private List<FactFinding> distinctFindings(List<FactFinding> factFindings, int limit) {
        List<FactFinding> distinctFindings = new ArrayList<FactFinding>();
        List<String> seenClaimTexts = new ArrayList<String>();
        if (factFindings == null || factFindings.isEmpty()) {
            return distinctFindings;
        }
        for (FactFinding factFinding : factFindings) {
            String claimText = resolveClaimText(factFinding);
            if (claimText.isBlank() || seenClaimTexts.contains(claimText)) {
                continue;
            }
            distinctFindings.add(factFinding);
            seenClaimTexts.add(claimText);
            if (distinctFindings.size() >= limit) {
                break;
            }
        }
        return distinctFindings;
    }

    /**
     * 汇总多条 finding 的锚点，避免比较答案丢 citation。
     *
     * @param factFindings finding 列表
     * @return 合并后的锚点字面量
     */
    private String resolveFindingAnchorLiterals(List<FactFinding> factFindings) {
        List<String> anchorIds = new ArrayList<String>();
        if (factFindings == null || factFindings.isEmpty()) {
            return " (ev#missing)";
        }
        for (FactFinding factFinding : factFindings) {
            if (factFinding == null || factFinding.getAnchorIds() == null) {
                continue;
            }
            for (String anchorId : factFinding.getAnchorIds()) {
                if (anchorId == null || anchorId.isBlank() || anchorIds.contains(anchorId.trim())) {
                    continue;
                }
                anchorIds.add(anchorId.trim());
            }
        }
        if (anchorIds.isEmpty()) {
            return " (ev#missing)";
        }
        StringBuilder anchorBuilder = new StringBuilder();
        for (String anchorId : anchorIds) {
            anchorBuilder.append(" (").append(anchorId).append(")");
        }
        return anchorBuilder.toString();
    }

    /**
     * 判断证据卡是否为综合任务卡。
     *
     * @param evidenceCard 证据卡
     * @return 是综合任务时返回 true
     */
    private boolean looksLikeSynthesisTask(com.xbk.lattice.query.deepresearch.domain.EvidenceCard evidenceCard) {
        return evidenceCard != null
                && evidenceCard.getTaskId() != null
                && evidenceCard.getTaskId().toLowerCase().contains("synthesis");
    }

    private String resolveClaimText(FactFinding finding) {
        if (finding == null) {
            return "";
        }
        if (finding.getClaimText() != null && !finding.getClaimText().isBlank()) {
            return finding.getClaimText().trim();
        }
        return finding.getValueText() == null ? "" : finding.getValueText().trim();
    }

    private String resolveAnchorLiterals(FactFinding finding) {
        if (finding == null || finding.getAnchorIds() == null || finding.getAnchorIds().isEmpty()) {
            return " (ev#missing)";
        }
        StringBuilder anchorBuilder = new StringBuilder();
        for (String anchorId : finding.getAnchorIds()) {
            if (anchorId == null || anchorId.isBlank()) {
                continue;
            }
            anchorBuilder.append(" (").append(anchorId.trim()).append(")");
        }
        return anchorBuilder.isEmpty() ? " (ev#missing)" : anchorBuilder.toString();
    }

    /**
     * 规范化分层摘要文案，避免内部 taskId 与尾随标点泄漏到用户答案。
     *
     * @param layerSummary 分层摘要
     * @return 规范化后的摘要文案
     */
    private String resolveLayerSummaryMarkdown(LayerSummary layerSummary) {
        if (layerSummary == null
                || layerSummary.getSummaryMarkdown() == null
                || layerSummary.getSummaryMarkdown().isBlank()) {
            return "NO_EVIDENCE";
        }
        String normalizedMarkdown = layerSummary.getSummaryMarkdown().replace("\r\n", "\n");
        List<String> normalizedSegments = new ArrayList<String>();
        for (String line : normalizedMarkdown.split("\n")) {
            if (line == null || line.isBlank()) {
                continue;
            }
            for (String segment : line.split("[；;]")) {
                String normalizedSegment = normalizeLayerSummarySegment(segment);
                if (!normalizedSegment.isBlank()) {
                    normalizedSegments.add(normalizedSegment);
                }
            }
        }
        if (normalizedSegments.isEmpty()) {
            return "NO_EVIDENCE";
        }
        return String.join("; ", normalizedSegments);
    }

    /**
     * 规范化单个摘要片段。
     *
     * @param segment 原始摘要片段
     * @return 规范化后的摘要片段
     */
    private String normalizeLayerSummarySegment(String segment) {
        if (segment == null || segment.isBlank()) {
            return "";
        }
        String normalizedSegment = segment.trim();
        while (normalizedSegment.startsWith("-") || normalizedSegment.startsWith("*")) {
            normalizedSegment = normalizedSegment.substring(1).trim();
        }
        normalizedSegment = trimTrailingSummaryPunctuation(normalizedSegment);
        int separatorIndex = normalizedSegment.indexOf('：');
        if (separatorIndex > 0) {
            String prefix = normalizedSegment.substring(0, separatorIndex).trim();
            String suffix = normalizedSegment.substring(separatorIndex + 1).trim();
            if (looksLikeInternalTaskId(prefix) && !suffix.isBlank()) {
                normalizedSegment = suffix;
            }
        }
        return trimTrailingSummaryPunctuation(normalizedSegment);
    }

    /**
     * 去掉摘要片段尾部多余句号或分号。
     *
     * @param text 原始文本
     * @return 清洗后的文本
     */
    private String trimTrailingSummaryPunctuation(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalizedText = text.trim();
        while (!normalizedText.isEmpty()) {
            char tail = normalizedText.charAt(normalizedText.length() - 1);
            if (tail != '；' && tail != ';' && tail != '。') {
                break;
            }
            normalizedText = normalizedText.substring(0, normalizedText.length() - 1).trim();
        }
        return normalizedText;
    }

    /**
     * 判断前缀是否像内部 taskId。
     *
     * @param text 待判断文本
     * @return 像内部 taskId 时返回 true
     */
    private boolean looksLikeInternalTaskId(String text) {
        if (text == null || text.isBlank() || text.length() > 80) {
            return false;
        }
        boolean containsLetter = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (isAsciiLetter(current) || Character.isDigit(current)) {
                if (isAsciiLetter(current)) {
                    containsLetter = true;
                }
                continue;
            }
            if (current != '_' && current != '-') {
                return false;
            }
        }
        return containsLetter;
    }

    /**
     * 判断字符是否为 ASCII 英文字母。
     *
     * @param current 当前字符
     * @return 是时返回 true
     */
    private boolean isAsciiLetter(char current) {
        return current >= 'a' && current <= 'z'
                || current >= 'A' && current <= 'Z';
    }

    private boolean containsConflictSignals(EvidenceLedger evidenceLedger) {
        if (evidenceLedger == null || evidenceLedger.getFindingsByFactKey() == null) {
            return false;
        }
        for (List<FactFinding> factFindings : evidenceLedger.getFindingsByFactKey().values()) {
            for (FactFinding factFinding : factFindings) {
                String claimText = resolveClaimText(factFinding);
                if (claimText.contains("冲突")
                        || claimText.contains("不一致")
                        || claimText.contains("不能直接")) {
                    return true;
                }
            }
        }
        return false;
    }
}
