package com.xbk.lattice.query.deepresearch;

import com.xbk.lattice.query.deepresearch.validator.DeepResearchAnchorValidator;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchor;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchorSourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DeepResearchAnchorValidator 测试
 *
 * 职责：验证锚点字段组合与 content hash 分型公式
 *
 * @author xiexu
 */
class DeepResearchAnchorValidatorTests {

    /**
     * 验证 ARTICLE 锚点可通过校验并生成稳定 content hash。
     */
    @Test
    void shouldValidateArticleAnchorAndBuildStableContentHash() {
        DeepResearchAnchorValidator deepResearchAnchorValidator = new DeepResearchAnchorValidator();
        EvidenceAnchor evidenceAnchor = new EvidenceAnchor();
        evidenceAnchor.setAnchorId("ev#12");
        evidenceAnchor.setSourceType(EvidenceAnchorSourceType.ARTICLE);
        evidenceAnchor.setSourceId("payment-routing");
        evidenceAnchor.setChunkId("chunk-1");
        evidenceAnchor.setQuoteText("默认最多重试 5 次");

        deepResearchAnchorValidator.validateAndNormalize(evidenceAnchor);

        assertThat(evidenceAnchor.getContentHash()).hasSize(64);
        assertThat(evidenceAnchor.getContentHash())
                .isEqualTo(deepResearchAnchorValidator.buildContentHash(evidenceAnchor));
    }

    /**
     * 验证 SOURCE_FILE 锚点要求 path/sourceId 一致且行号成对出现。
     */
    @Test
    void shouldRejectInvalidSourceFileAnchorCombination() {
        DeepResearchAnchorValidator deepResearchAnchorValidator = new DeepResearchAnchorValidator();
        EvidenceAnchor evidenceAnchor = new EvidenceAnchor();
        evidenceAnchor.setAnchorId("ev#13");
        evidenceAnchor.setSourceType(EvidenceAnchorSourceType.SOURCE_FILE);
        evidenceAnchor.setSourceId("docs/payment.md");
        evidenceAnchor.setPath("docs/other.md");
        evidenceAnchor.setQuoteText("retry.max-attempts: 5");

        assertThatThrownBy(() -> deepResearchAnchorValidator.validateAndNormalize(evidenceAnchor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path 与 sourceId 完全一致");
    }

    /**
     * 验证 GRAPH_FACT 锚点不允许混入 path/line/chunk 字段。
     */
    @Test
    void shouldRejectGraphFactAnchorWithFileScopedFields() {
        DeepResearchAnchorValidator deepResearchAnchorValidator = new DeepResearchAnchorValidator();
        EvidenceAnchor evidenceAnchor = new EvidenceAnchor();
        evidenceAnchor.setAnchorId("ev#14");
        evidenceAnchor.setSourceType(EvidenceAnchorSourceType.GRAPH_FACT);
        evidenceAnchor.setSourceId("payment.retry.maxAttempts.current_config");
        evidenceAnchor.setPath("docs/payment.md");
        evidenceAnchor.setQuoteText("payment.retry.maxAttempts.current_config=5");

        assertThatThrownBy(() -> deepResearchAnchorValidator.validateAndNormalize(evidenceAnchor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GRAPH_FACT 锚点不允许携带 path");
    }
}
