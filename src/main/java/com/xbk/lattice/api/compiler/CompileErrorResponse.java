package com.xbk.lattice.api.compiler;

/**
 * 编译错误响应
 *
 * 职责：承载编译接口的最小错误响应体
 *
 * @author xiexu
 */
public class CompileErrorResponse {

    private final String code;

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

    /**
     * 获取错误码。
     *
     * @return 错误码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取错误信息。
     *
     * @return 错误信息
     */
    public String getMessage() {
        return message;
    }
}
