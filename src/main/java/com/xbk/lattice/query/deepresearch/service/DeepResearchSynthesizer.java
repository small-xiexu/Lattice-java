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
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        String answerMarkdown = answerProjectionBundle.getAnswerMarkdown();
        CitationCheckReport citationCheckReport = citationCheckService.check(answerMarkdown, answerProjectionBundle);
        if (citationCheckService.shouldRepair(citationCheckReport, CITATION_CHECK_OPTIONS, 0)) {
            answerMarkdown = citationCheckService.repair(answerMarkdown, citationCheckReport);
            answerProjectionBundle = citationCheckService.repairProjectionBundle(
                    answerProjectionBundle,
                    citationCheckReport,
                    answerMarkdown
            );
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
        answerBuilder.append("# 深度研究结论").append("\n\n");
        answerBuilder.append("## 问题").append("\n");
        answerBuilder.append(question == null ? "" : question.trim()).append("\n\n");
        answerBuilder.append("## 结论").append("\n");
        populateFactState(internalAnswerDraft, evidenceLedger);
        Map<String, List<FactFinding>> comparisonFindingsByTopic = isComparisonQuestion(question)
                ? collectComparisonFindingsByTopic(evidenceLedger)
                : Map.of();
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
            answerBuilder.append("- 当前证据不足，暂无法形成完整结论").append("\n");
        }
        answerBuilder.append("\n");
        if (!comparisonFindingsByTopic.isEmpty()) {
            appendStructuredComparisonDimensions(answerBuilder, question, comparisonFindingsByTopic);
        }
        if (layerSummaries != null && !layerSummaries.isEmpty()) {
            answerBuilder.append("## 分层摘要").append("\n");
            for (LayerSummary layerSummary : layerSummaries) {
                answerBuilder.append("- 第 ").append(layerSummary.getLayerIndex() + 1).append(" 层：")
                        .append(resolveLayerSummaryMarkdown(layerSummary))
                        .append("\n");
            }
            answerBuilder.append("\n");
        }
        if (evidenceLedger != null && evidenceLedger.hasConflicts()) {
            answerBuilder.append("## 冲突提示").append("\n");
            answerBuilder.append("- 当前证据链中存在不同来源结论不一致的情况，建议结合原始文档进一步确认。").append("\n");
        }
        if (!internalAnswerDraft.getMissingFactKeys().isEmpty()) {
            answerBuilder.append("\n## 缺失事实").append("\n");
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
     * 为带维度提示的对比题追加结构化维度整理。
     *
     * @param answerBuilder 答案构建器
     * @param question 原始问题
     * @param comparisonFindingsByTopic 主体到 finding 的映射
     */
    private void appendStructuredComparisonDimensions(
            StringBuilder answerBuilder,
            String question,
            Map<String, List<FactFinding>> comparisonFindingsByTopic
    ) {
        List<String> dimensions = extractComparisonDimensions(question);
        if (answerBuilder == null || dimensions.isEmpty() || comparisonFindingsByTopic == null || comparisonFindingsByTopic.size() < 2) {
            return;
        }
        answerBuilder.append("## 按维度对比").append("\n\n");
        for (String dimension : dimensions) {
            answerBuilder.append("### ").append(dimension).append("\n");
            for (Map.Entry<String, List<FactFinding>> entry : comparisonFindingsByTopic.entrySet()) {
                List<FactFinding> dimensionFindings = selectDimensionFindings(dimension, entry.getValue());
                if (dimensionFindings.isEmpty()) {
                    answerBuilder.append("- ")
                            .append(entry.getKey())
                            .append("：当前证据未直接说明该维度。")
                            .append("\n");
                    continue;
                }
                for (FactFinding dimensionFinding : distinctFindings(dimensionFindings, 2)) {
                    answerBuilder.append("- ")
                            .append(entry.getKey())
                            .append("：")
                            .append(resolveClaimText(dimensionFinding))
                            .append(resolveAnchorLiterals(dimensionFinding))
                            .append("\n");
                }
            }
            answerBuilder.append("\n");
        }
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
     * 判断当前问题是否为带对比语义的问题。
     *
     * @param question 原始问题
     * @return 是时返回 true
     */
    private boolean isComparisonQuestion(String question) {
        return question != null && question.contains("对比");
    }

    /**
     * 提取用户要求的对比维度。
     *
     * @param question 原始问题
     * @return 维度列表
     */
    private List<String> extractComparisonDimensions(String question) {
        List<String> dimensions = new ArrayList<String>();
        if (question == null || question.isBlank() || !question.contains("对比")) {
            return dimensions;
        }
        String prefix = question.substring(0, question.indexOf("对比")).trim();
        prefix = prefix.replaceFirst("^请按", "").trim();
        for (String part : prefix.split("[、，,和与及]")) {
            String dimension = normalizeComparisonDimension(part);
            if (!dimension.isBlank() && !dimensions.contains(dimension)) {
                dimensions.add(dimension);
            }
        }
        return dimensions;
    }

    /**
     * 规范化对比维度文本。
     *
     * @param rawDimension 原始维度
     * @return 规范化后的维度
     */
    private String normalizeComparisonDimension(String rawDimension) {
        if (rawDimension == null || rawDimension.isBlank()) {
            return "";
        }
        String dimension = rawDimension.trim().replaceAll("[：:。？?]+$", "");
        if (dimension.equals("请按")) {
            return "";
        }
        return dimension;
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
     * 为某个维度挑选更贴近该维度的 finding。
     *
     * @param dimension 对比维度
     * @param factFindings finding 列表
     * @return 筛选后的 finding
     */
    private List<FactFinding> selectDimensionFindings(String dimension, List<FactFinding> factFindings) {
        List<FactFinding> selectedFindings = new ArrayList<FactFinding>();
        if (dimension == null || factFindings == null || factFindings.isEmpty()) {
            return selectedFindings;
        }
        int bestScore = Integer.MIN_VALUE;
        for (FactFinding factFinding : factFindings) {
            String claimText = resolveClaimText(factFinding);
            int score = scoreDimensionFinding(dimension, claimText);
            if (score > bestScore) {
                selectedFindings.clear();
                selectedFindings.add(factFinding);
                bestScore = score;
                continue;
            }
            if (score == bestScore && score > 0 && selectedFindings.size() < 2) {
                selectedFindings.add(factFinding);
            }
        }
        if (!selectedFindings.isEmpty() && bestScore > 0) {
            return selectedFindings;
        }
        selectedFindings.clear();
        selectedFindings.add(factFindings.get(0));
        if (factFindings.size() > 1 && "实现方式".equals(dimension)) {
            selectedFindings.add(factFindings.get(1));
        }
        return selectedFindings;
    }

    /**
     * 计算 finding 与指定对比维度的贴合度。
     *
     * @param dimension 对比维度
     * @param claimText finding 文本
     * @return 分值
     */
    private int scoreDimensionFinding(String dimension, String claimText) {
        if (dimension == null || claimText == null || claimText.isBlank()) {
            return 0;
        }
        int score = 0;
        if ("目标".equals(dimension)) {
            if (claimText.contains("避免") || claimText.contains("保证") || claimText.contains("规避")) {
                score += 6;
            }
            if (claimText.contains("重试") || claimText.contains("恢复")) {
                score += 4;
            }
        }
        if ("触发时机".equals(dimension)) {
            if (claimText.contains("失败后") || claimText.contains("场景") || claimText.contains("扣减")) {
                score += 6;
            }
            if (claimText.contains("进入") || claimText.contains("触发")) {
                score += 3;
            }
        }
        if ("实现方式".equals(dimension)) {
            if (claimText.contains("采用") || claimText.contains("通过") || claimText.contains("使用")) {
                score += 6;
            }
            if (claimText.contains("Redis")
                    || claimText.contains("乐观锁")
                    || claimText.contains("retry_queue")
                    || claimText.contains("RetryWorker")
                    || claimText.contains("字段")) {
                score += 4;
            }
        }
        return score;
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
            return "当前层未取得有效证据";
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
            return "当前层未取得有效证据";
        }
        return String.join("；", normalizedSegments);
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
