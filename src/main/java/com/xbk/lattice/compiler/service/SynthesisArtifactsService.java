package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.domain.MergedConcept;
import com.xbk.lattice.compiler.prompt.LatticePrompts;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 合成产物服务
 *
 * 职责：生成并落库 index / timeline / tradeoffs / gaps 四类知识库产物
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class SynthesisArtifactsService {

    private final LlmGateway llmGateway;

    private final SynthesisArtifactStore synthesisArtifactStore;

    /**
     * 创建合成产物服务。
     *
     * @param llmGateway LLM 网关
     * @param synthesisArtifactStore 合成产物存储
     */
    public SynthesisArtifactsService(LlmGateway llmGateway, SynthesisArtifactStore synthesisArtifactStore) {
        this.llmGateway = llmGateway;
        this.synthesisArtifactStore = synthesisArtifactStore;
    }

    /**
     * 生成全部合成产物。
     *
     * @param mergedConcepts 合并概念列表
     */
    public void generateAll(List<MergedConcept> mergedConcepts) {
        String conceptSummary = buildConceptSummary(mergedConcepts);
        saveArtifact("index", "Knowledge Base Index", LatticePrompts.SYSTEM_COMPILE_INDEX, conceptSummary);
        saveArtifact("timeline", "Knowledge Timeline", LatticePrompts.SYSTEM_COMPILE_TIMELINE, conceptSummary);
        saveArtifact("tradeoffs", "Knowledge Trade-offs", LatticePrompts.SYSTEM_COMPILE_TRADEOFFS, conceptSummary);
        saveArtifact("gaps", "Knowledge Gaps", LatticePrompts.SYSTEM_COMPILE_GAPS, conceptSummary);
    }

    /**
     * 保存单个合成产物。
     *
     * @param artifactType 产物类型
     * @param title 标题
     * @param systemPrompt 系统提示词
     * @param conceptSummary 概念摘要
     */
    private void saveArtifact(String artifactType, String title, String systemPrompt, String conceptSummary) {
        String content = tryGenerateArtifact(artifactType, systemPrompt, conceptSummary);
        if (content == null || content.isBlank()) {
            content = buildFallbackArtifact(title, conceptSummary);
        }
        synthesisArtifactStore.save(new SynthesisArtifactRecord(
                artifactType,
                title,
                content,
                OffsetDateTime.now()
        ));
    }

    /**
     * 尝试使用 LLM 生成产物。
     *
     * @param artifactType 产物类型
     * @param systemPrompt 系统提示词
     * @param conceptSummary 概念摘要
     * @return 生成结果；失败时返回 null
     */
    private String tryGenerateArtifact(String artifactType, String systemPrompt, String conceptSummary) {
        try {
            return llmGateway.compile("synthesis-" + artifactType, systemPrompt, conceptSummary);
        }
        catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * 构建概念摘要。
     *
     * @param mergedConcepts 合并概念列表
     * @return 概念摘要
     */
    private String buildConceptSummary(List<MergedConcept> mergedConcepts) {
        StringBuilder builder = new StringBuilder();
        for (MergedConcept mergedConcept : mergedConcepts) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("- ")
                    .append(mergedConcept.getTitle())
                    .append(": ")
                    .append(mergedConcept.getDescription());
        }
        return builder.toString();
    }

    /**
     * 构建回退产物。
     *
     * @param title 标题
     * @param conceptSummary 概念摘要
     * @return 回退产物
     */
    private String buildFallbackArtifact(String title, String conceptSummary) {
        return "# " + title + "\n\n" + conceptSummary;
    }
}
