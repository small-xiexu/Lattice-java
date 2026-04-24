package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.CompileJobProperties;
import com.xbk.lattice.infra.persistence.CompileJobRecord;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CompileJobDerivedStatusResolver 测试
 *
 * 职责：验证运行态主状态与派生展示状态的分层语义
 *
 * @author xiexu
 */
class CompileJobDerivedStatusResolverTests {

    /**
     * 验证非运行态任务直接沿用主状态。
     */
    @Test
    void shouldReturnPrimaryStatusWhenJobIsNotRunning() {
        CompileJobDerivedStatusResolver resolver = new CompileJobDerivedStatusResolver(buildProperties(180));
        CompileJobRecord compileJobRecord = buildRecord(
                "QUEUED",
                null,
                null,
                null
        );

        assertThat(resolver.resolve(compileJobRecord)).isEqualTo("QUEUED");
    }

    /**
     * 验证运行租约过期时派生为 STALLED。
     */
    @Test
    void shouldResolveStalledWhenLeaseExpired() {
        OffsetDateTime now = OffsetDateTime.now();
        CompileJobDerivedStatusResolver resolver = new CompileJobDerivedStatusResolver(buildProperties(180));
        CompileJobRecord compileJobRecord = buildRecord(
                "RUNNING",
                now.minusSeconds(10),
                now.minusSeconds(1),
                now.minusSeconds(10)
        );

        assertThat(resolver.resolve(compileJobRecord)).isEqualTo(CompileJobDerivedStatuses.STALLED);
    }

    /**
     * 验证长时间无进度更新时派生为 STALLED。
     */
    @Test
    void shouldResolveStalledWhenProgressNotUpdatedForLongTime() {
        OffsetDateTime now = OffsetDateTime.now();
        CompileJobDerivedStatusResolver resolver = new CompileJobDerivedStatusResolver(buildProperties(60));
        CompileJobRecord compileJobRecord = buildRecord(
                "RUNNING",
                now.minusSeconds(10),
                now.plusSeconds(60),
                now.minusSeconds(120)
        );

        assertThat(resolver.resolve(compileJobRecord)).isEqualTo(CompileJobDerivedStatuses.STALLED);
    }

    /**
     * 验证运行态健康时继续返回 RUNNING。
     */
    @Test
    void shouldKeepRunningWhenLeaseAndProgressAreHealthy() {
        OffsetDateTime now = OffsetDateTime.now();
        CompileJobDerivedStatusResolver resolver = new CompileJobDerivedStatusResolver(buildProperties(180));
        CompileJobRecord compileJobRecord = buildRecord(
                "RUNNING",
                now.minusSeconds(10),
                now.plusSeconds(60),
                now.minusSeconds(10)
        );

        assertThat(resolver.resolve(compileJobRecord)).isEqualTo("RUNNING");
    }

    /**
     * 构造编译作业配置。
     *
     * @param stalledThresholdSeconds 卡住阈值秒数
     * @return 编译作业配置
     */
    private CompileJobProperties buildProperties(long stalledThresholdSeconds) {
        CompileJobProperties compileJobProperties = new CompileJobProperties();
        compileJobProperties.setStalledThresholdSeconds(stalledThresholdSeconds);
        return compileJobProperties;
    }

    /**
     * 构造最小运行态记录。
     *
     * @param status 主状态
     * @param lastHeartbeatAt 最近心跳时间
     * @param runningExpiresAt 运行租约到期时间
     * @param progressUpdatedAt 最近进度更新时间
     * @return 编译作业记录
     */
    private CompileJobRecord buildRecord(
            String status,
            OffsetDateTime lastHeartbeatAt,
            OffsetDateTime runningExpiresAt,
            OffsetDateTime progressUpdatedAt
    ) {
        OffsetDateTime requestedAt = OffsetDateTime.now().minusMinutes(5);
        return new CompileJobRecord(
                "job-derived-status",
                "/tmp/source-dir",
                1L,
                2L,
                "trace-root",
                false,
                "state_graph",
                status,
                "worker-x",
                lastHeartbeatAt,
                runningExpiresAt,
                "compile_new_articles",
                1,
                3,
                "正在生成第 1/3 篇文章",
                progressUpdatedAt,
                null,
                0,
                null,
                1,
                requestedAt,
                requestedAt.plusSeconds(10),
                null
        );
    }
}
