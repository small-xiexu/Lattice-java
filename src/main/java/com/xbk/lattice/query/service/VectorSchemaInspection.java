package com.xbk.lattice.query.service;

/**
 * 向量 schema 检查结果
 *
 * 职责：承载 profile 维度、schema 维度与 ANN 索引状态
 *
 * @author xiexu
 */
public class VectorSchemaInspection {

    private final Integer profileDimensions;

    private final Integer schemaDimensions;

    private final boolean dimensionsConsistent;

    private final boolean annIndexReady;

    private final String annIndexType;

    /**
     * 创建向量 schema 检查结果。
     *
     * @param profileDimensions profile 维度
     * @param schemaDimensions schema 维度
     * @param dimensionsConsistent 维度是否一致
     * @param annIndexReady ANN 索引是否就绪
     * @param annIndexType ANN 索引类型
     */
    public VectorSchemaInspection(
            Integer profileDimensions,
            Integer schemaDimensions,
            boolean dimensionsConsistent,
            boolean annIndexReady,
            String annIndexType
    ) {
        this.profileDimensions = profileDimensions;
        this.schemaDimensions = schemaDimensions;
        this.dimensionsConsistent = dimensionsConsistent;
        this.annIndexReady = annIndexReady;
        this.annIndexType = annIndexType;
    }

    /**
     * 返回 profile 维度。
     *
     * @return profile 维度
     */
    public Integer getProfileDimensions() {
        return profileDimensions;
    }

    /**
     * 返回 schema 维度。
     *
     * @return schema 维度
     */
    public Integer getSchemaDimensions() {
        return schemaDimensions;
    }

    /**
     * 返回维度是否一致。
     *
     * @return 维度是否一致
     */
    public boolean isDimensionsConsistent() {
        return dimensionsConsistent;
    }

    /**
     * 返回 ANN 索引是否就绪。
     *
     * @return ANN 索引是否就绪
     */
    public boolean isAnnIndexReady() {
        return annIndexReady;
    }

    /**
     * 返回 ANN 索引类型。
     *
     * @return ANN 索引类型
     */
    public String getAnnIndexType() {
        return annIndexType;
    }
}
