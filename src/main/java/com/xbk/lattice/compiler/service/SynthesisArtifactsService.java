package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.domain.MergedConcept;
import com.xbk.lattice.compiler.prompt.LatticePrompts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 合成产物服务
 *
 * 职责：生成并落库 index / timeline / tradeoffs / gaps 四类知识库产物
 *
 * @author xiexu
 */
@Service
@Slf4j
public class SynthesisArtifactsService {

    private static final String COMPILE_SCENE = "compile";

    private static final String WRITER_ROLE = "writer";

    private static final long NANOS_PER_MILLISECOND = 1_000_000L;

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
        generateAll(null, mergedConcepts);
    }

    /**
     * 在指定编译作业作用域下生成全部合成产物。
     *
     * @param scopeId 编译作业标识
     * @param mergedConcepts 合并概念列表
     */
    public void generateAll(String scopeId, List<MergedConcept> mergedConcepts) {
        String conceptSummary = buildConceptSummary(mergedConcepts);
        ExecutorService executorService = Executors.newFixedThreadPool(4);
        try {
            CompletableFuture<Void> indexFuture = CompletableFuture.runAsync(
                    () -> saveArtifact(
                            scopeId,
                            "index",
                            "Knowledge Base Index",
                            LatticePrompts.SYSTEM_COMPILE_INDEX,
                            conceptSummary
                    ),
                    executorService
            );
            CompletableFuture<Void> timelineFuture = CompletableFuture.runAsync(
                    () -> saveArtifact(
                            scopeId,
                            "timeline",
                            "Knowledge Timeline",
                            LatticePrompts.SYSTEM_COMPILE_TIMELINE,
                            conceptSummary
                    ),
                    executorService
            );
            CompletableFuture<Void> tradeoffsFuture = CompletableFuture.runAsync(
                    () -> saveArtifact(
                            scopeId,
                            "tradeoffs",
                            "Knowledge Trade-offs",
                            LatticePrompts.SYSTEM_COMPILE_TRADEOFFS,
                            conceptSummary
                    ),
                    executorService
            );
            CompletableFuture<Void> gapsFuture = CompletableFuture.runAsync(
                    () -> saveArtifact(
                            scopeId,
                            "gaps",
                            "Knowledge Gaps",
                            LatticePrompts.SYSTEM_COMPILE_GAPS,
                            conceptSummary
                    ),
                    executorService
            );
            CompletableFuture.allOf(indexFuture, timelineFuture, tradeoffsFuture, gapsFuture).join();
        }
        finally {
            executorService.shutdown();
        }
    }

    /**
     * 保存单个合成产物。
     *
     * @param artifactType 产物类型
     * @param title 标题
     * @param systemPrompt 系统提示词
     * @param conceptSummary 概念摘要
     */
    private void saveArtifact(
            String scopeId,
            String artifactType,
            String title,
            String systemPrompt,
            String conceptSummary
    ) {
        long startedAtNanos = System.nanoTime();
        String content = tryGenerateArtifact(scopeId, artifactType, systemPrompt, conceptSummary);
        boolean fallbackUsed = false;
        if (content == null || content.isBlank()) {
            content = buildFallbackArtifact(title, conceptSummary);
            fallbackUsed = true;
        }
        synthesisArtifactStore.save(new SynthesisArtifactRecord(
                artifactType,
                title,
                content,
                OffsetDateTime.now()
        ));
        log.info(
                "compile synthesis artifact generated. scopeId: {}, artifactType: {}, durationMs: {}, fallbackUsed: {}, conceptSummaryChars: {}",
                scopeId,
                artifactType,
                elapsedMillis(startedAtNanos),
                fallbackUsed,
                conceptSummary.length()
        );
    }

    /**
     * 尝试使用 LLM 生成产物。
     *
     * @param artifactType 产物类型
     * @param systemPrompt 系统提示词
     * @param conceptSummary 概念摘要
     * @return 生成结果；失败时返回 null
     */
    private String tryGenerateArtifact(
            String scopeId,
            String artifactType,
            String systemPrompt,
            String conceptSummary
    ) {
        try {
            if (scopeId != null && !scopeId.isBlank()) {
                return llmGateway.generateTextWithScope(
                        scopeId,
                        COMPILE_SCENE,
                        WRITER_ROLE,
                        "synthesis-" + artifactType,
                        systemPrompt,
                        conceptSummary
                );
            }
            return llmGateway.generateText(
                    COMPILE_SCENE,
                    WRITER_ROLE,
                    "synthesis-" + artifactType,
                    systemPrompt,
                    conceptSummary
            );
        }
        catch (RuntimeException ex) {
            log.warn(
                    "compile synthesis artifact llm generation failed. scopeId: {}, artifactType: {}, error: {}",
                    scopeId,
                    artifactType,
                    ex.toString()
            );
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

    /**
     * 计算耗时毫秒数。
     *
     * @param startedAtNanos 起始纳秒时间
     * @return 耗时毫秒数
     */
    private long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / NANOS_PER_MILLISECOND);
    }
}
