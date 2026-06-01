package com.xbk.lattice.api.query;

import lombok.Getter;

/**
 * 查询错误响应。
 *
 * <p>承载查询接口的错误响应体。当查询因参数校验失败、超时、系统异常等原因
 * 无法正常返回时，调用方通过这个结构获取错误码和描述信息。
 *
 * @author xiexu
 */
@Getter
public class QueryErrorResponse {

    /**
     * 错误码。
     *
     * <p>机器可读的错误标识，例如 INVALID_QUESTION、QUERY_TIMEOUT、INTERNAL_ERROR 等。
     * 调用方可以根据错误码做分类处理和重试策略。</p>
     */
    private final String code;

    /**
     * 错误信息。
     *
     * <p>面向人可读的错误描述，用于前端展示或日志记录。内容可能包含具体的失败原因和排查建议。</p>
     */
    private final String message;

    /**
     * 创建查询错误响应。
     *
     * @param code 错误码
     * @param message 错误信息
     */
    public QueryErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
