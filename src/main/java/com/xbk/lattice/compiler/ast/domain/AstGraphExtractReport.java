package com.xbk.lattice.compiler.ast.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * AST 图谱抽取报告。
 *
 * <p>汇总编译期 AST 抽取与落库结果——实体、事实、关系的 upsert 统计和告警列表。
 * 由 AST 抽取管道逐步构建的可变运行态对象。
 *
 * @author xiexu
 */
@Getter
@Setter
public class AstGraphExtractReport {

    /** 实体 upsert 数量。 */
    private int entityUpsertCount;

    /** 事实 upsert 数量。 */
    private int factUpsertCount;

    /** 关系 upsert 数量。 */
    private int relationUpsertCount;

    /** 抽取过程中的告警列表。 */
    private List<String> warnings = new ArrayList<String>();
}
