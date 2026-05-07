package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.QueryVectorConfigRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Query 向量配置 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 query_vector_settings 表
 *
 * @author xiexu
 */
@Mapper
public interface QueryVectorConfigMapper {

    /**
     * 查询指定作用域的向量配置。
     *
     * @param configScope 配置作用域
     * @return 向量配置
     */
    QueryVectorConfigRecord findByScope(@Param("configScope") String configScope);

    /**
     * 插入向量配置。
     *
     * @param record 向量配置记录
     * @return 影响行数
     */
    int insert(@Param("record") QueryVectorConfigRecord record);

    /**
     * 更新向量配置。
     *
     * @param record 向量配置记录
     * @return 影响行数
     */
    int update(@Param("record") QueryVectorConfigRecord record);
}
