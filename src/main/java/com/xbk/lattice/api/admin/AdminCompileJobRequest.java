package com.xbk.lattice.api.admin;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * 管理侧编译作业请求。
 *
 * <p>承载 admin compile job 提交参数，由 Spring MVC 从 JSON 请求体绑定。
 *
 * @author xiexu
 */
@Getter
@Setter
public class AdminCompileJobRequest {

    /**
     * 源目录路径。
     *
     * <p>可以是绝对路径或上传暂存目录。为空时编译无输入源，作业将失败。
     * 服务端应对此路径做规范化和存在性校验。</p>
     */
    private String sourceDir;

    /**
     * 是否增量编译。
     *
     * <p>{@code true} 时仅处理变更文件，编译快但可能遗漏依赖变更导致的级联影响。
     * {@code false} 时全量重编所有源文件，耗时长但结果完整。</p>
     */
    private boolean incremental;

    /**
     * 是否异步执行。
     *
     * <p>默认 {@code Boolean.TRUE}。{@code true} 时立即返回 jobId，前端轮询状态。
     * {@code false} 时同步等待编译完成，可能导致 HTTP 超时。
     * 为 {@code null} 时 {@code isAsync()} 按 {@code true} 处理（null-coalescing 防御逻辑）。
     * Lombok getter/setter 已排除此字段，由手写 {@code isAsync()} 和 {@code setAsync()} 管理。</p>
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Boolean async = Boolean.TRUE;

    /**
     * 编排模式标识。
     *
     * <p>影响 compile 步骤（parse→review→fix→persist）的执行顺序和并发度。
     * 为空时使用服务端默认编排。可选值由服务端定义（如 {@code sequential} / {@code parallel}）。</p>
     */
    private String orchestrationMode;

    /**
     * 审查模式标识。
     *
     * <p>影响编译后是否触发 LLM review 步骤及审查深度。
     * {@code none} 时跳过全部审查，编译最快但无质量保障。
     * {@code full} 时执行完整审查 + 自动修复。
     * 为空时使用服务端默认审查模式。</p>
     */
    private String reviewMode;

    /**
     * 返回是否异步执行（null-safe）。
     *
     * <p>{@code async} 为 {@code null} 或 {@code true} 时均返回 {@code true}，
     * 仅在显式设为 {@code false} 时返回 {@code false}。</p>
     *
     * @return 是否异步执行
     */
    public boolean isAsync() {
        return async == null || async.booleanValue();
    }

    /**
     * 设置是否异步执行。
     *
     * @param async 是否异步执行；{@code null} 时 {@code isAsync()} 按 {@code true} 处理
     */
    public void setAsync(Boolean async) {
        this.async = async;
    }
}
