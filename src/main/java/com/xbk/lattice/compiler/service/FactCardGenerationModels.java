package com.xbk.lattice.compiler.service;

import java.util.List;

/**
 * 表格块。
 *
 * @author xiexu
 */
final class TableBlock {

    final List<String> headers;

    final List<List<String>> rows;

    final String evidenceText;

    final int startIndex;

    /**
     * 创建表格块。
     *
     * @param headers 表头
     * @param rows 数据行
     * @param evidenceText 证据文本
     * @param startIndex 起始行号
     */
    TableBlock(List<String> headers, List<List<String>> rows, String evidenceText, int startIndex) {
        this.headers = headers;
        this.rows = rows;
        this.evidenceText = evidenceText;
        this.startIndex = startIndex;
    }

    /**
     * 获取表头。
     *
     * @return 表头
     */
    List<String> getHeaders() {
        return headers;
    }

    /**
     * 获取数据行。
     *
     * @return 数据行
     */
    List<List<String>> getRows() {
        return rows;
    }

    /**
     * 获取证据文本。
     *
     * @return 证据文本
     */
    String getEvidenceText() {
        return evidenceText;
    }

    /**
     * 获取起始行号。
     *
     * @return 起始行号
     */
    int getStartIndex() {
        return startIndex;
    }
}

/**
 * 文本列表项。
 *
 * @author xiexu
 */
final class LineItem {

    final String order;

    final String text;

    final String raw;

    /**
     * 创建文本列表项。
     *
     * @param order 顺序标识
     * @param text 文本
     * @param raw 原始行
     */
    LineItem(String order, String text, String raw) {
        this.order = order;
        this.text = text;
        this.raw = raw;
    }

    /**
     * 获取顺序标识。
     *
     * @return 顺序标识
     */
    String getOrder() {
        return order;
    }

    /**
     * 获取文本。
     *
     * @return 文本
     */
    String getText() {
        return text;
    }

    /**
     * 获取原始行。
     *
     * @return 原始行
     */
    String getRaw() {
        return raw;
    }
}

/**
 * 键值列表项。
 *
 * @author xiexu
 */
final class KeyValueItem {

    final String key;

    final String value;

    final String raw;

    /**
     * 创建键值列表项。
     *
     * @param key 键
     * @param value 值
     * @param raw 原始行
     */
    KeyValueItem(String key, String value, String raw) {
        this.key = key;
        this.value = value;
        this.raw = raw;
    }

    /**
     * 获取键。
     *
     * @return 键
     */
    String getKey() {
        return key;
    }

    /**
     * 获取值。
     *
     * @return 值
     */
    String getValue() {
        return value;
    }

    /**
     * 获取原始行。
     *
     * @return 原始行
     */
    String getRaw() {
        return raw;
    }
}

/**
 * 状态定义。
 *
 * @author xiexu
 */
final class StatusDefinition {

    final String value;

    final String group;

    /**
     * 创建状态定义。
     *
     * @param value 状态词
     * @param group 互斥分组
     */
    StatusDefinition(String value, String group) {
        this.value = value;
        this.group = group;
    }

    /**
     * 获取状态词。
     *
     * @return 状态词
     */
    String getValue() {
        return value;
    }

    /**
     * 获取互斥分组。
     *
     * @return 互斥分组
     */
    String getGroup() {
        return group;
    }
}

/**
 * 状态条目。
 *
 * @author xiexu
 */
final class StatusItem {

    final String subject;

    final String status;

    final String statusGroup;

    final String raw;

    /**
     * 创建状态条目。
     *
     * @param subject 状态主语
     * @param status 状态词
     * @param statusGroup 互斥分组
     * @param raw 原始行
     */
    StatusItem(String subject, String status, String statusGroup, String raw) {
        this.subject = subject;
        this.status = status;
        this.statusGroup = statusGroup;
        this.raw = raw;
    }

    /**
     * 获取状态主语。
     *
     * @return 状态主语
     */
    String getSubject() {
        return subject;
    }

    /**
     * 获取状态词。
     *
     * @return 状态词
     */
    String getStatus() {
        return status;
    }

    /**
     * 获取互斥分组。
     *
     * @return 互斥分组
     */
    String getStatusGroup() {
        return statusGroup;
    }

    /**
     * 获取原始行。
     *
     * @return 原始行
     */
    String getRaw() {
        return raw;
    }
}

/**
 * 规则约束条目。
 *
 * @author xiexu
 */
final class PolicyItem {

    final String text;

    final String raw;

    /**
     * 创建规则约束条目。
     *
     * @param text 约束文本
     * @param raw 原始行
     */
    PolicyItem(String text, String raw) {
        this.text = text;
        this.raw = raw;
    }

    /**
     * 获取约束文本。
     *
     * @return 约束文本
     */
    String getText() {
        return text;
    }

    /**
     * 获取原始行。
     *
     * @return 原始行
     */
    String getRaw() {
        return raw;
    }
}
