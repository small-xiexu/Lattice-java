package com.xbk.lattice.infra.persistence;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 编译作业表结构初始化器
 *
 * 职责：在单基线迁移模型下补齐 compile_jobs 的兼容列，避免旧 schema 缺失新字段
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class CompileJobSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建编译作业表结构初始化器。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public CompileJobSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 启动时补齐 compile_jobs 的 root_trace_id 列。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("alter table compile_jobs add column if not exists root_trace_id varchar(64)");
        jdbcTemplate.execute("comment on column compile_jobs.root_trace_id is '异步编译链路根追踪标识'");
    }
}
