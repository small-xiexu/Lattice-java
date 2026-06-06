package com.xbk.lattice.query.citation;

import com.xbk.lattice.observability.StructuredEventLogger;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Query Debug Trace 管理器
 *
 * 职责：统一 trace 事件输出入口，封装 L1/L2 开关判断与字段截断
 *
 * @author xiexu
 */
@Component
public class QueryTraceManager {

    private static final int CLAIM_TEXT_MAX_LENGTH = 120;
    private static final int HARD_FACT_TOKENS_MAX_LENGTH = 200;
    private static final int MATCHED_EXCERPT_MAX_LENGTH = 80;
    private static final int TU_EVIDENCE_TEXT_MAX_LENGTH = 200;

    private final StructuredEventLogger structuredEventLogger;

    private final QueryTraceProperties traceProperties;

    /**
     * 创建 trace 管理器。
     *
     * @param structuredEventLogger 结构化事件日志器
     * @param traceProperties trace 配置
     */
    public QueryTraceManager(
            StructuredEventLogger structuredEventLogger,
            QueryTraceProperties traceProperties
    ) {
        this.structuredEventLogger = structuredEventLogger;
        this.traceProperties = traceProperties;
    }

    /**
     * 记录 L1 生产安全事件（默认开启，记录数量和结果）。
     *
     * @param eventName 事件名
     * @param stage 阶段名
     * @param baseFields 基础字段
     */
    public void logL1Event(String eventName, String stage, Map<String, Object> baseFields) {
        Map<String, Object> fields = new LinkedHashMap<String, Object>(baseFields);
        fields.put("event_level", "L1");
        fields.put("stage", stage);
        fields.put("query_id", currentQueryId());
        fields.put("trace_id", currentTraceId());
        structuredEventLogger.info(eventName, fields);
    }

    /**
     * 记录 L2 Debug 详细事件（仅在总开关和对应 stage 开关均开启时输出）。
     *
     * @param eventName 事件名
     * @param stage 阶段名
     * @param baseFields 基础字段
     */
    public void logL2Event(String eventName, String stage, Map<String, Object> baseFields) {
        if (!traceProperties.isStageEnabled(stage)) {
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<String, Object>(baseFields);
        fields.put("event_level", "L2");
        fields.put("stage", stage);
        fields.put("query_id", currentQueryId());
        fields.put("trace_id", currentTraceId());
        structuredEventLogger.info(eventName, fields);
    }

    /**
     * 判断指定 stage 的 L2 trace 是否开启。
     */
    public boolean isL2Enabled(String stage) {
        return traceProperties.isStageEnabled(stage);
    }

    String truncateClaimText(String text) {
        return truncate(text, CLAIM_TEXT_MAX_LENGTH);
    }

    String truncateHardFactTokens(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(token);
            if (sb.length() >= HARD_FACT_TOKENS_MAX_LENGTH) {
                break;
            }
        }
        return truncate(sb.toString(), HARD_FACT_TOKENS_MAX_LENGTH);
    }

    String truncateMatchedExcerpt(String excerpt) {
        return truncate(excerpt, MATCHED_EXCERPT_MAX_LENGTH);
    }

    String truncateTuEvidenceText(String text) {
        return truncate(text, TU_EVIDENCE_TEXT_MAX_LENGTH);
    }

    private String currentQueryId() {
        return MDC.get("queryId");
    }

    private String currentTraceId() {
        return MDC.get("traceId");
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
