package com.xbk.lattice.llm.infra.mapper;

import com.xbk.lattice.llm.domain.LlmModelProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 模型配置 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 llm_model_profiles 表
 *
 * @author xiexu
 */
@Mapper
public interface LlmModelProfileMapper {

    /**
     * 插入模型配置。
     *
     * @param modelProfile 模型配置
     * @return 主键
     */
    Long insert(@Param("modelProfile") LlmModelProfile modelProfile);

    /**
     * 更新模型配置。
     *
     * @param modelProfile 模型配置
     * @return 影响行数
     */
    int update(@Param("modelProfile") LlmModelProfile modelProfile);

    /**
     * 查询全部模型配置。
     *
     * @return 模型配置列表
     */
    List<LlmModelProfile> findAll();

    /**
     * 按主键查询模型配置。
     *
     * @param id 主键
     * @return 模型配置
     */
    LlmModelProfile findById(@Param("id") Long id);

    /**
     * 按主键查询启用中的模型配置。
     *
     * @param id 主键
     * @return 启用中的模型配置
     */
    LlmModelProfile findEnabledById(@Param("id") Long id);

    /**
     * 删除模型配置。
     *
     * @param id 主键
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);
}
