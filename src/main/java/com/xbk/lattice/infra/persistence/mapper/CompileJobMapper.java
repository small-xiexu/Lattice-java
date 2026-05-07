package com.xbk.lattice.infra.persistence.mapper;

import com.xbk.lattice.infra.persistence.CompileJobRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 编译作业 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 compile_jobs 表
 *
 * @author xiexu
 */
@Mapper
public interface CompileJobMapper {

    /**
     * 保存或更新编译作业。
     *
     * @param record 编译作业记录
     * @return 影响行数
     */
    int upsert(@Param("record") CompileJobRecord record);

    /**
     * 查询全部编译作业。
     *
     * @return 编译作业列表
     */
    List<CompileJobRecord> findAll();

    /**
     * 查询最近编译作业。
     *
     * @param limit 返回上限
     * @return 编译作业列表
     */
    List<CompileJobRecord> findRecent(@Param("limit") int limit);

    /**
     * 查询最近独立编译作业。
     *
     * @param limit 返回上限
     * @return 编译作业列表
     */
    List<CompileJobRecord> findRecentStandalone(@Param("limit") int limit);

    /**
     * 按资料源查询最近独立编译作业。
     *
     * @param sourceId 资料源主键
     * @param limit 返回上限
     * @return 编译作业列表
     */
    List<CompileJobRecord> findRecentStandaloneBySourceId(@Param("sourceId") Long sourceId, @Param("limit") int limit);

    /**
     * 按作业标识查询编译作业。
     *
     * @param jobId 作业标识
     * @return 编译作业
     */
    CompileJobRecord findByJobId(@Param("jobId") String jobId);

    /**
     * 查询最早排队中的作业。
     *
     * @return 编译作业
     */
    CompileJobRecord findNextQueued();

    /**
     * 将排队作业标记为运行中。
     *
     * @param jobId 作业标识
     * @param workerId worker 标识
     * @param startedAt 开始时间
     * @param runningExpiresAt 运行租约到期时间
     * @return 影响行数
     */
    int markRunning(
            @Param("jobId") String jobId,
            @Param("workerId") String workerId,
            @Param("startedAt") OffsetDateTime startedAt,
            @Param("runningExpiresAt") OffsetDateTime runningExpiresAt
    );

    /**
     * 刷新运行中心跳。
     *
     * @param jobId 作业标识
     * @param workerId worker 标识
     * @param heartbeatAt 心跳时间
     * @param runningExpiresAt 运行租约到期时间
     * @return 影响行数
     */
    int refreshHeartbeat(
            @Param("jobId") String jobId,
            @Param("workerId") String workerId,
            @Param("heartbeatAt") OffsetDateTime heartbeatAt,
            @Param("runningExpiresAt") OffsetDateTime runningExpiresAt
    );

    /**
     * 更新当前步骤。
     *
     * @param jobId 作业标识
     * @param workerId worker 标识
     * @param currentStep 当前步骤
     * @param progressMessage 进度提示
     * @param heartbeatAt 心跳时间
     * @param runningExpiresAt 运行租约到期时间
     * @return 影响行数
     */
    int updateCurrentStep(
            @Param("jobId") String jobId,
            @Param("workerId") String workerId,
            @Param("currentStep") String currentStep,
            @Param("progressMessage") String progressMessage,
            @Param("heartbeatAt") OffsetDateTime heartbeatAt,
            @Param("runningExpiresAt") OffsetDateTime runningExpiresAt
    );

    /**
     * 更新进度快照。
     *
     * @param jobId 作业标识
     * @param workerId worker 标识
     * @param currentStep 当前步骤
     * @param progressCurrent 当前进度
     * @param progressTotal 总进度
     * @param progressMessage 进度提示
     * @param heartbeatAt 心跳时间
     * @param runningExpiresAt 运行租约到期时间
     * @return 影响行数
     */
    int updateProgressSnapshot(
            @Param("jobId") String jobId,
            @Param("workerId") String workerId,
            @Param("currentStep") String currentStep,
            @Param("progressCurrent") int progressCurrent,
            @Param("progressTotal") int progressTotal,
            @Param("progressMessage") String progressMessage,
            @Param("heartbeatAt") OffsetDateTime heartbeatAt,
            @Param("runningExpiresAt") OffsetDateTime runningExpiresAt
    );

    /**
     * 将作业标记为成功。
     *
     * @param jobId 作业标识
     * @param persistedCount 持久化数量
     * @param finishedAt 完成时间
     * @return 影响行数
     */
    int markSucceeded(
            @Param("jobId") String jobId,
            @Param("persistedCount") int persistedCount,
            @Param("finishedAt") OffsetDateTime finishedAt
    );

    /**
     * 将作业标记为失败。
     *
     * @param jobId 作业标识
     * @param errorCode 错误码
     * @param errorMessage 错误信息
     * @param finishedAt 完成时间
     * @return 影响行数
     */
    int markFailed(
            @Param("jobId") String jobId,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("finishedAt") OffsetDateTime finishedAt
    );

    /**
     * 仅在作业仍运行中时标记失败。
     *
     * @param jobId 作业标识
     * @param errorCode 错误码
     * @param errorMessage 错误信息
     * @param finishedAt 完成时间
     * @return 影响行数
     */
    int markFailedIfRunning(
            @Param("jobId") String jobId,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("finishedAt") OffsetDateTime finishedAt
    );

    /**
     * 重试已失败作业。
     *
     * @param jobId 作业标识
     * @return 影响行数
     */
    int retry(@Param("jobId") String jobId);

    /**
     * 回收指定 worker 持有的运行中作业。
     *
     * @param workerId worker 标识
     * @return 影响行数
     */
    int requeueRunningJobsOwnedByWorker(@Param("workerId") String workerId);

    /**
     * 查询已过期的运行中作业标识。
     *
     * @param now 当前时间
     * @return 作业标识列表
     */
    List<String> findExpiredRunningJobIds(@Param("now") OffsetDateTime now);
}
