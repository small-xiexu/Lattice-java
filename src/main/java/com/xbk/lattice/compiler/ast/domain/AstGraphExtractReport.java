package com.xbk.lattice.compiler.ast.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * AST 图谱抽取报告
 *
 * 职责：汇总编译期 AST 抽取与落库结果
 *
 * @author xiexu
 */
@Data
public class AstGraphExtractReport {

    private int entityUpsertCount;

    private int factUpsertCount;

    private int relationUpsertCount;

    private List<String> warnings = new ArrayList<String>();
}
