package com.xbk.lattice.query.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.shared.json.JsonMappers;

/**
 * Terminal unit hit metadata 小工具。
 *
 * 职责：对 QueryArticleHit 做 terminal unit channel 的结构化 JSON 判断，
 *       避免脆弱字符串匹配因 JSONB 序列化格式（compact vs spaced）而失效。
 *
 * @author xiexu
 */
final class TerminalUnitHitMetadataSupport {

    private static final ObjectMapper OBJECT_MAPPER = JsonMappers.defaultMapper();

    private TerminalUnitHitMetadataSupport() {
    }

    /**
     * 判断命中是否来自 terminal unit FTS channel。
     *
     * 使用结构化 JSON 解析读取 metadata 的 {@code channel} 字段，
     * 值与 {@code fact_card_terminal_fts} 相等时返回 true。
     * metadata 为空、解析失败、channel 缺失时 fail-closed 返回 false。
     *
     * @param hit 查询命中
     * @return 是 terminal unit channel 命中返回 true
     */
    static boolean isTerminalUnitChannelHit(QueryArticleHit hit) {
        if (hit == null) {
            return false;
        }
        if (hit.getEvidenceType() != QueryEvidenceType.FACT_CARD) {
            return false;
        }
        String metadataJson = hit.getMetadataJson();
        if (metadataJson == null || metadataJson.isBlank()) {
            return false;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(metadataJson);
            String channel = node.path("channel").asText("");
            return RetrievalStrategyResolver.CHANNEL_FACT_CARD_TERMINAL_FTS.equals(channel);
        } catch (Exception exception) {
            return false;
        }
    }
}
