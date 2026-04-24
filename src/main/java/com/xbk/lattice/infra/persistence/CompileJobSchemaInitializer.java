package com.xbk.lattice.infra.persistence;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 编译作业表结构初始化器
 *
 * 职责：在单基线迁移模型下补齐 compile_jobs 的兼容列，避免旧 schema 缺失运行态字段
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
     * 启动时补齐 compile_jobs 的兼容列与索引。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        addColumn("root_trace_id varchar(64)", "root_trace_id", "异步编译链路根追踪标识");
        addColumn("worker_id varchar(128)", "worker_id", "当前持有任务的 worker 标识");
        addColumn("last_heartbeat_at timestamptz", "last_heartbeat_at", "最近一次运行心跳时间");
        addColumn("running_expires_at timestamptz", "running_expires_at", "当前运行租约到期时间");
        addColumn("current_step varchar(64)", "current_step", "当前执行步骤");
        addColumn("progress_current integer not null default 0", "progress_current", "当前已完成子任务数量");
        addColumn("progress_total integer not null default 0", "progress_total", "当前总子任务数量");
        addColumn("progress_message text", "progress_message", "当前进度说明");
        addColumn("progress_updated_at timestamptz", "progress_updated_at", "最近一次进度更新时间");
        addColumn("error_code varchar(64)", "error_code", "机器可识别错误码");
        jdbcTemplate.execute(
                """
                        create index if not exists idx_compile_jobs_status_running_expires_at
                        on compile_jobs (status, running_expires_at, job_id)
                        """
        );
    }

    /**
     * 为 compile_jobs 补齐指定列并写入注释。
     *
     * @param definition 列定义
     * @param columnName 列名
     * @param comment 列注释
     */
    private void addColumn(String definition, String columnName, String comment) {
        jdbcTemplate.execute("alter table compile_jobs add column if not exists " + definition);
        jdbcTemplate.execute("comment on column compile_jobs." + columnName + " is '" + comment + "'");
    }
}
