package com.xbk.lattice.compiler.ast.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * AST 抽取结果
 *
 * 职责：聚合单次源码抽取出的实体、事实、关系与告警
 *
 * @author xiexu
 */
public class AstExtractionResult {

    private final List<AstEntity> entities = new ArrayList<AstEntity>();

    private final List<AstFact> facts = new ArrayList<AstFact>();

    private final List<AstRelation> relations = new ArrayList<AstRelation>();

    private final List<String> warnings = new ArrayList<String>();

    /**
     * 返回空结果。
     *
     * @return 空结果
     */
    public static AstExtractionResult empty() {
        return new AstExtractionResult();
    }

    /**
     * 追加实体。
     *
     * @param entity 实体
     */
    public void addEntity(AstEntity entity) {
        if (entity != null) {
            entities.add(entity);
        }
    }

    /**
     * 追加事实。
     *
     * @param fact 事实
     */
    public void addFact(AstFact fact) {
        if (fact != null) {
            facts.add(fact);
        }
    }

    /**
     * 追加关系。
     *
     * @param relation 关系
     */
    public void addRelation(AstRelation relation) {
        if (relation != null) {
            relations.add(relation);
        }
    }

    /**
     * 追加告警。
     *
     * @param warning 告警
     */
    public void addWarning(String warning) {
        if (warning != null && !warning.isBlank()) {
            warnings.add(warning);
        }
    }

    /**
     * 合并另一份抽取结果。
     *
     * @param other 另一份抽取结果
     * @return 当前结果
     */
    public AstExtractionResult merge(AstExtractionResult other) {
        if (other == null) {
            return this;
        }
        entities.addAll(other.getEntities());
        facts.addAll(other.getFacts());
        relations.addAll(other.getRelations());
        warnings.addAll(other.warnings());
        return this;
    }

    /**
     * 返回当前结果是否为空。
     *
     * @return 是否为空
     */
    public boolean isEmpty() {
        return entities.isEmpty() && facts.isEmpty() && relations.isEmpty();
    }

    /**
     * 返回实体列表。
     *
     * @return 实体列表
     */
    public List<AstEntity> getEntities() {
        return entities;
    }

    /**
     * 返回事实列表。
     *
     * @return 事实列表
     */
    public List<AstFact> getFacts() {
        return facts;
    }

    /**
     * 返回关系列表。
     *
     * @return 关系列表
     */
    public List<AstRelation> getRelations() {
        return relations;
    }

    /**
     * 返回告警列表。
     *
     * @return 告警列表
     */
    public List<String> warnings() {
        return warnings;
    }
}
