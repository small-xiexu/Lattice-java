package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.CompileReviewConfigRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Compile 审查配置 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 compile_review_settings 表
 *
 * @author xiexu
 */
@Mapper
public interface CompileReviewConfigMapper {

    /**
     * 查询默认配置。
     *
     * @return 默认配置
     */
    CompileReviewConfigRecord findDefault();

    /**
     * 保存默认配置。
     *
     * @param record 配置记录
     * @return 影响行数
     */
    int saveDefault(@Param("record") CompileReviewConfigRecord record);
}
