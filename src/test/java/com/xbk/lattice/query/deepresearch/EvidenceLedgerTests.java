package com.xbk.lattice.query.deepresearch;

import com.xbk.lattice.query.deepresearch.domain.EvidenceCard;
import com.xbk.lattice.query.deepresearch.domain.EvidenceFinding;
import com.xbk.lattice.query.deepresearch.domain.EvidenceLedger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EvidenceLedger 测试
 *
 * 职责：验证同一主题不同结论能够触发冲突探测
 */
class EvidenceLedgerTests {

    @Test
    void shouldDetectConflictsForSameTopicDifferentClaims() {
        EvidenceLedger evidenceLedger = new EvidenceLedger();
        evidenceLedger.addCard(card("DPFM 使用乐观锁", "article-a"));
        evidenceLedger.addCard(card("DPFM 使用 Redis 锁", "article-b"));

        assertThat(evidenceLedger.hasConflicts()).isTrue();
    }

    private EvidenceCard card(String claim, String sourceId) {
        EvidenceFinding evidenceFinding = new EvidenceFinding();
        evidenceFinding.setClaim(claim);
        evidenceFinding.setSourceId(sourceId);

        EvidenceCard evidenceCard = new EvidenceCard();
        evidenceCard.getFindings().add(evidenceFinding);
        return evidenceCard;
    }
}
