package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.service.mapper.SynthesisArtifactMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 合成产物 JDBC 存储
 *
 * 职责：将 index/timeline/tradeoffs/gaps 写入 synthesis_artifacts 表
 *
 * @author xiexu
 */
@Repository
public class SynthesisArtifactJdbcStore implements SynthesisArtifactStore {

    private final SynthesisArtifactMapper synthesisArtifactMapper;

    /**
     * 创建合成产物存储。
     *
     * @param synthesisArtifactMapper 合成产物 Mapper
     */
    @Autowired
    public SynthesisArtifactJdbcStore(SynthesisArtifactMapper synthesisArtifactMapper) {
        this.synthesisArtifactMapper = synthesisArtifactMapper;
    }

    @Override
    public void save(SynthesisArtifactRecord synthesisArtifactRecord) {
        if (synthesisArtifactMapper == null) {
            return;
        }
        synthesisArtifactMapper.upsert(synthesisArtifactRecord);
    }

    /**
     * 查询全部合成产物。
     *
     * @return 合成产物列表
     */
    public List<SynthesisArtifactRecord> findAll() {
        if (synthesisArtifactMapper == null) {
            return List.of();
        }
        return synthesisArtifactMapper.findAll();
    }

    /**
     * 清空全部合成产物。
     */
    public void deleteAll() {
        if (synthesisArtifactMapper != null) {
            synthesisArtifactMapper.truncateAll();
        }
    }
}
