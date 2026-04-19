package com.xbk.lattice.llm.service;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Arrays;
import java.util.List;

/**
 * 基于 Spring AI ChatModel 的 LLM 客户端
 *
 * 职责：把 system/user prompt 转换为 Prompt，并提取文本与 token 用量
 *
 * @author xiexu
 */
public class ChatModelLlmClient implements LlmClient {

    private final ChatModel chatModel;

    /**
     * 创建基于 ChatModel 的 LLM 客户端。
     *
     * @param chatModel Spring AI ChatModel
     */
    public ChatModelLlmClient(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 调用模型。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return 调用结果
     */
    @Override
    public LlmCallResult call(String systemPrompt, String userPrompt) {
        List<Message> messages = Arrays.<Message>asList(
                new SystemMessage(systemPrompt),
                new UserMessage(userPrompt)
        );
        ChatResponse chatResponse = chatModel.call(new Prompt(messages));
        String content = chatResponse.getResult().getOutput().getText();
        ChatResponseMetadata metadata = chatResponse.getMetadata();
        Usage usage = metadata == null ? null : metadata.getUsage();
        int inputTokens = usage == null || usage.getPromptTokens() == null
                ? estimateTokens(systemPrompt + userPrompt)
                : usage.getPromptTokens().intValue();
        int outputTokens = usage == null || usage.getCompletionTokens() == null
                ? estimateTokens(content)
                : usage.getCompletionTokens().intValue();
        return new LlmCallResult(content, inputTokens, outputTokens);
    }

    /**
     * 估算 token 数。
     *
     * @param text 文本内容
     * @return 估算 token 数
     */
    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int cjkChars = 0;
        for (int index = 0; index < text.length(); index++) {
            if (String.valueOf(text.charAt(index)).matches("[\\u4e00-\\u9fff]")) {
                cjkChars++;
            }
        }
        int nonCjkChars = text.length() - cjkChars;
        return (int) Math.ceil(cjkChars * 1.5D + nonCjkChars * 0.4D);
    }
}
