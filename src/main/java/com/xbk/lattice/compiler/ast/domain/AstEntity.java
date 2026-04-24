package com.xbk.lattice.compiler.ast.domain;

import lombok.Data;

/**
 * AST 图谱实体
 *
 * 职责：表示从源码中抽取出的稳定实体节点
 *
 * @author xiexu
 */
@Data
public class AstEntity {

    private String id;

    private String canonicalName;

    private String simpleName;

    private AstEntityType entityType;

    private String systemLabel;

    private Long sourceFileId;

    private String anchorRef;

    private String resolutionStatus;

    private String metadataJson;
}
