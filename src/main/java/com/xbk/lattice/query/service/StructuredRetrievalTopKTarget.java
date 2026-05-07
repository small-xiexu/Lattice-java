package com.xbk.lattice.query.service;

/**
 * 结构化检索 topK 目标证据
 *
 * 职责：用通用 evidenceType 与命中身份描述期望进入 topK 的证据
 *
 * @author xiexu
 */
public class StructuredRetrievalTopKTarget {

    private final QueryEvidenceType evidenceType;

    private final String articleKey;

    private final String conceptId;

    /**
     * 创建结构化检索 topK 目标证据。
     *
     * @param evidenceType 证据类型
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     */
    public StructuredRetrievalTopKTarget(QueryEvidenceType evidenceType, String articleKey, String conceptId) {
        this.evidenceType = evidenceType;
        this.articleKey = articleKey;
        this.conceptId = conceptId;
    }

    /**
     * 使用 articleKey 创建目标证据。
     *
     * @param evidenceType 证据类型
     * @param articleKey 文章唯一键
     * @return 目标证据
     */
    public static StructuredRetrievalTopKTarget forArticleKey(QueryEvidenceType evidenceType, String articleKey) {
        return new StructuredRetrievalTopKTarget(evidenceType, articleKey, null);
    }

    /**
     * 使用 conceptId 创建目标证据。
     *
     * @param evidenceType 证据类型
     * @param conceptId 概念标识
     * @return 目标证据
     */
    public static StructuredRetrievalTopKTarget forConceptId(QueryEvidenceType evidenceType, String conceptId) {
        return new StructuredRetrievalTopKTarget(evidenceType, null, conceptId);
    }

    /**
     * 获取证据类型。
     *
     * @return 证据类型
     */
    public QueryEvidenceType getEvidenceType() {
        return evidenceType;
    }

    /**
     * 获取文章唯一键。
     *
     * @return 文章唯一键
     */
    public String getArticleKey() {
        return articleKey;
    }

    /**
     * 获取概念标识。
     *
     * @return 概念标识
     */
    public String getConceptId() {
        return conceptId;
    }

    /**
     * 判断目标是否具备可匹配身份。
     *
     * @return 具备身份返回 true
     */
    public boolean hasIdentity() {
        return !isBlank(articleKey) || !isBlank(conceptId);
    }

    /**
     * 判断查询命中是否匹配该目标。
     *
     * @param queryArticleHit 查询命中
     * @return 匹配返回 true
     */
    public boolean matches(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null || evidenceType == null || queryArticleHit.getEvidenceType() != evidenceType) {
            return false;
        }
        if (!isBlank(articleKey) && !articleKey.equals(queryArticleHit.getArticleKey())) {
            return false;
        }
        if (!isBlank(conceptId) && !conceptId.equals(queryArticleHit.getConceptId())) {
            return false;
        }
        return hasIdentity();
    }

    /**
     * 判断文本是否为空。
     *
     * @param text 文本
     * @return 为空返回 true
     */
    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
