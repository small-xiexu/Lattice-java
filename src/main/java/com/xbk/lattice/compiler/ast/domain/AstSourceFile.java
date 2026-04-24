package com.xbk.lattice.compiler.ast.domain;

import lombok.Data;

/**
 * AST 源文件
 *
 * 职责：表示进入 AST 抽取链的最小源文件视图
 *
 * @author xiexu
 */
@Data
public class AstSourceFile {

    private Long sourceFileId;

    private String relativePath;

    private String content;

    private String systemLabel;
}
