package com.xbk.lattice.shared.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * JSON Mapper 单例集合
 *
 * 职责：集中提供无特殊配置的 JSON 序列化工具，避免各子域散落创建 ObjectMapper
 *
 * @author xiexu
 */
public final class JsonMappers {

    private static final ObjectMapper DEFAULT_MAPPER = JsonMapper.builder().build();

    private static final ObjectMapper MODULE_AWARE_MAPPER = JsonMapper.builder().findAndAddModules().build();

    private JsonMappers() {
    }

    /**
     * 返回默认 JSON Mapper。
     *
     * @return 默认 JSON Mapper
     */
    public static ObjectMapper defaultMapper() {
        return DEFAULT_MAPPER;
    }

    /**
     * 返回启用模块发现的 JSON Mapper。
     *
     * @return 启用模块发现的 JSON Mapper
     */
    public static ObjectMapper moduleAwareMapper() {
        return MODULE_AWARE_MAPPER;
    }
}
