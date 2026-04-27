package com.xbk.lattice.query.service;

/**
 * 查询意图
 *
 * 职责：表达 Query Preparation 阶段识别出的检索倾向
 *
 * @author xiexu
 */
public enum QueryIntent {

    /**
     * 通用知识查询。
     */
    GENERAL,

    /**
     * 代码结构、类名、方法或调用链查询。
     */
    CODE_STRUCTURE,

    /**
     * 配置键、开关、阈值或运行参数查询。
     */
    CONFIGURATION,

    /**
     * 故障、异常、失败原因或排查类查询。
     */
    TROUBLESHOOTING,

    /**
     * 架构、设计、取舍或方案解释类查询。
     */
    ARCHITECTURE
}
