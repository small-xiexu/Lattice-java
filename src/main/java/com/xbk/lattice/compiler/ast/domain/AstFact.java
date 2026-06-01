package com.xbk.lattice.compiler.ast.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * AST 图谱事实。
 *
 * <p>表示附着在实体上的结构化事实（entity-predicate-value 三元组），
 * 由 AST 抽取管道逐步构建的可变运行态对象。
 *
 * @author xiexu
 */
@Getter
@Setter
public class AstFact {

    /** 关联的实体标识。事实所属的实体 ID。 */
    private String entityId;

    /** 谓词（如 has_method / has_type / has_annotation）。 */
    private String predicate;

    /** 事实值（如方法名、类型名、注解值）。 */
    private String value;

    /** 事实在源码中的定位引用。 */
    private String sourceRef;

    /** 事实在源码中的起始行号。 */
    private int sourceStartLine;

    /** 事实在源码中的结束行号。 */
    private int sourceEndLine;

    /** 证据原文摘录。从源码中提取的支持该事实的文本片段。 */
    private String evidenceExcerpt;

    /** 抽取置信度（0.0-1.0）。 */
    private double confidence;

    /** 抽取器标识。 */
    private String extractor;
}
