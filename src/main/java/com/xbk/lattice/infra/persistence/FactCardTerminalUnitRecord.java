package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;
import com.xbk.lattice.query.evidence.domain.FactCardType;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 事实证据卡终端字段证据单元记录
 *
 * 职责：承载 fact_card_terminal_units 表的持久化数据
 *
 * @author xiexu
 */
public class FactCardTerminalUnitRecord {

    private final Long id;

    private final String unitId;

    private final String terminalUnitIdentity;

    private final Long factCardId;

    private final String cardId;

    private final Long sourceId;

    private final Long sourceFileId;

    private final List<Long> sourceChunkIds;

    private final List<Long> articleIds;

    private final FactCardType cardType;

    private final AnswerShape answerShape;

    private final String structure;

    private final int itemIndex;

    private final String keyPath;

    private final String parentPath;

    private final String terminalKey;

    private final String pathSegmentsJson;

    private final String fieldLabel;

    private final String fieldAliasesJson;

    private final String fieldDescription;

    private final String displayText;

    private final String valueText;

    private final String normalizedValue;

    private final String valueType;

    private final String sourceRefsJson;

    private final String ftsText;

    private final String metadataJson;

    private final FactCardReviewStatus reviewStatus;

    private final double confidence;

    private final String contentHash;

    private final OffsetDateTime createdAt;

    private final OffsetDateTime updatedAt;

    /**
     * 创建终端字段证据单元记录。
     *
     * @param unitId 稳定业务标识
     * @param terminalUnitIdentity 检索融合身份
     * @param factCardId 所属事实卡主键
     * @param cardId 所属事实卡业务标识
     * @param sourceId 资料源主键
     * @param sourceFileId 源文件主键
     * @param sourceChunkIds 源文件分块主键
     * @param articleIds 关联文章主键
     * @param cardType 事实卡类型
     * @param answerShape 答案形态
     * @param structure 来源结构
     * @param itemIndex 条目序号
     * @param keyPath 完整路径
     * @param parentPath 父级路径
     * @param terminalKey 末级字段
     * @param pathSegmentsJson 路径片段 JSON
     * @param fieldLabel 字段展示名
     * @param fieldAliasesJson 字段别名 JSON
     * @param fieldDescription 字段上下文描述
     * @param displayText 展示文本
     * @param valueText 原始值
     * @param normalizedValue 归一化值
     * @param valueType 值形态
     * @param sourceRefsJson 来源回指 JSON
     * @param ftsText 检索文本
     * @param metadataJson 元数据 JSON
     * @param reviewStatus 审查状态
     * @param confidence 置信度
     * @param contentHash 内容哈希
     */
    public FactCardTerminalUnitRecord(
            String unitId,
            String terminalUnitIdentity,
            Long factCardId,
            String cardId,
            Long sourceId,
            Long sourceFileId,
            List<Long> sourceChunkIds,
            List<Long> articleIds,
            FactCardType cardType,
            AnswerShape answerShape,
            String structure,
            int itemIndex,
            String keyPath,
            String parentPath,
            String terminalKey,
            String pathSegmentsJson,
            String fieldLabel,
            String fieldAliasesJson,
            String fieldDescription,
            String displayText,
            String valueText,
            String normalizedValue,
            String valueType,
            String sourceRefsJson,
            String ftsText,
            String metadataJson,
            FactCardReviewStatus reviewStatus,
            double confidence,
            String contentHash
    ) {
        this(
                null,
                unitId,
                terminalUnitIdentity,
                factCardId,
                cardId,
                sourceId,
                sourceFileId,
                sourceChunkIds,
                articleIds,
                cardType,
                answerShape,
                structure,
                itemIndex,
                keyPath,
                parentPath,
                terminalKey,
                pathSegmentsJson,
                fieldLabel,
                fieldAliasesJson,
                fieldDescription,
                displayText,
                valueText,
                normalizedValue,
                valueType,
                sourceRefsJson,
                ftsText,
                metadataJson,
                reviewStatus,
                confidence,
                contentHash,
                null,
                null
        );
    }

    /**
     * 创建终端字段证据单元记录。
     *
     * @param id 主键
     * @param unitId 稳定业务标识
     * @param terminalUnitIdentity 检索融合身份
     * @param factCardId 所属事实卡主键
     * @param cardId 所属事实卡业务标识
     * @param sourceId 资料源主键
     * @param sourceFileId 源文件主键
     * @param sourceChunkIds 源文件分块主键
     * @param articleIds 关联文章主键
     * @param cardType 事实卡类型
     * @param answerShape 答案形态
     * @param structure 来源结构
     * @param itemIndex 条目序号
     * @param keyPath 完整路径
     * @param parentPath 父级路径
     * @param terminalKey 末级字段
     * @param pathSegmentsJson 路径片段 JSON
     * @param fieldLabel 字段展示名
     * @param fieldAliasesJson 字段别名 JSON
     * @param fieldDescription 字段上下文描述
     * @param displayText 展示文本
     * @param valueText 原始值
     * @param normalizedValue 归一化值
     * @param valueType 值形态
     * @param sourceRefsJson 来源回指 JSON
     * @param ftsText 检索文本
     * @param metadataJson 元数据 JSON
     * @param reviewStatus 审查状态
     * @param confidence 置信度
     * @param contentHash 内容哈希
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public FactCardTerminalUnitRecord(
            Long id,
            String unitId,
            String terminalUnitIdentity,
            Long factCardId,
            String cardId,
            Long sourceId,
            Long sourceFileId,
            List<Long> sourceChunkIds,
            List<Long> articleIds,
            FactCardType cardType,
            AnswerShape answerShape,
            String structure,
            int itemIndex,
            String keyPath,
            String parentPath,
            String terminalKey,
            String pathSegmentsJson,
            String fieldLabel,
            String fieldAliasesJson,
            String fieldDescription,
            String displayText,
            String valueText,
            String normalizedValue,
            String valueType,
            String sourceRefsJson,
            String ftsText,
            String metadataJson,
            FactCardReviewStatus reviewStatus,
            double confidence,
            String contentHash,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this.id = id;
        this.unitId = unitId;
        this.terminalUnitIdentity = terminalUnitIdentity;
        this.factCardId = factCardId;
        this.cardId = cardId;
        this.sourceId = sourceId;
        this.sourceFileId = sourceFileId;
        this.sourceChunkIds = sourceChunkIds == null ? List.of() : List.copyOf(sourceChunkIds);
        this.articleIds = articleIds == null ? List.of() : List.copyOf(articleIds);
        this.cardType = cardType;
        this.answerShape = answerShape;
        this.structure = structure;
        this.itemIndex = itemIndex;
        this.keyPath = keyPath;
        this.parentPath = parentPath;
        this.terminalKey = terminalKey;
        this.pathSegmentsJson = pathSegmentsJson;
        this.fieldLabel = fieldLabel;
        this.fieldAliasesJson = fieldAliasesJson;
        this.fieldDescription = fieldDescription;
        this.displayText = displayText;
        this.valueText = valueText;
        this.normalizedValue = normalizedValue;
        this.valueType = valueType;
        this.sourceRefsJson = sourceRefsJson;
        this.ftsText = ftsText;
        this.metadataJson = metadataJson;
        this.reviewStatus = reviewStatus;
        this.confidence = confidence;
        this.contentHash = contentHash;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 创建仅替换 fieldAliasesJson 和 ftsText 的新记录，其余字段完全透传。
     *
     * @param newFieldAliasesJson 新字段别名 JSON
     * @param newFtsText 新检索文本
     * @return 新记录
     */
    public FactCardTerminalUnitRecord withFieldAliasesAndFtsText(
            String newFieldAliasesJson,
            String newFtsText
    ) {
        return withFieldAliasesFtsTextAndMetadata(newFieldAliasesJson, newFtsText, this.metadataJson);
    }

    /**
     * 创建替换 fieldAliasesJson、ftsText 和 metadataJson 的新记录，其余字段完全透传。
     *
     * @param newFieldAliasesJson 新字段别名 JSON
     * @param newFtsText 新检索文本
     * @param newMetadataJson 新 metadata JSON
     * @return 新记录
     */
    public FactCardTerminalUnitRecord withFieldAliasesFtsTextAndMetadata(
            String newFieldAliasesJson,
            String newFtsText,
            String newMetadataJson
    ) {
        return new FactCardTerminalUnitRecord(
                this.id,
                this.unitId,
                this.terminalUnitIdentity,
                this.factCardId,
                this.cardId,
                this.sourceId,
                this.sourceFileId,
                this.sourceChunkIds,
                this.articleIds,
                this.cardType,
                this.answerShape,
                this.structure,
                this.itemIndex,
                this.keyPath,
                this.parentPath,
                this.terminalKey,
                this.pathSegmentsJson,
                this.fieldLabel,
                newFieldAliasesJson,
                this.fieldDescription,
                this.displayText,
                this.valueText,
                this.normalizedValue,
                this.valueType,
                this.sourceRefsJson,
                newFtsText,
                newMetadataJson,
                this.reviewStatus,
                this.confidence,
                this.contentHash,
                this.createdAt,
                this.updatedAt
        );
    }

    /**
     * 获取主键。
     *
     * @return 主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 获取稳定业务标识。
     *
     * @return 稳定业务标识
     */
    public String getUnitId() {
        return unitId;
    }

    /**
     * 获取检索融合身份。
     *
     * @return 检索融合身份
     */
    public String getTerminalUnitIdentity() {
        return terminalUnitIdentity;
    }

    /**
     * 获取所属事实卡主键。
     *
     * @return 所属事实卡主键
     */
    public Long getFactCardId() {
        return factCardId;
    }

    /**
     * 获取所属事实卡业务标识。
     *
     * @return 所属事实卡业务标识
     */
    public String getCardId() {
        return cardId;
    }

    /**
     * 获取资料源主键。
     *
     * @return 资料源主键
     */
    public Long getSourceId() {
        return sourceId;
    }

    /**
     * 获取源文件主键。
     *
     * @return 源文件主键
     */
    public Long getSourceFileId() {
        return sourceFileId;
    }

    /**
     * 获取源文件分块主键。
     *
     * @return 源文件分块主键
     */
    public List<Long> getSourceChunkIds() {
        return sourceChunkIds;
    }

    /**
     * 获取关联文章主键。
     *
     * @return 关联文章主键
     */
    public List<Long> getArticleIds() {
        return articleIds;
    }

    /**
     * 获取事实卡类型。
     *
     * @return 事实卡类型
     */
    public FactCardType getCardType() {
        return cardType;
    }

    /**
     * 获取答案形态。
     *
     * @return 答案形态
     */
    public AnswerShape getAnswerShape() {
        return answerShape;
    }

    /**
     * 获取来源结构。
     *
     * @return 来源结构
     */
    public String getStructure() {
        return structure;
    }

    /**
     * 获取条目序号。
     *
     * @return 条目序号
     */
    public int getItemIndex() {
        return itemIndex;
    }

    /**
     * 获取完整路径。
     *
     * @return 完整路径
     */
    public String getKeyPath() {
        return keyPath;
    }

    /**
     * 获取父级路径。
     *
     * @return 父级路径
     */
    public String getParentPath() {
        return parentPath;
    }

    /**
     * 获取末级字段。
     *
     * @return 末级字段
     */
    public String getTerminalKey() {
        return terminalKey;
    }

    /**
     * 获取路径片段 JSON。
     *
     * @return 路径片段 JSON
     */
    public String getPathSegmentsJson() {
        return pathSegmentsJson;
    }

    /**
     * 获取字段展示名。
     *
     * @return 字段展示名
     */
    public String getFieldLabel() {
        return fieldLabel;
    }

    /**
     * 获取字段别名 JSON。
     *
     * @return 字段别名 JSON
     */
    public String getFieldAliasesJson() {
        return fieldAliasesJson;
    }

    /**
     * 获取字段上下文描述。
     *
     * @return 字段上下文描述
     */
    public String getFieldDescription() {
        return fieldDescription;
    }

    /**
     * 获取展示文本。
     *
     * @return 展示文本
     */
    public String getDisplayText() {
        return displayText;
    }

    /**
     * 获取原始值。
     *
     * @return 原始值
     */
    public String getValueText() {
        return valueText;
    }

    /**
     * 获取归一化值。
     *
     * @return 归一化值
     */
    public String getNormalizedValue() {
        return normalizedValue;
    }

    /**
     * 获取值形态。
     *
     * @return 值形态
     */
    public String getValueType() {
        return valueType;
    }

    /**
     * 获取来源回指 JSON。
     *
     * @return 来源回指 JSON
     */
    public String getSourceRefsJson() {
        return sourceRefsJson;
    }

    /**
     * 获取 FTS 检索文本。
     *
     * @return FTS 检索文本
     */
    public String getFtsText() {
        return ftsText;
    }

    /**
     * 获取 metadata JSON。
     *
     * @return metadata JSON
     */
    public String getMetadataJson() {
        return metadataJson;
    }

    /**
     * 获取审查状态。
     *
     * @return 审查状态
     */
    public FactCardReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    /**
     * 获取置信度。
     *
     * @return 置信度
     */
    public double getConfidence() {
        return confidence;
    }

    /**
     * 获取内容哈希。
     *
     * @return 内容哈希
     */
    public String getContentHash() {
        return contentHash;
    }

    /**
     * 获取创建时间。
     *
     * @return 创建时间
     */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 获取更新时间。
     *
     * @return 更新时间
     */
    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
