package com.xbk.lattice.compiler.ast.domain;

import lombok.Data;

/**
 * AST 图谱事实
 *
 * 职责：表示附着在实体上的结构化事实
 *
 * @author xiexu
 */
@Data
public class AstFact {

    private String entityId;

    private String predicate;

    private String value;

    private String sourceRef;

    private int sourceStartLine;

    private int sourceEndLine;

    private String evidenceExcerpt;

    private double confidence;

    private String extractor;
}
