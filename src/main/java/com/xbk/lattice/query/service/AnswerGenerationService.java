package com.xbk.lattice.query.service;

import com.xbk.lattice.compiler.service.LlmGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 答案生成服务
 *
 * 职责：作为 Spring Bean 入口暴露查询答案生成能力，具体 API 与编排逻辑由父级支撑类承载
 *
 * @author xiexu
 */
@Service
public class AnswerGenerationService extends AnswerGenerationApiSupport {

    /**
     * 创建无 LLM 网关的答案生成服务。
     */
    public AnswerGenerationService() {
        super();
    }

    /**
     * 创建答案生成服务。
     *
     * @param llmGateway LLM 网关
     */
    @Autowired
    public AnswerGenerationService(LlmGateway llmGateway) {
        super(llmGateway);
    }
}
