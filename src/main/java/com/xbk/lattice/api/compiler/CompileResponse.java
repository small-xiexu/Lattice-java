package com.xbk.lattice.api.compiler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * 编译响应。
 *
 * <p>表示最小编译接口的返回结果，包含本次编译落盘的文章数量和作业标识。
 * 调用方通过 persistedCount 确认编译产出规模，通过 jobId 追踪编译任务状态。
 *
 * @author xiexu
 */
@Getter
public class CompileResponse {

    /**
     * 落盘数量。
     *
     * <p>本次编译通过 review gate 并成功写入正式表的文章数量。只有 review_status=passed
     * 且 lifecycle=ACTIVE 的文章才会被计入。调用方用这个值判断编译是否产出了有效内容。</p>
     */
    private final int persistedCount;

    /**
     * 编译作业标识。
     *
     * <p>系统为本次编译任务分配的唯一 ID。调用方可以通过它查询编译进度、
     * 审查步骤详情和重试编译。便捷构造器不传 jobId 时默认为 null。</p>
     */
    private final String jobId;

    /**
     * 创建编译响应。
     *
     * @param persistedCount 落盘数量
     */
    public CompileResponse(int persistedCount) {
        this(persistedCount, null);
    }

    /**
     * 创建编译响应。
     *
     * @param persistedCount 落盘数量
     * @param jobId 作业标识
     */
    @JsonCreator
    public CompileResponse(
            @JsonProperty("persistedCount") int persistedCount,
            @JsonProperty("jobId") String jobId
    ) {
        this.persistedCount = persistedCount;
        this.jobId = jobId;
    }
}
