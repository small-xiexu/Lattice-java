package com.xbk.lattice.query.citation;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Citation 检查服务
 *
 * 职责：执行 claim 分段、引用核验与规则化修复
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class CitationCheckService {

    private final CitationExtractor citationExtractor;

    private final CitationValidator citationValidator;

    /**
     * 创建 Citation 检查服务。
     *
     * @param citationExtractor 引用提取器
     * @param citationValidator 引用校验器
     */
    public CitationCheckService(CitationExtractor citationExtractor, CitationValidator citationValidator) {
        this.citationExtractor = citationExtractor;
        this.citationValidator = citationValidator;
    }

    /**
     * 执行一次规则校验。
     *
     * @param answerMarkdown 答案 Markdown
     * @return 检查报告
     */
    public CitationCheckReport check(String answerMarkdown) {
        List<ClaimSegment> claimSegments = citationExtractor.extractClaims(answerMarkdown);
        List<CitationValidationResult> validationResults = new ArrayList<CitationValidationResult>();
        int verifiedCount = 0;
        int demotedCount = 0;
        int skippedCount = 0;
        int coveredClaimCount = 0;
        int unsupportedClaimCount = 0;
        for (ClaimSegment claimSegment : claimSegments) {
            boolean claimCovered = false;
            boolean claimSupported = false;
            boolean indeterminateClaim = false;
            for (Citation citation : claimSegment.getCitations()) {
                CitationValidationResult validationResult = citationValidator.validate(citation);
                validationResults.add(validationResult);
                if (validationResult.isVerified()) {
                    verifiedCount++;
                    claimCovered = true;
                    claimSupported = true;
                }
                else if (validationResult.isSkipped()) {
                    skippedCount++;
                    claimCovered = true;
                    if ("no_hard_fact_literals".equals(validationResult.getReason())) {
                        indeterminateClaim = true;
                    }
                }
                else {
                    demotedCount++;
                }
            }
            if (claimCovered) {
                coveredClaimCount++;
            }
            if (!claimSupported && !indeterminateClaim) {
                unsupportedClaimCount++;
            }
        }
        boolean noCitation = claimSegments.stream().allMatch(claimSegment -> claimSegment.getCitations().isEmpty());
        double coverageRate = claimSegments.isEmpty() ? 0.0D : coveredClaimCount * 1.0D / claimSegments.size();
        return new CitationCheckReport(
                answerMarkdown,
                claimSegments,
                validationResults,
                verifiedCount,
                demotedCount,
                skippedCount,
                noCitation,
                coverageRate,
                unsupportedClaimCount
        );
    }

    /**
     * 判断当前报告是否需要进入修复。
     *
     * @param report 检查报告
     * @param options 检查选项
     * @param repairAttemptCount 已执行修复次数
     * @return 是否需要修复
     */
    public boolean shouldRepair(CitationCheckReport report, CitationCheckOptions options, int repairAttemptCount) {
        if (report == null || options == null) {
            return false;
        }
        if (repairAttemptCount >= options.getMaxRepairRounds()) {
            return false;
        }
        return report.getDemotedCount() > 0 || report.getCoverageRate() < options.getMinCitationCoverage();
    }

    /**
     * 对答案执行一次规则化修复。
     *
     * @param answerMarkdown 原始答案
     * @param report 检查报告
     * @return 修复后的答案
     */
    public String repair(String answerMarkdown, CitationCheckReport report) {
        if (answerMarkdown == null || answerMarkdown.isBlank() || report == null) {
            return answerMarkdown;
        }
        String repairedAnswer = answerMarkdown;
        for (CitationValidationResult validationResult : report.getResults()) {
            if (!validationResult.isDemoted()) {
                continue;
            }
            Citation failedCitation = findCitation(report.getClaimSegments(), validationResult.getOrdinal());
            if (failedCitation == null) {
                continue;
            }
            repairedAnswer = repairedAnswer.replace(
                    failedCitation.getLiteral(),
                    "（引用未通过核验：" + failedCitation.getTargetKey() + "）"
            );
        }
        for (ClaimSegment claimSegment : report.getClaimSegments()) {
            if (!claimSegment.getCitations().isEmpty()) {
                continue;
            }
            if (claimSegment.getClaimText().contains("当前证据不足")) {
                continue;
            }
            repairedAnswer = repairedAnswer.replace(
                    claimSegment.getParagraphText(),
                    claimSegment.getClaimText() + "（当前证据不足）"
            );
        }
        return repairedAnswer;
    }

    private Citation findCitation(List<ClaimSegment> claimSegments, int ordinal) {
        for (ClaimSegment claimSegment : claimSegments) {
            for (Citation citation : claimSegment.getCitations()) {
                if (citation.getOrdinal() == ordinal) {
                    return citation;
                }
            }
        }
        return null;
    }
}
