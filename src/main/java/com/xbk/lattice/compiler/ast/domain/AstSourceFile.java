package com.xbk.lattice.compiler.ast.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * AST 源文件。
 *
 * <p>表示进入 AST 抽取链的最小源文件视图——含标识、路径、内容和语言标签。
 * 由 AST 抽取管道逐步构建的可变运行态对象。
 *
 * @author xiexu
 */
@Getter
@Setter
public class AstSourceFile {

    /** 源文件主键。 */
    private Long sourceFileId;

    /** 文件相对路径。 */
    private String relativePath;

    /**
     * 完整源文件内容。
     *
     * <p>可能很大（完整源代码），禁止参与 {@code toString()}。</p>
     */
    private String content;

    /** 系统/语言标签（如 java / python）。 */
    private String systemLabel;
}
