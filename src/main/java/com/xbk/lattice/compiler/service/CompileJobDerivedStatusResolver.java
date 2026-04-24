package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.CompileJobProperties;
import com.xbk.lattice.infra.persistence.CompileJobRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * 编译作业派生状态解析器
 *
 * 职责：根据运行租约、心跳与进度更新时间推导页面展示状态
 *
 * @author xiexu
 */
@Component
@Profile("jdbc")
public class CompileJobDerivedStatusResolver {

    private final CompileJobProperties compileJobProperties;

    /**
     * 创建编译作业派生状态解析器。
     *
     * @param compileJobProperties 编译作业配置
     */
    public CompileJobDerivedStatusResolver(CompileJobProperties compileJobProperties) {
        this.compileJobProperties = compileJobProperties;
    }

    /**
     * 解析编译作业的派生展示状态。
     *
     * @param compileJobRecord 编译作业记录
     * @return 派生展示状态
     */
    public String resolve(CompileJobRecord compileJobRecord) {
        if (compileJobRecord == null || compileJobRecord.getStatus() == null) {
            return null;
        }
        if (!CompileJobStatuses.RUNNING.equalsIgnoreCase(compileJobRecord.getStatus())) {
            return compileJobRecord.getStatus();
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (isLeaseExpired(compileJobRecord, now)
                || isHeartbeatStalled(compileJobRecord, now)
                || isProgressStalled(compileJobRecord, now)) {
            return CompileJobDerivedStatuses.STALLED;
        }
        return CompileJobStatuses.RUNNING;
    }

    /**
     * 判断运行租约是否已经过期。
     *
     * @param compileJobRecord 编译作业记录
     * @param now 当前时间
     * @return 是否已过期
     */
    private boolean isLeaseExpired(CompileJobRecord compileJobRecord, OffsetDateTime now) {
        return compileJobRecord.getRunningExpiresAt() != null
                && compileJobRecord.getRunningExpiresAt().isBefore(now);
    }

    /**
     * 判断最近心跳是否超过卡住阈值。
     *
     * @param compileJobRecord 编译作业记录
     * @param now 当前时间
     * @return 是否疑似卡住
     */
    private boolean isHeartbeatStalled(CompileJobRecord compileJobRecord, OffsetDateTime now) {
        return isBeforeStalledThreshold(compileJobRecord.getLastHeartbeatAt(), now);
    }

    /**
     * 判断最近进度更新时间是否超过卡住阈值。
     *
     * @param compileJobRecord 编译作业记录
     * @param now 当前时间
     * @return 是否疑似卡住
     */
    private boolean isProgressStalled(CompileJobRecord compileJobRecord, OffsetDateTime now) {
        return isBeforeStalledThreshold(compileJobRecord.getProgressUpdatedAt(), now);
    }

    /**
     * 判断给定时间是否早于卡住阈值。
     *
     * @param timestamp 待判断时间
     * @param now 当前时间
     * @return 是否早于阈值
     */
    private boolean isBeforeStalledThreshold(OffsetDateTime timestamp, OffsetDateTime now) {
        if (timestamp == null) {
            return false;
        }
        OffsetDateTime threshold = now.minusSeconds(compileJobProperties.getStalledThresholdSeconds());
        return timestamp.isBefore(threshold);
    }
}
