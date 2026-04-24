package com.xbk.lattice.query.deepresearch.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 证据账本
 *
 * 职责：维护 Deep Research 的内部证据链与冲突探测结果
 *
 * @author xiexu
 */
@Data
public class EvidenceLedger {

    private List<EvidenceCard> cards = new ArrayList<EvidenceCard>();

    /**
     * 追加证据卡。
     *
     * @param card 证据卡
     */
    public void addCard(EvidenceCard card) {
        if (card != null) {
            cards.add(card);
        }
    }

    /**
     * 追加证据卡列表。
     *
     * @param evidenceCards 证据卡列表
     */
    public void addCards(List<EvidenceCard> evidenceCards) {
        if (evidenceCards == null || evidenceCards.isEmpty()) {
            return;
        }
        for (EvidenceCard evidenceCard : evidenceCards) {
            addCard(evidenceCard);
        }
    }

    /**
     * 返回证据卡数量。
     *
     * @return 证据卡数量
     */
    public int cardCount() {
        return cards.size();
    }

    /**
     * 返回是否存在冲突证据。
     *
     * @return 是否存在冲突证据
     */
    public boolean hasConflicts() {
        Map<String, ClaimObservation> observationByKey = new LinkedHashMap<String, ClaimObservation>();
        for (EvidenceCard card : cards) {
            for (EvidenceFinding finding : card.getFindings()) {
                String normalizedClaim = normalizeClaim(finding.getClaim());
                if (normalizedClaim.isBlank()) {
                    continue;
                }
                String conflictKey = resolveConflictKey(normalizedClaim);
                if (conflictKey.isBlank()) {
                    continue;
                }
                String currentTarget = finding.getSourceId() == null ? "" : finding.getSourceId();
                ClaimObservation existingObservation = observationByKey.putIfAbsent(
                        conflictKey,
                        new ClaimObservation(normalizedClaim, currentTarget)
                );
                if (existingObservation != null
                        && (!existingObservation.normalizedClaim.equals(normalizedClaim)
                        || !existingObservation.sourceId.equals(currentTarget))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String normalizeClaim(String claim) {
        if (claim == null) {
            return "";
        }
        return claim.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveConflictKey(String normalizedClaim) {
        if (normalizedClaim == null || normalizedClaim.isBlank()) {
            return "";
        }
        String cleanedClaim = normalizedClaim.replaceAll("\\s+", " ").trim();
        String[] separators = {"采用", "使用", "通过", "暴露", "调用", "进入", "依赖", "是", "为"};
        for (String separator : separators) {
            int index = cleanedClaim.indexOf(separator);
            if (index > 1) {
                return cleanedClaim.substring(0, index).trim();
            }
        }
        int colonIndex = cleanedClaim.indexOf('：');
        if (colonIndex > 1) {
            return cleanedClaim.substring(0, colonIndex).trim();
        }
        return cleanedClaim;
    }

    private static class ClaimObservation {

        private final String normalizedClaim;

        private final String sourceId;

        private ClaimObservation(String normalizedClaim, String sourceId) {
            this.normalizedClaim = normalizedClaim;
            this.sourceId = sourceId;
        }
    }
}
