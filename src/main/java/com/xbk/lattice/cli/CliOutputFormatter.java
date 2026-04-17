package com.xbk.lattice.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * CLI 输出格式化器
 *
 * 职责：统一命令行的文本与 JSON 输出
 *
 * @author xiexu
 */
public final class CliOutputFormatter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CliOutputFormatter() {
    }

    /**
     * 输出普通文本。
     *
     * @param text 文本内容
     */
    public static void printLine(String text) {
        System.out.println(text);
    }

    /**
     * 输出诊断文本到标准错误流。
     *
     * @param text 文本内容
     */
    public static void printErrorLine(String text) {
        System.err.println(text);
    }

    /**
     * 将对象格式化为 JSON 文本。
     *
     * @param value 对象
     * @return JSON 文本
     */
    public static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化 CLI 输出失败", exception);
        }
    }
}
