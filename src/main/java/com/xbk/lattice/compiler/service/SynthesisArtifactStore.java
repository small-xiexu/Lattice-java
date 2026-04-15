package com.xbk.lattice.compiler.service;

/**
 * 合成产物存储抽象
 *
 * 职责：隔离 index/timeline/tradeoffs/gaps 的持久化细节
 *
 * @author xiexu
 */
public interface SynthesisArtifactStore {

    /**
     * 保存或更新合成产物。
     *
     * @param synthesisArtifactRecord 合成产物记录
     */
    void save(SynthesisArtifactRecord synthesisArtifactRecord);
}
