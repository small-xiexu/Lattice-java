package com.xbk.lattice.query.deepresearch.projector;

import com.xbk.lattice.query.deepresearch.domain.EvidenceLedger;
import com.xbk.lattice.query.deepresearch.domain.InternalAnswerDraft;
import com.xbk.lattice.query.evidence.domain.AnswerProjection;
import com.xbk.lattice.query.evidence.domain.AnswerProjectionBundle;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchor;
import com.xbk.lattice.query.evidence.domain.ProjectionCandidate;
import com.xbk.lattice.query.evidence.domain.ProjectionCitationFormat;
import com.xbk.lattice.query.evidence.domain.ProjectionStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deep Research 答案投影器
 *
 * 职责：把内部 ev#N 草稿投影为最终用户可见答案与 citation 白名单
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class DeepResearchProjector {

    /**
     * 将内部草稿投影成最终答案。
     *
     * @param internalAnswerDraft 内部答案草稿
     * @param evidenceLedger 证据账本
     * @return 答案投影包
     */
    public AnswerProjectionBundle project(InternalAnswerDraft internalAnswerDraft, EvidenceLedger evidenceLedger) {
        if (internalAnswerDraft == null
                || internalAnswerDraft.getDraftMarkdown() == null
                || internalAnswerDraft.getDraftMarkdown().isBlank()
                || evidenceLedger == null) {
            return insufficientBundle();
        }
        Map<String, AnswerProjection> activeProjectionByLiteral = new LinkedHashMap<String, AnswerProjection>();
        String answerMarkdown = internalAnswerDraft.getDraftMarkdown();
        for (ProjectionCandidate projectionCandidate : evidenceLedger.getProjectionCandidates()) {
            AnswerProjection answerProjection = toAnswerProjection(projectionCandidate, evidenceLedger, activeProjectionByLiteral.size() + 1);
            if (answerProjection == null) {
                continue;
            }
            activeProjectionByLiteral.putIfAbsent(answerProjection.getCitationLiteral(), answerProjection);
            answerMarkdown = replaceEvidenceId(answerMarkdown, projectionCandidate.getAnchorId(), answerProjection.getCitationLiteral());
        }
        answerMarkdown = cleanupMarkdown(removeUnprojectedEvidenceIds(answerMarkdown));
        if (activeProjectionByLiteral.isEmpty()) {
            return insufficientBundle();
        }
        return new AnswerProjectionBundle(
                answerMarkdown.trim(),
                new ArrayList<AnswerProjection>(activeProjectionByLiteral.values())
        );
    }

    private AnswerProjection toAnswerProjection(
            ProjectionCandidate projectionCandidate,
            EvidenceLedger evidenceLedger,
            int projectionOrdinal
    ) {
        if (projectionCandidate == null
                || !projectionCandidate.isVerified()
                || projectionCandidate.getPreferredCitationFormat() == null
                || projectionCandidate.getAnchorId() == null
                || projectionCandidate.getAnchorId().isBlank()) {
            return null;
        }
        EvidenceAnchor evidenceAnchor = evidenceLedger.getAnchorsById().get(projectionCandidate.getAnchorId());
        if (evidenceAnchor == null) {
            return null;
        }
        String citationLiteral = renderCitationLiteral(projectionCandidate, evidenceAnchor);
        if (citationLiteral.isBlank()) {
            return null;
        }
        return new AnswerProjection(
                projectionOrdinal,
                projectionCandidate.getAnchorId(),
                projectionCandidate.getPreferredCitationFormat(),
                citationLiteral,
                projectionCandidate.getTargetKey(),
                ProjectionStatus.ACTIVE,
                0,
                null
        );
    }

    private String renderCitationLiteral(ProjectionCandidate projectionCandidate, EvidenceAnchor evidenceAnchor) {
        if (projectionCandidate.getPreferredCitationFormat() == ProjectionCitationFormat.ARTICLE) {
            return "[[" + projectionCandidate.getTargetKey() + "]]";
        }
        if (projectionCandidate.getPreferredCitationFormat() == ProjectionCitationFormat.SOURCE_FILE) {
            if (evidenceAnchor.getLineStart() != null && evidenceAnchor.getLineEnd() != null) {
                return "[→ " + projectionCandidate.getTargetKey() + ", lines "
                        + evidenceAnchor.getLineStart() + "-" + evidenceAnchor.getLineEnd() + "]";
            }
            return "[→ " + projectionCandidate.getTargetKey() + "]";
        }
        return "";
    }

    private String removeUnprojectedEvidenceIds(String answerMarkdown) {
        return answerMarkdown.replaceAll("\\s*\\(?ev#\\d+\\)?", "");
    }

    private String replaceEvidenceId(String answerMarkdown, String anchorId, String citationLiteral) {
        if (answerMarkdown == null || anchorId == null || anchorId.isBlank() || citationLiteral == null) {
            return answerMarkdown;
        }
        String replacedMarkdown = answerMarkdown.replace("(" + anchorId + ")", citationLiteral);
        return replacedMarkdown.replace(anchorId, citationLiteral);
    }

    private String cleanupMarkdown(String answerMarkdown) {
        if (answerMarkdown == null || answerMarkdown.isBlank()) {
            return answerMarkdown;
        }
        String cleanedMarkdown = answerMarkdown
                .replaceAll("\\(\\s*\\)", "")
                .replace("\r\n", "\n")
                .replaceAll("[ \t]{2,}", " ")
                .replaceAll("[ \t]+\n", "\n")
                .replaceAll("\\n{3,}", "\n\n");
        return cleanedMarkdown.trim();
    }

    private AnswerProjectionBundle insufficientBundle() {
        return new AnswerProjectionBundle(
                "当前证据不足，无法生成可核验引用版答案",
                List.of()
        );
    }
}
