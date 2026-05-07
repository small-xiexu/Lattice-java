package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.CompileReviewConfigMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Compile 审查配置 JDBC 仓储
 *
 * 职责：提供 compile_review_settings 表的读取与保存能力
 *
 * @author xiexu
 */
@Repository
public class CompileReviewConfigJdbcRepository {

    private final CompileReviewConfigMapper compileReviewConfigMapper;

    /**
     * 创建 Compile 审查配置 JDBC 仓储。
     *
     * @param compileReviewConfigMapper Compile 审查配置 Mapper
     */
    public CompileReviewConfigJdbcRepository(CompileReviewConfigMapper compileReviewConfigMapper) {
        this.compileReviewConfigMapper = compileReviewConfigMapper;
    }

    /**
     * 查询默认配置。
     *
     * @return 默认配置
     */
    public Optional<CompileReviewConfigRecord> findDefault() {
        return Optional.ofNullable(compileReviewConfigMapper.findDefault());
    }

    /**
     * 保存默认配置。
     *
     * @param record 配置记录
     * @return 保存后的配置记录
     */
    public CompileReviewConfigRecord saveDefault(CompileReviewConfigRecord record) {
        compileReviewConfigMapper.saveDefault(record);
        return findDefault().orElse(record);
    }
}
