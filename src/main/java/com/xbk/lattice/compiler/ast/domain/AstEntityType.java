package com.xbk.lattice.compiler.ast.domain;

/**
 * AST 实体类型。
 *
 * <p>标识图谱实体所属的代码语义类别，用于 AST 图遍历时的类型判断和过滤。
 *
 * @author xiexu
 */
public enum AstEntityType {
    /** 类定义。 */
    CLASS,
    /** 接口定义。 */
    INTERFACE,
    /** 枚举定义。 */
    ENUM,
    /** 方法定义（含构造函数）。 */
    METHOD
}
