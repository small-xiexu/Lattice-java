package com.xbk.lattice.compiler.service.mapper;

import com.xbk.lattice.compiler.service.SynthesisArtifactRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 合成产物 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 synthesis_artifacts 表
 *
 * @author xiexu
 */
@Mapper
public interface SynthesisArtifactMapper {

    /**
     * 保存或更新合成产物。
     *
     * @param record 合成产物记录
     * @return 影响行数
     */
    int upsert(@Param("record") SynthesisArtifactRecord record);

    /**
     * 查询全部合成产物。
     *
     * @return 合成产物列表
     */
    List<SynthesisArtifactRecord> findAll();

    /**
     * 清空全部合成产物。
     */
    void truncateAll();
}
