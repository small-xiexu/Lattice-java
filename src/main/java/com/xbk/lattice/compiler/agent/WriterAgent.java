package com.xbk.lattice.compiler.agent;

/**
 * WriterAgent
 *
 * 职责：根据单个合并概念生成结构化文章草稿
 *
 * @author xiexu
 */
public interface WriterAgent {

    /**
     * 执行文章草稿生成。
     *
     * @param writerTask Writer 输入任务
     * @return Writer 输出结果
     */
    WriterResult write(WriterTask writerTask);
}
