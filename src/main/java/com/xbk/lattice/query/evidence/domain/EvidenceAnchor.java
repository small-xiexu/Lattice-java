package com.xbk.lattice.query.evidence.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 证据锚点。
 *
 * <p>表示 claim/fact 可回指的最小证据单元——通过 identitySignature() 冻结后用于内容去重和引用匹配。
 *
 * @author xiexu
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceAnchor {

    /** 锚点唯一标识。 */
    private String anchorId;
    /** 来源类型（ARTICLE / SOURCE_FILE / GRAPH_FACT / CONTRIBUTION）。 */
    private EvidenceAnchorSourceType sourceType;
    /** 来源实体 ID。 */
    private String sourceId;
    /** 来源文件路径（sourceType=SOURCE_FILE 时有值）。 */
    private String path;
    /** 行号起始。null 表示无行号信息。 */
    private Integer lineStart;
    /** 行号结束。null 表示无行号信息。 */
    private Integer lineEnd;
    /** chunk 标识（sourceType=ARTICLE 时有值）。 */
    private String chunkId;
    /** 证据引用原文。可能很长。 */
    private String quoteText;
    /** 检索相关性分数。 */
    private double retrievalScore;
    /** 内容哈希（用于检测证据变更）。 */
    private String contentHash;
    /** 校验状态。默认 RAW（原始未校验）。 */
    private EvidenceAnchorValidationStatus validationStatus = EvidenceAnchorValidationStatus.RAW;

    /**
     * 返回按 sourceType 冻结后的锚点身份串——用于生成 content hash 的规范化身份串。
     */
    public String identitySignature() {
        if (sourceType == null) return "";
        switch (sourceType) {
            case ARTICLE:
                return sourceType.name() + "|" + normalize(sourceId) + "|" + normalizeChunk(chunkId) + "|" + normalize(quoteText);
            case SOURCE_FILE:
                return sourceType.name() + "|" + normalize(sourceId) + "|" + normalizeLine(lineStart) + "|" + normalizeLine(lineEnd) + "|" + normalize(quoteText);
            case GRAPH_FACT:
            case CONTRIBUTION:
                return sourceType.name() + "|" + normalize(sourceId) + "|" + normalize(quoteText);
            default:
                return "";
        }
    }

    public boolean hasReusableIdentity() {
        return !identitySignature().isBlank();
    }

    private String normalize(String value) { return value == null ? "" : value.trim(); }

    private String normalizeChunk(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? "~" : normalized;
    }

    private String normalizeLine(Integer value) { return value == null ? "" : String.valueOf(value); }
}
