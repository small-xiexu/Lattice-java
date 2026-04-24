package com.xbk.lattice.llm.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 * LLM 重试支撑工具
 *
 * 职责：统一承载 LLM 调用的瞬时失败判定、退避与重试日志
 *
 * @author xiexu
 */
@Slf4j
public final class LlmRetrySupport {

    private static final int MAX_RETRY_ATTEMPTS = 5;

    private static final long BASE_RETRY_BACKOFF_MILLIS = 300L;

    private LlmRetrySupport() {
    }

    /**
     * 以统一重试策略执行调用。
     *
     * @param operationName 操作名称
     * @param routeResolution 路由解析结果
     * @param purpose 调用用途
     * @param supplier 实际调用逻辑
     * @param <T> 返回值类型
     * @return 调用结果
     */
    public static <T> T executeWithRetry(
            String operationName,
            LlmRouteResolution routeResolution,
            String purpose,
            RetryableSupplier<T> supplier
    ) {
        return executeWithRetry(operationName, routeResolution, purpose, null, supplier);
    }

    /**
     * 以统一重试策略执行调用，并回调每次失败尝试的观测信息。
     *
     * @param operationName 操作名称
     * @param routeResolution 路由解析结果
     * @param purpose 调用用途
     * @param retryObserver 失败尝试观测器
     * @param supplier 实际调用逻辑
     * @param <T> 返回值类型
     * @return 调用结果
     */
    public static <T> T executeWithRetry(
            String operationName,
            LlmRouteResolution routeResolution,
            String purpose,
            RetryObserver retryObserver,
            RetryableSupplier<T> supplier
    ) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return supplier.get();
            }
            catch (RuntimeException exception) {
                lastException = exception;
                boolean retryable = isRetryable(exception);
                boolean willRetry = retryable && attempt < MAX_RETRY_ATTEMPTS;
                long backoffMillis = willRetry ? BASE_RETRY_BACKOFF_MILLIS * attempt : 0L;
                notifyRetryObserver(
                        retryObserver,
                        new RetryObservation(
                                operationName,
                                attempt,
                                MAX_RETRY_ATTEMPTS,
                                willRetry,
                                backoffMillis,
                                resolveStatusCode(exception),
                                resolveErrorCode(exception, retryable && !willRetry),
                                resolveErrorSummary(exception),
                                exception
                        )
                );
                if (!willRetry) {
                    if (retryable && attempt >= MAX_RETRY_ATTEMPTS) {
                        throw createRetryExhaustedException(operationName, attempt, exception);
                    }
                    throw exception;
                }
                String routeLabel = routeResolution == null ? "" : routeResolution.getRouteLabel();
                log.warn(
                        "{} failed on attempt {}/{} and will retry. routeLabel: {}, purpose: {}, reason: {}",
                        operationName,
                        attempt,
                        MAX_RETRY_ATTEMPTS,
                        routeLabel,
                        purpose == null ? "" : purpose,
                        resolveErrorSummary(exception)
                );
                sleepBeforeRetry(backoffMillis);
            }
        }
        throw lastException == null
                ? new IllegalStateException(operationName + " failed without exception")
                : lastException;
    }

    /**
     * 返回统一最大重试次数。
     *
     * @return 最大重试次数
     */
    public static int maxAttempts() {
        return MAX_RETRY_ATTEMPTS;
    }

    /**
     * 解析异常对应的 HTTP 状态码。
     *
     * @param throwable 异常
     * @return HTTP 状态码；无法识别时返回 null
     */
    public static Integer resolveStatusCode(Throwable throwable) {
        if (throwable instanceof RestClientResponseException responseException) {
            return Integer.valueOf(responseException.getStatusCode().value());
        }
        if (throwable instanceof TransientAiException transientAiException) {
            return parseLeadingStatusCode(transientAiException.getMessage());
        }
        Throwable rootCause = rootCause(throwable);
        if (rootCause instanceof RestClientResponseException responseException) {
            return Integer.valueOf(responseException.getStatusCode().value());
        }
        return null;
    }

    /**
     * 解析异常对应的稳定错误码。
     *
     * @param throwable 异常
     * @return 稳定错误码
     */
    public static String resolveErrorCode(Throwable throwable) {
        return resolveErrorCode(throwable, false);
    }

    /**
     * 解析异常对应的稳定错误码。
     *
     * @param throwable 异常
     * @param retryExhausted 是否已重试耗尽
     * @return 稳定错误码
     */
    public static String resolveErrorCode(Throwable throwable, boolean retryExhausted) {
        if (retryExhausted) {
            return "LLM_RETRY_EXHAUSTED";
        }
        if (throwable instanceof LlmRetryExhaustedException) {
            return "LLM_RETRY_EXHAUSTED";
        }
        Integer statusCode = resolveStatusCode(throwable);
        if (statusCode != null) {
            if (statusCode.intValue() >= 500) {
                return "LLM_UPSTREAM_5XX";
            }
            if (statusCode.intValue() == 408 || statusCode.intValue() == 409
                    || statusCode.intValue() == 425 || statusCode.intValue() == 429) {
                return "LLM_RETRYABLE_HTTP_ERROR";
            }
            return "LLM_HTTP_ERROR";
        }
        Throwable rootCause = rootCause(throwable);
        if (rootCause instanceof SocketTimeoutException) {
            return "LLM_REQUEST_TIMEOUT";
        }
        if (rootCause instanceof EOFException || rootCause instanceof SocketException || rootCause instanceof IOException) {
            return "LLM_TRANSPORT_ERROR";
        }
        if (throwable instanceof TransientAiException) {
            return "LLM_TRANSIENT_AI_ERROR";
        }
        return "LLM_CALL_FAILED";
    }

    /**
     * 解析异常摘要。
     *
     * @param throwable 异常
     * @return 错误摘要
     */
    public static String resolveErrorSummary(Throwable throwable) {
        Throwable rootCause = rootCause(throwable);
        String message = rootCause.getMessage();
        if (message == null || message.isBlank()) {
            return rootCause.getClass().getSimpleName();
        }
        return rootCause.getClass().getSimpleName() + ": " + message;
    }

    /**
     * 解析上游 Provider 请求标识。
     *
     * @param throwable 异常
     * @return Provider 请求标识；无法识别时返回 null
     */
    public static String resolveProviderRequestId(Throwable throwable) {
        RestClientResponseException responseException = findResponseException(throwable);
        if (responseException == null) {
            return null;
        }
        String headerRequestId = readFirstHeader(
                responseException.getResponseHeaders(),
                "x-request-id",
                "request-id",
                "anthropic-request-id",
                "openai-request-id"
        );
        if (headerRequestId != null && !headerRequestId.isBlank()) {
            return headerRequestId;
        }
        String responseBody = responseException.getResponseBodyAsString();
        String requestId = extractJsonTextValue(responseBody, "request_id");
        if (requestId != null && !requestId.isBlank()) {
            return requestId;
        }
        return extractJsonTextValue(responseBody, "id");
    }

    private static boolean isRetryable(RuntimeException exception) {
        if (exception instanceof TransientAiException) {
            return true;
        }
        if (exception instanceof RestClientResponseException responseException) {
            int statusCode = responseException.getStatusCode().value();
            return statusCode == 408
                    || statusCode == 409
                    || statusCode == 425
                    || statusCode == 429
                    || statusCode >= 500;
        }
        if (!(exception instanceof RestClientException) && !(exception instanceof IllegalStateException)) {
            return false;
        }
        Throwable rootCause = rootCause(exception);
        return rootCause instanceof EOFException
                || rootCause instanceof SocketException
                || rootCause instanceof SocketTimeoutException
                || rootCause instanceof IOException;
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static RestClientResponseException findResponseException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != current) {
            if (current instanceof RestClientResponseException responseException) {
                return responseException;
            }
            current = current.getCause();
        }
        return null;
    }

    private static String readFirstHeader(HttpHeaders headers, String... headerNames) {
        if (headers == null || headerNames == null) {
            return null;
        }
        for (String headerName : headerNames) {
            if (headerName == null || headerName.isBlank()) {
                continue;
            }
            String value = headers.getFirst(headerName);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String extractJsonTextValue(String text, String fieldName) {
        if (text == null || text.isBlank() || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        String quotedField = "\"" + fieldName + "\"";
        int fieldIndex = text.indexOf(quotedField);
        if (fieldIndex < 0) {
            return null;
        }
        int colonIndex = text.indexOf(':', fieldIndex + quotedField.length());
        if (colonIndex < 0) {
            return null;
        }
        int startQuote = text.indexOf('"', colonIndex + 1);
        if (startQuote < 0) {
            return null;
        }
        int endQuote = text.indexOf('"', startQuote + 1);
        if (endQuote <= startQuote) {
            return null;
        }
        return text.substring(startQuote + 1, endQuote).trim();
    }

    private static LlmRetryExhaustedException createRetryExhaustedException(
            String operationName,
            int attempts,
            RuntimeException exception
    ) {
        Integer statusCode = resolveStatusCode(exception);
        String errorCode = resolveErrorCode(exception);
        String errorSummary = resolveErrorSummary(exception);
        return new LlmRetryExhaustedException(
                operationName,
                attempts,
                statusCode,
                errorCode,
                errorSummary,
                exception
        );
    }

    private static Integer parseLeadingStatusCode(String message) {
        if (message == null || message.length() < 3) {
            return null;
        }
        String candidate = message.substring(0, 3);
        if (!candidate.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return Integer.valueOf(candidate);
    }

    private static void notifyRetryObserver(RetryObserver retryObserver, RetryObservation retryObservation) {
        if (retryObserver == null || retryObservation == null) {
            return;
        }
        try {
            retryObserver.onAttemptFailure(retryObservation);
        }
        catch (RuntimeException observerException) {
            log.warn("Retry observer failed: {}", observerException.getMessage(), observerException);
        }
    }

    private static void sleepBeforeRetry(long backoffMillis) {
        try {
            Thread.sleep(backoffMillis);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM invocation retry interrupted", exception);
        }
    }

    /**
     * 可重试调用抽象。
     *
     * @param <T> 返回值类型
     */
    @FunctionalInterface
    public interface RetryableSupplier<T> {

        /**
         * 执行一次调用。
         *
         * @return 调用结果
         */
        T get();
    }

    /**
     * 重试失败观测器。
     *
     * 职责：接收每次失败尝试的观测信息
     *
     * @author xiexu
     */
    @FunctionalInterface
    public interface RetryObserver {

        /**
         * 处理一次失败尝试。
         *
         * @param retryObservation 重试观测信息
         */
        void onAttemptFailure(RetryObservation retryObservation);
    }

    /**
     * 重试失败观测信息。
     *
     * 职责：承载失败尝试的序号、退避与归因信息
     *
     * @author xiexu
     */
    public static final class RetryObservation {

        private final String operationName;

        private final int attemptNo;

        private final int maxAttempts;

        private final boolean willRetry;

        private final long backoffMillis;

        private final Integer statusCode;

        private final String errorCode;

        private final String errorSummary;

        private final RuntimeException exception;

        /**
         * 创建重试失败观测信息。
         *
         * @param operationName 操作名称
         * @param attemptNo 当前尝试次数
         * @param maxAttempts 最大尝试次数
         * @param willRetry 是否继续重试
         * @param backoffMillis 退避毫秒数
         * @param statusCode HTTP 状态码
         * @param errorCode 稳定错误码
         * @param errorSummary 错误摘要
         * @param exception 原始异常
         */
        public RetryObservation(
                String operationName,
                int attemptNo,
                int maxAttempts,
                boolean willRetry,
                long backoffMillis,
                Integer statusCode,
                String errorCode,
                String errorSummary,
                RuntimeException exception
        ) {
            this.operationName = operationName;
            this.attemptNo = attemptNo;
            this.maxAttempts = maxAttempts;
            this.willRetry = willRetry;
            this.backoffMillis = backoffMillis;
            this.statusCode = statusCode;
            this.errorCode = errorCode;
            this.errorSummary = errorSummary;
            this.exception = exception;
        }

        /**
         * 返回操作名称。
         *
         * @return 操作名称
         */
        public String getOperationName() {
            return operationName;
        }

        /**
         * 返回当前尝试次数。
         *
         * @return 当前尝试次数
         */
        public int getAttemptNo() {
            return attemptNo;
        }

        /**
         * 返回最大尝试次数。
         *
         * @return 最大尝试次数
         */
        public int getMaxAttempts() {
            return maxAttempts;
        }

        /**
         * 返回是否继续重试。
         *
         * @return 是否继续重试
         */
        public boolean isWillRetry() {
            return willRetry;
        }

        /**
         * 返回退避毫秒数。
         *
         * @return 退避毫秒数
         */
        public long getBackoffMillis() {
            return backoffMillis;
        }

        /**
         * 返回 HTTP 状态码。
         *
         * @return HTTP 状态码
         */
        public Integer getStatusCode() {
            return statusCode;
        }

        /**
         * 返回稳定错误码。
         *
         * @return 稳定错误码
         */
        public String getErrorCode() {
            return errorCode;
        }

        /**
         * 返回错误摘要。
         *
         * @return 错误摘要
         */
        public String getErrorSummary() {
            return errorSummary;
        }

        /**
         * 返回原始异常。
         *
         * @return 原始异常
         */
        public RuntimeException getException() {
            return exception;
        }
    }
}
