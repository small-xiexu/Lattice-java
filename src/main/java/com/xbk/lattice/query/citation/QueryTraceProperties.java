package com.xbk.lattice.query.citation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Query Debug Trace 配置
 *
 * 职责：控制全链路结构化 trace 的总开关和各 stage 独立开关
 *
 * @author xiexu
 */
@Component
@ConfigurationProperties("lattice.query.trace")
public class QueryTraceProperties {

    /**
     * 总开关，默认关闭。当 evalRunId 存在时自动开启。
     */
    private boolean enabled = false;

    /**
     * 各 stage 独立开关，默认全部关闭。
     */
    private Map<String, Boolean> stages = new LinkedHashMap<String, Boolean>();

    /**
     * 创建 trace 配置。
     */
    public QueryTraceProperties() {
        stages.put("query_entry", false);
        stages.put("rewrite", false);
        stages.put("retrieval", false);
        stages.put("rerank", false);
        stages.put("rrf_fusion", false);
        stages.put("evidence_selector", false);
        stages.put("fallback_outcome", false);
        stages.put("citation_check", false);
        stages.put("citation_validation", false);
        stages.put("answer_generation", false);
        stages.put("finalize", false);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Boolean> getStages() {
        return stages;
    }

    public void setStages(Map<String, Boolean> stages) {
        this.stages = stages;
    }

    /**
     * 判断指定 stage 的详细 trace（L2）是否开启。
     */
    public boolean isStageEnabled(String stageName) {
        if (!enabled) {
            return false;
        }
        Boolean stageEnabled = stages.get(stageName);
        return stageEnabled != null && stageEnabled;
    }
}
