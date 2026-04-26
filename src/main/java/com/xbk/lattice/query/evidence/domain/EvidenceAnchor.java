package com.xbk.lattice.query.evidence.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 证据锚点
 *
 * 职责：表示 claim/fact 可回指的最小证据单元
 *
 * @author xiexu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceAnchor {

    private String anchorId;

    private EvidenceAnchorSourceType sourceType;

    private String sourceId;

    private String path;

    private Integer lineStart;

    private Integer lineEnd;

    private String chunkId;

    private String quoteText;

    private double retrievalScore;

    private String contentHash;

    private EvidenceAnchorValidationStatus validationStatus = EvidenceAnchorValidationStatus.RAW;

    /**
     * 返回按 sourceType 冻结后的锚点身份串。
     *
     * @return 可用于生成 content hash 的规范化身份串
     */
    public String identitySignature() {
        if (sourceType == null) {
            return "";
        }
        switch (sourceType) {
            case ARTICLE:
                return sourceType.name() + "|" + normalize(sourceId) + "|" + normalizeChunk(chunkId) + "|" + normalize(quoteText);
            case SOURCE_FILE:
                return sourceType.name() + "|" + normalize(sourceId) + "|" + normalizeLine(lineStart) + "|" + normalizeLine(lineEnd)
                        + "|" + normalize(quoteText);
            case GRAPH_FACT:
            case CONTRIBUTION:
                return sourceType.name() + "|" + normalize(sourceId) + "|" + normalize(quoteText);
            default:
                return "";
        }
    }

    /**
     * 判断当前锚点是否满足最小 identity 前提。
     *
     * @return 仅当来源类型与关键标识齐备时返回 true
     */
    public boolean hasReusableIdentity() {
        return !identitySignature().isBlank();
    }

    /**
     * 规范化普通文本字段。
     *
     * @param value 待规范化字段
     * @return 去空白后的文本，缺失时返回空串
     */
    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 规范化 chunk 标识。
     *
     * @param value chunk 标识
     * @return article 缺省 chunk 时返回 `~`
     */
    private String normalizeChunk(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? "~" : normalized;
    }

    /**
     * 规范化行号。
     *
     * @param value 行号
     * @return 缺失时返回空串
     */
    private String normalizeLine(Integer value) {
        return value == null ? "" : String.valueOf(value);
    }
}
