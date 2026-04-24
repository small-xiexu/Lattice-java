package com.xbk.lattice.compiler.ast.domain;

import lombok.Data;

/**
 * AST 图谱关系
 *
 * 职责：表示实体之间的结构化关系边
 *
 * @author xiexu
 */
@Data
public class AstRelation {

    private String srcId;

    private String edgeType;

    private String dstId;

    private String sourceRef;

    private int sourceStartLine;

    private int sourceEndLine;

    private double confidence;

    private String extractor;
}
