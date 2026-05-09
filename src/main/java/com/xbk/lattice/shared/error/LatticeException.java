package com.xbk.lattice.shared.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lattice 领域异常基类
 *
 * 职责：统一承载业务错误码、错误消息与可观测上下文
 *
 * @author xiexu
 */
public abstract class LatticeException extends RuntimeException {

    private final String errorCode;

    private final Map<String, String> context;

    /**
     * 创建 Lattice 领域异常。
     *
     * @param errorCode 错误码
     * @param message 错误消息
     */
    protected LatticeException(String errorCode, String message) {
        this(errorCode, message, null, Collections.emptyMap());
    }

    /**
     * 创建带根因的 Lattice 领域异常。
     *
     * @param errorCode 错误码
     * @param message 错误消息
     * @param cause 根因
     */
    protected LatticeException(String errorCode, String message, Throwable cause) {
        this(errorCode, message, cause, Collections.emptyMap());
    }

    /**
     * 创建带上下文的 Lattice 领域异常。
     *
     * @param errorCode 错误码
     * @param message 错误消息
     * @param cause 根因
     * @param context 错误上下文
     */
    protected LatticeException(
            String errorCode,
            String message,
            Throwable cause,
            Map<String, String> context
    ) {
        super(message, cause);
        this.errorCode = errorCode;
        this.context = copyContext(context);
    }

    /**
     * 返回错误码。
     *
     * @return 错误码
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * 返回错误上下文。
     *
     * @return 不可变错误上下文
     */
    public Map<String, String> getContext() {
        return context;
    }

    /**
     * 复制并冻结错误上下文。
     *
     * @param source 原始上下文
     * @return 不可变上下文
     */
    private Map<String, String> copyContext(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<String, String>(source));
    }
}
