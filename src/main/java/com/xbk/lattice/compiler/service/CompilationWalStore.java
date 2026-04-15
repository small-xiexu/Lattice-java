package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.model.MergedConcept;

import java.util.List;

/**
 * 编译 WAL 存储
 *
 * 职责：暂存待提交概念，并记录提交完成状态
 *
 * @author xiexu
 */
public interface CompilationWalStore {

    /**
     * 暂存待提交概念。
     *
     * @param jobId 作业标识
     * @param mergedConcepts 合并概念列表
     */
    void stage(String jobId, List<MergedConcept> mergedConcepts);

    /**
     * 读取尚未提交的概念。
     *
     * @param jobId 作业标识
     * @return 尚未提交的概念
     */
    List<MergedConcept> loadPendingConcepts(String jobId);

    /**
     * 标记概念已提交。
     *
     * @param jobId 作业标识
     * @param conceptId 概念标识
     */
    void markCommitted(String jobId, String conceptId);
}
