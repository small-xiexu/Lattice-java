package com.xbk.lattice.query.evidence;

import com.xbk.lattice.query.evidence.domain.EvidenceAnchor;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchorSourceType;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchorValidationStatus;
import com.xbk.lattice.query.evidence.domain.FactFinding;
import com.xbk.lattice.query.evidence.domain.FactValueType;
import com.xbk.lattice.query.evidence.domain.FindingSupportLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 证据领域身份规则测试
 *
 * 职责：验证 v2.6 的 factKey 与锚点 identity 冻结规则
 *
 * @author xiexu
 */
class EvidenceDomainIdentityTests {

    /**
     * 验证 FactFinding 会按 `subject.predicate.qualifier` 生成冻结 factKey。
     */
    @Test
    void shouldBuildFrozenFactKeyAndMergeIdentityForFactFinding() {
        FactFinding factFinding = new FactFinding();
        factFinding.setFactKey("payment.retry.maxAttempts.current_config");
        factFinding.setSubject("payment.retry");
        factFinding.setPredicate("maxAttempts");
        factFinding.setQualifier("current_config");
        factFinding.setValueText("5");
        factFinding.setValueType(FactValueType.NUMBER);
        factFinding.setUnit("times");
        factFinding.setClaimText("当前配置中最多重试 5 次");
        factFinding.setSupportLevel(FindingSupportLevel.DIRECT);
        factFinding.setAnchorIds(List.of("ev#1"));

        assertThat(factFinding.expectedFactKey()).isEqualTo("payment.retry.maxAttempts.current_config");
        assertThat(factFinding.matchesFrozenFactKey()).isTrue();
        assertThat(factFinding.mergeIdentity()).isEqualTo("payment.retry.maxAttempts.current_config|5|times");
        assertThat(factFinding.canEnterLedger()).isTrue();
    }

    /**
     * 验证缺少 anchor 的 finding 不允许直接进入 ledger。
     */
    @Test
    void shouldRejectFactFindingWithoutAnchorsForLedgerEntry() {
        FactFinding factFinding = new FactFinding();
        factFinding.setFactKey("payment.retry.maxAttempts.current_config");
        factFinding.setSubject("payment.retry");
        factFinding.setPredicate("maxAttempts");
        factFinding.setQualifier("current_config");
        factFinding.setValueText("5");

        assertThat(factFinding.matchesFrozenFactKey()).isTrue();
        assertThat(factFinding.canEnterLedger()).isFalse();
    }

    /**
     * 验证 EvidenceAnchor 会按 sourceType 生成分型 identity。
     */
    @Test
    void shouldBuildEvidenceAnchorIdentityBySourceType() {
        EvidenceAnchor articleAnchor = new EvidenceAnchor();
        articleAnchor.setAnchorId("ev#1");
        articleAnchor.setSourceType(EvidenceAnchorSourceType.ARTICLE);
        articleAnchor.setSourceId("payment-routing");
        articleAnchor.setChunkId("chunk-1");
        articleAnchor.setQuoteText("默认最多重试 5 次");

        EvidenceAnchor sourceFileAnchor = new EvidenceAnchor();
        sourceFileAnchor.setAnchorId("ev#2");
        sourceFileAnchor.setSourceType(EvidenceAnchorSourceType.SOURCE_FILE);
        sourceFileAnchor.setSourceId("docs/payment.md");
        sourceFileAnchor.setPath("docs/payment.md");
        sourceFileAnchor.setLineStart(Integer.valueOf(10));
        sourceFileAnchor.setLineEnd(Integer.valueOf(12));
        sourceFileAnchor.setQuoteText("retry.max-attempts: 5");

        EvidenceAnchor graphFactAnchor = new EvidenceAnchor();
        graphFactAnchor.setAnchorId("ev#3");
        graphFactAnchor.setSourceType(EvidenceAnchorSourceType.GRAPH_FACT);
        graphFactAnchor.setSourceId("payment.retry.maxAttempts.current_config");
        graphFactAnchor.setQuoteText("payment.retry.maxAttempts.current_config=5");

        assertThat(articleAnchor.identitySignature()).isEqualTo("ARTICLE|payment-routing|chunk-1|默认最多重试 5 次");
        assertThat(sourceFileAnchor.identitySignature()).isEqualTo("SOURCE_FILE|docs/payment.md|10|12|retry.max-attempts: 5");
        assertThat(graphFactAnchor.identitySignature())
                .isEqualTo("GRAPH_FACT|payment.retry.maxAttempts.current_config|payment.retry.maxAttempts.current_config=5");
        assertThat(articleAnchor.hasReusableIdentity()).isTrue();
    }

    /**
     * 验证锚点校验状态已补齐 `SKIPPED`，与 v2.6 DDL 保持一致。
     */
    @Test
    void shouldContainSkippedAnchorValidationStatus() {
        assertThat(EvidenceAnchorValidationStatus.values()).contains(EvidenceAnchorValidationStatus.SKIPPED);
    }
}
