package com.xbk.lattice.compiler.ast.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * AST 图谱实体。
 *
 * <p>表示从源码中抽取出的稳定实体节点（类/接口/枚举/方法），
 * 由 AST 抽取管道逐步构建的可变运行态对象。
 *
 * @author xiexu
 */
@Getter
@Setter
public class AstEntity {

    /** 实体标识（唯一，如 fully.qualified.ClassName）。 */
    private String id;

    /** 规范全名（含包路径）。 */
    private String canonicalName;

    /** 简短名称（不含包路径）。 */
    private String simpleName;

    /** 实体类型（CLASS / INTERFACE / ENUM / METHOD）。 */
    private AstEntityType entityType;

    /** 系统标签（如 java / python / kotlin）。 */
    private String systemLabel;

    /** 源文件主键。 */
    private Long sourceFileId;

    /** 实体在源文件中的锚点引用（如 line:start-end）。 */
    private String anchorRef;

    /** 解析状态（如 RESOLVED / UNRESOLVED / PARTIAL）。 */
    private String resolutionStatus;

    /** 扩展元数据 JSON。 */
    private String metadataJson;
}
