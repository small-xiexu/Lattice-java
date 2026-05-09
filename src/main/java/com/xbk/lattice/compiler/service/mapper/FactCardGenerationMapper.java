package com.xbk.lattice.compiler.service.mapper;

import com.xbk.lattice.compiler.service.FactCardSourceChunkView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 事实卡生成 MyBatis Mapper
 *
 * 职责：读取事实卡生成需要的 source chunk 视图
 *
 * @author xiexu
 */
@Mapper
public interface FactCardGenerationMapper {

    /**
     * 按源文件主键查询 chunk 视图。
     *
     * @param sourceFileId 源文件主键
     * @return chunk 视图列表
     */
    List<FactCardSourceChunkView> findChunksBySourceFileId(@Param("sourceFileId") Long sourceFileId);
}
