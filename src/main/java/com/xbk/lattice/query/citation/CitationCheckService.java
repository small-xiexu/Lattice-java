package com.xbk.lattice.query.citation;

import com.xbk.lattice.query.evidence.domain.AnswerProjection;
import com.xbk.lattice.query.evidence.domain.AnswerProjectionBundle;
import com.xbk.lattice.query.evidence.domain.ProjectionCitationFormat;
import com.xbk.lattice.query.evidence.domain.ProjectionStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Citation 检查服务
 *
 * 职责：执行 claim 分段、引用核验与规则化修复
 *
 * @author xiexu
 */
@Service
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
        return check(answerMarkdown, null);
    }

    /**
     * 执行一次规则校验。
     *
     * @param answerMarkdown 答案 Markdown
     * @param answerProjectionBundle 最终答案 projection 白名单
     * @return 检查报告
     */
    public CitationCheckReport check(String answerMarkdown, AnswerProjectionBundle answerProjectionBundle) {
        List<ClaimSegment> claimSegments = citationExtractor.extractClaims(answerMarkdown);
        ProjectionLookup projectionLookup = buildProjectionLookup(answerProjectionBundle);
        List<CitationValidationResult> validationResults = new ArrayList<CitationValidationResult>();
        Set<String> usedProjectionLiterals = new LinkedHashSet<String>();
        int verifiedCount = 0;
        int demotedCount = 0;
        int skippedCount = 0;
        int coveredClaimCount = 0;
        int unsupportedClaimCount = 0;
        int unmatchedLiteralCount = 0;
        for (ClaimSegment claimSegment : claimSegments) {
            boolean claimCovered = false;
            boolean claimSupported = false;
            boolean indeterminateClaim = false;
            for (Citation citation : claimSegment.getCitations()) {
                CitationValidationResult validationResult = validateAgainstProjection(citation, projectionLookup);
                validationResults.add(validationResult);
                if (validationResult.isVerified()) {
                    verifiedCount++;
                    claimSupported = true;
                }
                else if (validationResult.isSkipped()) {
                    skippedCount++;
                    if ("no_hard_fact_literals".equals(validationResult.getReason())) {
                        indeterminateClaim = true;
                    }
                }
                else {
                    demotedCount++;
                    if ("projection_literal_not_found".equals(validationResult.getReason())) {
                        unmatchedLiteralCount++;
                    }
                }
                markProjectionUsage(citation, projectionLookup, usedProjectionLiterals);
            }
            if (claimSupported) {
                claimCovered = true;
                coveredClaimCount++;
            }
            else if (indeterminateClaim && !claimSegment.getCitations().isEmpty()) {
                claimCovered = true;
                coveredClaimCount++;
            }
            if (!claimSupported && !indeterminateClaim) {
                unsupportedClaimCount++;
            }
        }
        int unusedProjectionCount = countUnusedProjection(projectionLookup, usedProjectionLiterals);
        int projectionMismatchCount = unmatchedLiteralCount;
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
                unsupportedClaimCount,
                unmatchedLiteralCount,
                unusedProjectionCount,
                projectionMismatchCount
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
        return report.getDemotedCount() > 0
                || report.getCoverageRate() < options.getMinCitationCoverage()
                || report.getProjectionMismatchCount() > 0;
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
        for (ClaimSegment claimSegment : report.getClaimSegments()) {
            ClaimRepairDecision repairDecision = resolveClaimRepairDecision(claimSegment, report);
            if (!repairDecision.shouldRepair()) {
                continue;
            }
            if (repairDecision.shouldDowngradeClaim()) {
                String nearestUsableCitationLiteral = nearestUsableCitationLiteral(claimSegment, report);
                if (!nearestUsableCitationLiteral.isBlank()) {
                    repairedAnswer = repairedAnswer.replace(
                            claimSegment.getParagraphText(),
                            claimSegment.getClaimText() + " " + nearestUsableCitationLiteral
                    );
                    continue;
                }
                repairedAnswer = repairedAnswer.replace(
                        claimSegment.getParagraphText(),
                        appendEvidenceInsufficientMarker(claimSegment.getClaimText())
                );
                continue;
            }
            for (Citation citation : repairDecision.getFailedCitations()) {
                repairedAnswer = repairedAnswer.replace(citation.getLiteral(), "");
            }
        }
        for (ClaimSegment claimSegment : report.getClaimSegments()) {
            if (!claimSegment.getCitations().isEmpty()
                    || claimSegment.getClaimText().contains("当前证据不足")) {
                continue;
            }
            String nearestUsableCitationLiteral = nearestUsableCitationLiteral(claimSegment, report);
            if (!nearestUsableCitationLiteral.isBlank()) {
                repairedAnswer = repairedAnswer.replace(
                        claimSegment.getParagraphText(),
                        claimSegment.getClaimText() + " " + nearestUsableCitationLiteral
                );
                continue;
            }
            repairedAnswer = repairedAnswer.replace(
                    claimSegment.getParagraphText(),
                    appendEvidenceInsufficientMarker(claimSegment.getClaimText())
            );
        }
        return normalizeEvidenceInsufficientMarkers(repairedAnswer);
    }

    private String nearestUsableCitationLiteral(ClaimSegment currentClaimSegment, CitationCheckReport report) {
        if (currentClaimSegment == null || report == null || report.getClaimSegments() == null) {
            return "";
        }
        String nearestCitationLiteral = "";
        int currentIndex = currentClaimSegment.getClaimIndex();
        int nearestDistance = Integer.MAX_VALUE;
        for (ClaimSegment candidateClaimSegment : report.getClaimSegments()) {
            if (candidateClaimSegment == null || candidateClaimSegment.getCitations().isEmpty()) {
                continue;
            }
            int distance = Math.abs(candidateClaimSegment.getClaimIndex() - currentIndex);
            for (Citation citation : candidateClaimSegment.getCitations()) {
                CitationValidationResult validationResult = resolveValidationResult(report, citation.getOrdinal());
                if (validationResult != null && (validationResult.isVerified() || validationResult.isSkipped())) {
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearestCitationLiteral = citation.getLiteral();
                    }
                }
            }
        }
        return nearestCitationLiteral;
    }

    /**
     * 根据修复后的答案生成 append-only projection 历史。
     *
     * @param answerProjectionBundle 原 projection 白名单
     * @param report 修复前检查报告
     * @param repairedAnswerMarkdown 修复后的答案
     * @return 含历史状态的 projection 白名单
     */
    public AnswerProjectionBundle repairProjectionBundle(
            AnswerProjectionBundle answerProjectionBundle,
            CitationCheckReport report,
            String repairedAnswerMarkdown
    ) {
        if (answerProjectionBundle == null || answerProjectionBundle.getProjections() == null) {
            return answerProjectionBundle;
        }
        List<AnswerProjection> repairedProjections = new ArrayList<AnswerProjection>();
        int nextProjectionOrdinal = nextProjectionOrdinal(answerProjectionBundle.getProjections());
        for (AnswerProjection answerProjection : answerProjectionBundle.getProjections()) {
            if (answerProjection == null) {
                continue;
            }
            if (answerProjection.getStatus() != ProjectionStatus.ACTIVE
                    || literalStillUsed(repairedAnswerMarkdown, answerProjection.getCitationLiteral())) {
                repairedProjections.add(answerProjection);
                continue;
            }
            AnswerProjection replacedProjection = copyProjectionWithStatus(
                    answerProjection,
                    answerProjection.getProjectionOrdinal(),
                    ProjectionStatus.REPLACED,
                    answerProjection.getRepairRound(),
                    answerProjection.getRepairedFromProjectionOrdinal()
            );
            repairedProjections.add(replacedProjection);
            AnswerProjection removedProjection = copyProjectionWithStatus(
                    answerProjection,
                    nextProjectionOrdinal,
                    ProjectionStatus.REMOVED,
                    answerProjection.getRepairRound() + 1,
                    Integer.valueOf(answerProjection.getProjectionOrdinal())
            );
            repairedProjections.add(removedProjection);
            nextProjectionOrdinal++;
        }
        return new AnswerProjectionBundle(repairedAnswerMarkdown, repairedProjections);
    }

    private CitationValidationResult validateAgainstProjection(
            Citation citation,
            ProjectionLookup projectionLookup
    ) {
        if (citation == null) {
            return citationValidator.validate(null);
        }
        if (projectionLookup == null || !projectionLookup.isEnforced()) {
            return citationValidator.validate(citation);
        }
        if (projectionLookup.isDuplicate(citation.getLiteral())) {
            return new CitationValidationResult(
                    citation.getTargetKey(),
                    citation.getSourceType(),
                    CitationValidationStatus.DEMOTED,
                    0.0D,
                    "projection_literal_ambiguous",
                    "",
                    citation.getOrdinal()
            );
        }
        AnswerProjection answerProjection = projectionLookup.findUnique(citation.getLiteral());
        if (answerProjection == null) {
            return new CitationValidationResult(
                    citation.getTargetKey(),
                    citation.getSourceType(),
                    CitationValidationStatus.DEMOTED,
                    0.0D,
                    "projection_literal_not_found",
                    "",
                    citation.getOrdinal()
            );
        }
        CitationValidationResult projectionValidationResult = validateProjectionRecord(citation, answerProjection);
        if (projectionValidationResult != null) {
            return projectionValidationResult;
        }
        Citation projectedCitation = new Citation(
                citation.getOrdinal(),
                citation.getLiteral(),
                mapProjectionSourceType(answerProjection),
                answerProjection.getTargetKey(),
                citation.getClaimText(),
                citation.getContextWindow()
        );
        return citationValidator.validate(projectedCitation);
    }

    private int nextProjectionOrdinal(List<AnswerProjection> answerProjections) {
        int maxProjectionOrdinal = 0;
        if (answerProjections == null) {
            return 1;
        }
        for (AnswerProjection answerProjection : answerProjections) {
            if (answerProjection != null && answerProjection.getProjectionOrdinal() > maxProjectionOrdinal) {
                maxProjectionOrdinal = answerProjection.getProjectionOrdinal();
            }
        }
        return maxProjectionOrdinal + 1;
    }

    private boolean literalStillUsed(String answerMarkdown, String citationLiteral) {
        if (answerMarkdown == null || citationLiteral == null || citationLiteral.isBlank()) {
            return false;
        }
        return answerMarkdown.contains(citationLiteral);
    }

    private AnswerProjection copyProjectionWithStatus(
            AnswerProjection answerProjection,
            int projectionOrdinal,
            ProjectionStatus projectionStatus,
            int repairRound,
            Integer repairedFromProjectionOrdinal
    ) {
        return new AnswerProjection(
                projectionOrdinal,
                answerProjection.getAnchorId(),
                answerProjection.getSourceType(),
                answerProjection.getCitationLiteral(),
                answerProjection.getTargetKey(),
                projectionStatus,
                repairRound,
                repairedFromProjectionOrdinal
        );
    }

    private void markProjectionUsage(
            Citation citation,
            ProjectionLookup projectionLookup,
            Set<String> usedProjectionLiterals
    ) {
        if (citation == null || projectionLookup == null || !projectionLookup.isEnforced()) {
            return;
        }
        if (projectionLookup.containsActiveLiteral(citation.getLiteral())) {
            usedProjectionLiterals.add(citation.getLiteral());
        }
    }

    private int countUnusedProjection(
            ProjectionLookup projectionLookup,
            Set<String> usedProjectionLiterals
    ) {
        if (projectionLookup == null || !projectionLookup.isEnforced()) {
            return 0;
        }
        int unusedProjectionCount = 0;
        for (String literal : projectionLookup.getActiveLiterals()) {
            if (!usedProjectionLiterals.contains(literal)) {
                unusedProjectionCount++;
            }
        }
        return unusedProjectionCount;
    }

    private String appendEvidenceInsufficientMarker(String claimText) {
        String normalizedClaimText = claimText == null ? "" : claimText.replace("（当前证据不足）", "").trim();
        if (normalizedClaimText.isBlank()) {
            return "当前证据不足";
        }
        return normalizedClaimText + "（当前证据不足）";
    }

    private String normalizeEvidenceInsufficientMarkers(String answerMarkdown) {
        if (answerMarkdown == null || answerMarkdown.isBlank()) {
            return answerMarkdown;
        }
        String normalizedAnswer = answerMarkdown
                .replaceAll("(（当前证据不足）\\s*){2,}", "（当前证据不足）")
                .replaceAll("\\*\\*\\s*（当前证据不足）", "**（当前证据不足）")
                .replaceAll("（当前证据不足）\\s+([，。；：])", "（当前证据不足）$1");
        return normalizedAnswer;
    }

    private ProjectionLookup buildProjectionLookup(AnswerProjectionBundle answerProjectionBundle) {
        Map<String, AnswerProjection> activeProjectionByLiteral = new LinkedHashMap<String, AnswerProjection>();
        Set<String> activeLiterals = new LinkedHashSet<String>();
        Set<String> duplicateLiterals = new LinkedHashSet<String>();
        if (answerProjectionBundle == null || answerProjectionBundle.getProjections() == null) {
            return ProjectionLookup.notEnforced();
        }
        for (AnswerProjection answerProjection : answerProjectionBundle.getProjections()) {
            if (answerProjection == null
                    || answerProjection.getStatus() != ProjectionStatus.ACTIVE
                    || answerProjection.getCitationLiteral() == null
                    || answerProjection.getCitationLiteral().isBlank()) {
                continue;
            }
            String citationLiteral = answerProjection.getCitationLiteral();
            if (!activeLiterals.add(citationLiteral)) {
                duplicateLiterals.add(citationLiteral);
                activeProjectionByLiteral.remove(citationLiteral);
                continue;
            }
            activeProjectionByLiteral.put(citationLiteral, answerProjection);
        }
        return new ProjectionLookup(true, activeProjectionByLiteral, activeLiterals, duplicateLiterals);
    }

    private CitationValidationResult validateProjectionRecord(Citation citation, AnswerProjection answerProjection) {
        if (answerProjection.getSourceType() == null) {
            return projectionFailure(citation, "projection_source_type_missing");
        }
        if (answerProjection.getSourceType() != ProjectionCitationFormat.ARTICLE
                && answerProjection.getSourceType() != ProjectionCitationFormat.SOURCE_FILE) {
            return projectionFailure(citation, "projection_source_type_unsupported");
        }
        if (!matchesProjectionSourceType(citation, answerProjection)) {
            return projectionFailure(citation, "projection_source_type_mismatch");
        }
        if (answerProjection.getAnchorId() == null || answerProjection.getAnchorId().isBlank()) {
            return projectionFailure(citation, "projection_anchor_missing");
        }
        if (answerProjection.getTargetKey() == null || answerProjection.getTargetKey().isBlank()) {
            return projectionFailure(citation, "projection_target_key_missing");
        }
        return null;
    }

    private boolean matchesProjectionSourceType(Citation citation, AnswerProjection answerProjection) {
        if (citation == null || citation.getSourceType() == null || answerProjection == null) {
            return false;
        }
        if (citation.getSourceType() == CitationSourceType.ARTICLE) {
            return answerProjection.getSourceType() == ProjectionCitationFormat.ARTICLE;
        }
        if (citation.getSourceType() == CitationSourceType.SOURCE_FILE) {
            return answerProjection.getSourceType() == ProjectionCitationFormat.SOURCE_FILE;
        }
        return false;
    }

    private CitationValidationResult projectionFailure(Citation citation, String reason) {
        return new CitationValidationResult(
                citation.getTargetKey(),
                citation.getSourceType(),
                CitationValidationStatus.DEMOTED,
                0.0D,
                reason,
                "",
                citation.getOrdinal()
        );
    }

    private CitationSourceType mapProjectionSourceType(AnswerProjection answerProjection) {
        ProjectionCitationFormat sourceType = answerProjection == null ? null : answerProjection.getSourceType();
        if (sourceType == ProjectionCitationFormat.SOURCE_FILE) {
            return CitationSourceType.SOURCE_FILE;
        }
        return CitationSourceType.ARTICLE;
    }

    private ClaimRepairDecision resolveClaimRepairDecision(ClaimSegment claimSegment, CitationCheckReport report) {
        List<Citation> failedCitations = new ArrayList<Citation>();
        boolean hasVerifiedCitation = false;
        for (Citation citation : claimSegment.getCitations()) {
            CitationValidationResult validationResult = resolveValidationResult(report, citation.getOrdinal());
            if (validationResult == null) {
                failedCitations.add(citation);
                continue;
            }
            if (validationResult.isVerified()) {
                hasVerifiedCitation = true;
                continue;
            }
            if (validationResult.isDemoted()) {
                failedCitations.add(citation);
            }
        }
        return new ClaimRepairDecision(hasVerifiedCitation, failedCitations);
    }

    private CitationValidationResult resolveValidationResult(CitationCheckReport report, int ordinal) {
        if (report == null || report.getResults() == null) {
            return null;
        }
        for (CitationValidationResult result : report.getResults()) {
            if (result.getOrdinal() == ordinal) {
                return result;
            }
        }
        return null;
    }

    private static class ClaimRepairDecision {

        private final boolean hasVerifiedCitation;

        private final List<Citation> failedCitations;

        private ClaimRepairDecision(boolean hasVerifiedCitation, List<Citation> failedCitations) {
            this.hasVerifiedCitation = hasVerifiedCitation;
            this.failedCitations = failedCitations;
        }

        private boolean shouldRepair() {
            return !failedCitations.isEmpty();
        }

        private boolean shouldDowngradeClaim() {
            return !hasVerifiedCitation && !failedCitations.isEmpty();
        }

        private List<Citation> getFailedCitations() {
            return failedCitations;
        }
    }

    private static class ProjectionLookup {

        private final boolean enforced;

        private final Map<String, AnswerProjection> uniqueProjectionByLiteral;

        private final Set<String> activeLiterals;

        private final Set<String> duplicateLiterals;

        private ProjectionLookup(
                boolean enforced,
                Map<String, AnswerProjection> uniqueProjectionByLiteral,
                Set<String> activeLiterals,
                Set<String> duplicateLiterals
        ) {
            this.enforced = enforced;
            this.uniqueProjectionByLiteral = uniqueProjectionByLiteral;
            this.activeLiterals = activeLiterals;
            this.duplicateLiterals = duplicateLiterals;
        }

        private static ProjectionLookup notEnforced() {
            return new ProjectionLookup(
                    false,
                    new LinkedHashMap<String, AnswerProjection>(),
                    new LinkedHashSet<String>(),
                    new LinkedHashSet<String>()
            );
        }

        private boolean isEnforced() {
            return enforced;
        }

        private AnswerProjection findUnique(String citationLiteral) {
            return uniqueProjectionByLiteral.get(citationLiteral);
        }

        private boolean containsActiveLiteral(String citationLiteral) {
            return activeLiterals.contains(citationLiteral);
        }

        private boolean isDuplicate(String citationLiteral) {
            return duplicateLiterals.contains(citationLiteral);
        }

        private Set<String> getActiveLiterals() {
            return activeLiterals;
        }
    }
}
