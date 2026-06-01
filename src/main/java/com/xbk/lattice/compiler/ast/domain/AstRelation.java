package com.xbk.lattice.compiler.ast.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * AST 图谱关系。
 *
 * <p>表示实体之间的结构化关系边，由 AST 抽取管道逐步构建的可变运行态对象。
 *
 * @author xiexu
 */
@Getter
@Setter
public class AstRelation {

    /** 源实体标识。关系起点的实体 ID。 */
    private String srcId;

    /** 关系边类型（如 extends / implements / calls / references）。 */
    private String edgeType;

    /** 目标实体标识。关系终点的实体 ID。 */
    private String dstId;

    /** 关系在源码中的定位引用（如文件路径:行号）。 */
    private String sourceRef;

    /** 关系在源码中的起始行号。 */
    private int sourceStartLine;

    /** 关系在源码中的结束行号。 */
    private int sourceEndLine;

    /** 抽取置信度（0.0-1.0）。 */
    private double confidence;

    /** 抽取器标识（如 ast_java / ast_python）。 */
    private String extractor;
}
