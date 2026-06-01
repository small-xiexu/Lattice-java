package com.xbk.lattice.compiler.ast.domain;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * AST 抽取结果。
 *
 * <p>聚合单次源码抽取出的实体、事实、关系与告警的可变累加器。
 * 通过 {@code addXxx()} 逐步构建，{@code merge()} 合并多份结果。
 * 仅对 entities/facts/relations 暴露 getter；warnings 走专用 {@code warnings()} 方法。
 *
 * @author xiexu
 */
public class AstExtractionResult {

    /** 抽取出的实体列表。 */
    @Getter
    private final List<AstEntity> entities = new ArrayList<AstEntity>();

    /** 抽取出的结构化事实列表。 */
    @Getter
    private final List<AstFact> facts = new ArrayList<AstFact>();

    /** 抽取出的关系列表。 */
    @Getter
    private final List<AstRelation> relations = new ArrayList<AstRelation>();

    /** 抽取过程中的告警列表。通过 {@code warnings()} 访问。 */
    private final List<String> warnings = new ArrayList<String>();

    /**
     * 返回空结果。
     */
    public static AstExtractionResult empty() {
        return new AstExtractionResult();
    }

    public void addEntity(AstEntity entity) {
        if (entity != null) {
            entities.add(entity);
        }
    }

    public void addFact(AstFact fact) {
        if (fact != null) {
            facts.add(fact);
        }
    }

    public void addRelation(AstRelation relation) {
        if (relation != null) {
            relations.add(relation);
        }
    }

    public void addWarning(String warning) {
        if (warning != null && !warning.isBlank()) {
            warnings.add(warning);
        }
    }

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

    public boolean isEmpty() {
        return entities.isEmpty() && facts.isEmpty() && relations.isEmpty();
    }

    /**
     * 返回告警列表。
     */
    public List<String> warnings() {
        return warnings;
    }
}
