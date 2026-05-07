package com.xbk.lattice.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 结构化事件日志器
 *
 * 职责：统一为关键主链事件补齐 MDC 字段，并交给 JSON appender 输出
 *
 * @author xiexu
 */
@Service
@Slf4j
public class StructuredEventLogger {

    private final Tracer tracer;

    /**
     * 创建结构化事件日志器。
     *
     * @param tracerProvider Micrometer Tracer 提供器
     */
    @Autowired
    public StructuredEventLogger(ObjectProvider<Tracer> tracerProvider) {
        this(tracerProvider.getIfAvailable());
    }

    /**
     * 创建无 tracing 的结构化事件日志器。
     */
    public StructuredEventLogger() {
        this((Tracer) null);
    }

    /**
     * 创建结构化事件日志器。
     *
     * @param tracer Micrometer Tracer
     */
    public StructuredEventLogger(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * 记录 INFO 级结构化事件。
     *
     * @param eventName 事件名
     * @param fields 事件字段
     */
    public void info(String eventName, Map<String, Object> fields) {
        withStructuredContext(eventName, fields, () -> log.info(eventName));
    }

    /**
     * 记录 WARN 级结构化事件。
     *
     * @param eventName 事件名
     * @param fields 事件字段
     * @param throwable 异常
     */
    public void warn(String eventName, Map<String, Object> fields, Throwable throwable) {
        withStructuredContext(eventName, fields, () -> {
            if (throwable == null) {
                log.warn(eventName);
            }
            else {
                log.warn(eventName, throwable);
            }
        });
    }

    /**
     * 记录 ERROR 级结构化事件。
     *
     * @param eventName 事件名
     * @param fields 事件字段
     * @param throwable 异常
     */
    public void error(String eventName, Map<String, Object> fields, Throwable throwable) {
        withStructuredContext(eventName, fields, () -> {
            if (throwable == null) {
                log.error(eventName);
            }
            else {
                log.error(eventName, throwable);
            }
        });
    }

    private void withStructuredContext(String eventName, Map<String, Object> fields, LogAction logAction) {
        Map<String, String> contextValues = buildContextValues(eventName, fields);
        Map<String, String> previousValues = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : contextValues.entrySet()) {
            String key = entry.getKey();
            previousValues.put(key, MDC.get(key));
            MDC.put(key, entry.getValue());
        }
        try {
            logAction.log();
        }
        finally {
            restoreMdc(previousValues);
        }
    }

    private Map<String, String> buildContextValues(String eventName, Map<String, Object> fields) {
        Map<String, String> contextValues = new LinkedHashMap<String, String>();
        contextValues.put("eventName", eventName);

        String traceId = resolveTraceId();
        String rootTraceId = resolveRootTraceId(traceId);
        if (traceId != null && !traceId.isBlank()) {
            contextValues.put("traceId", traceId);
        }
        if (rootTraceId != null && !rootTraceId.isBlank()) {
            contextValues.put("rootTraceId", rootTraceId);
        }
        String spanId = resolveSpanId();
        if (spanId != null && !spanId.isBlank()) {
            contextValues.put("spanId", spanId);
        }

        if (fields == null || fields.isEmpty()) {
            return contextValues;
        }
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            contextValues.put(entry.getKey(), String.valueOf(value));
        }
        return contextValues;
    }

    private String resolveTraceId() {
        Tracer currentTracer = this.tracer;
        if (currentTracer != null) {
            Span currentSpan = currentTracer.currentSpan();
            if (currentSpan != null && currentSpan.context() != null) {
                String traceId = currentSpan.context().traceId();
                if (traceId != null && !traceId.isBlank()) {
                    return traceId;
                }
            }
        }
        return MDC.get("traceId");
    }

    private String resolveRootTraceId(String traceId) {
        String rootTraceId = MDC.get("rootTraceId");
        if (rootTraceId != null && !rootTraceId.isBlank()) {
            return rootTraceId;
        }
        return traceId;
    }

    private String resolveSpanId() {
        Tracer currentTracer = this.tracer;
        if (currentTracer != null) {
            Span currentSpan = currentTracer.currentSpan();
            if (currentSpan != null && currentSpan.context() != null) {
                String spanId = currentSpan.context().spanId();
                if (spanId != null && !spanId.isBlank()) {
                    return spanId;
                }
            }
        }
        return MDC.get("spanId");
    }

    private void restoreMdc(Map<String, String> previousValues) {
        for (Map.Entry<String, String> entry : previousValues.entrySet()) {
            String key = entry.getKey();
            String previousValue = entry.getValue();
            if (previousValue == null) {
                MDC.remove(key);
            }
            else {
                MDC.put(key, previousValue);
            }
        }
    }

    /**
     * 日志动作。
     *
     * @author xiexu
     */
    @FunctionalInterface
    private interface LogAction {

        /**
         * 执行日志写入。
         */
        void log();
    }
}
