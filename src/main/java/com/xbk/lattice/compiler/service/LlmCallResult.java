package com.xbk.lattice.compiler.service;

/**
 * LLM 调用结果
 *
 * 职责：承载模型文本输出与 token 用量
 *
 * @author xiexu
 */
public class LlmCallResult {

    private final String content;

    private final int inputTokens;

    private final int outputTokens;

    /**
     * 创建 LLM 调用结果。
     *
     * @param content 模型输出文本
     * @param inputTokens 输入 token 数
     * @param outputTokens 输出 token 数
     */
    public LlmCallResult(String content, int inputTokens, int outputTokens) {
        this.content = content;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
    }

    /**
     * 获取模型输出文本。
     *
     * @return 模型输出文本
     */
    public String getContent() {
        return content;
    }

    /**
     * 获取输入 token 数。
     *
     * @return 输入 token 数
     */
    public int getInputTokens() {
        return inputTokens;
    }

    /**
     * 获取输出 token 数。
     *
     * @return 输出 token 数
     */
    public int getOutputTokens() {
        return outputTokens;
    }
}
