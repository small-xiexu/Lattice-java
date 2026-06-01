package com.xbk.lattice.api.compiler;

import lombok.Getter;

/**
 * 编译错误响应。
 *
 * <p>承载编译接口的错误响应体。当编译因参数校验失败、源目录不可读、
 * 编译流程异常等原因无法正常执行时，调用方通过这个结构获取错误信息。
 *
 * @author xiexu
 */
@Getter
public class CompileErrorResponse {

    /**
     * 错误码。
     *
     * <p>机器可读的错误标识，例如 INVALID_SOURCE_DIR、COMPILE_TIMEOUT、INTERNAL_ERROR 等。
     * 调用方可以根据错误码做分类处理和重试策略。</p>
     */
    private final String code;

    /**
     * 错误信息。
     *
     * <p>面向人可读的错误描述，用于前端展示或日志排查。内容可能包含具体失败原因和上下文。</p>
     */
    private final String message;

    /**
     * 创建编译错误响应。
     *
     * @param code 错误码
     * @param message 错误信息
     */
    public CompileErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
