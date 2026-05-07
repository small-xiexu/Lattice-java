package com.xbk.lattice.query.deepresearch.validator;

import com.xbk.lattice.query.evidence.domain.EvidenceAnchor;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchorSourceType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Deep Research 锚点校验器
 *
 * 职责：校验 EvidenceAnchor 的合法字段组合，并冻结 content hash 生成公式
 *
 * @author xiexu
 */
@Component
public class DeepResearchAnchorValidator {

    /**
     * 校验并归一化锚点。
     *
     * @param evidenceAnchor 待校验锚点
     * @return 已写入 content hash 的锚点
     */
    public EvidenceAnchor validateAndNormalize(EvidenceAnchor evidenceAnchor) {
        validate(evidenceAnchor);
        evidenceAnchor.setContentHash(buildContentHash(evidenceAnchor));
        return evidenceAnchor;
    }

    /**
     * 校验锚点字段组合是否合法。
     *
     * @param evidenceAnchor 待校验锚点
     */
    public void validate(EvidenceAnchor evidenceAnchor) {
        if (evidenceAnchor == null) {
            throw new IllegalArgumentException("evidenceAnchor 不能为空");
        }
        if (isBlank(evidenceAnchor.getAnchorId()) || !evidenceAnchor.getAnchorId().matches("ev#\\d+")) {
            throw new IllegalArgumentException("anchorId 必须满足 ev#N 格式");
        }
        if (evidenceAnchor.getSourceType() == null) {
            throw new IllegalArgumentException("sourceType 不能为空");
        }
        if (isBlank(evidenceAnchor.getSourceId())) {
            throw new IllegalArgumentException("sourceId 不能为空");
        }
        if (isBlank(evidenceAnchor.getQuoteText())) {
            throw new IllegalArgumentException("quoteText 不能为空");
        }
        switch (evidenceAnchor.getSourceType()) {
            case ARTICLE:
                validateArticleAnchor(evidenceAnchor);
                break;
            case SOURCE_FILE:
                validateSourceFileAnchor(evidenceAnchor);
                break;
            case GRAPH_FACT:
            case CONTRIBUTION:
                validateInternalAnchor(evidenceAnchor);
                break;
            default:
                throw new IllegalArgumentException("不支持的 sourceType: " + evidenceAnchor.getSourceType());
        }
    }

    /**
     * 生成冻结后的 content hash。
     *
     * @param evidenceAnchor 证据锚点
     * @return SHA-256 形式的 content hash
     */
    public String buildContentHash(EvidenceAnchor evidenceAnchor) {
        String identitySignature = evidenceAnchor == null ? "" : evidenceAnchor.identitySignature();
        if (identitySignature.isBlank()) {
            throw new IllegalArgumentException("锚点 identitySignature 为空，无法生成 content hash");
        }
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(identitySignature.getBytes(StandardCharsets.UTF_8));
            StringBuilder hashBuilder = new StringBuilder();
            for (byte value : digest) {
                hashBuilder.append(String.format("%02x", Integer.valueOf(value & 0xff)));
            }
            return hashBuilder.toString();
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }

    private void validateArticleAnchor(EvidenceAnchor evidenceAnchor) {
        if (!isBlank(evidenceAnchor.getPath())) {
            throw new IllegalArgumentException("ARTICLE 锚点不允许携带 path");
        }
        if (evidenceAnchor.getLineStart() != null || evidenceAnchor.getLineEnd() != null) {
            throw new IllegalArgumentException("ARTICLE 锚点不允许携带行号");
        }
    }

    private void validateSourceFileAnchor(EvidenceAnchor evidenceAnchor) {
        if (isBlank(evidenceAnchor.getPath()) || !evidenceAnchor.getSourceId().equals(evidenceAnchor.getPath())) {
            throw new IllegalArgumentException("SOURCE_FILE 锚点要求 path 与 sourceId 完全一致");
        }
        if (!isBlank(evidenceAnchor.getChunkId())) {
            throw new IllegalArgumentException("SOURCE_FILE 锚点不允许携带 chunkId");
        }
        Integer lineStart = evidenceAnchor.getLineStart();
        Integer lineEnd = evidenceAnchor.getLineEnd();
        if ((lineStart == null) != (lineEnd == null)) {
            throw new IllegalArgumentException("SOURCE_FILE 锚点的 lineStart/lineEnd 必须成对出现");
        }
        if (lineStart != null && lineStart.intValue() > lineEnd.intValue()) {
            throw new IllegalArgumentException("SOURCE_FILE 锚点要求 lineStart <= lineEnd");
        }
    }

    private void validateInternalAnchor(EvidenceAnchor evidenceAnchor) {
        if (!isBlank(evidenceAnchor.getPath())) {
            throw new IllegalArgumentException(evidenceAnchor.getSourceType() + " 锚点不允许携带 path");
        }
        if (evidenceAnchor.getLineStart() != null || evidenceAnchor.getLineEnd() != null) {
            throw new IllegalArgumentException(evidenceAnchor.getSourceType() + " 锚点不允许携带行号");
        }
        if (!isBlank(evidenceAnchor.getChunkId())) {
            throw new IllegalArgumentException(evidenceAnchor.getSourceType() + " 锚点不允许携带 chunkId");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
