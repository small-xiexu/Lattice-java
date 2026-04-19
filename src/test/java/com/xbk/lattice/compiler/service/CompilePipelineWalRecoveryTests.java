package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.infra.persistence.ArticleChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CompilePipelineService WAL 恢复测试
 *
 * 职责：验证编译提交阶段中断后可基于 jobId 重试剩余概念
 *
 * @author xiexu
 */
class CompilePipelineWalRecoveryTests {

    /**
     * 验证第二个概念提交失败后，可通过 WAL 重试剩余概念。
     *
     * @param tempDir 临时目录
     * @throws IOException IO 异常
     */
    @Test
    void shouldRetryRemainingConceptsFromWalAfterCommitFailure(@TempDir Path tempDir) throws IOException {
        CompilerProperties compilerProperties = new CompilerProperties();
        compilerProperties.setIngestMaxChars(800);
        compilerProperties.setBatchMaxChars(200);

        RecordingArticleJdbcRepository articleJdbcRepository = new RecordingArticleJdbcRepository("payment");
        NoOpArticleChunkJdbcRepository articleChunkJdbcRepository = new NoOpArticleChunkJdbcRepository();
        RecordingSourceFileJdbcRepository sourceFileJdbcRepository = new RecordingSourceFileJdbcRepository();
        FakeCompilationWalStore compilationWalStore = new FakeCompilationWalStore();
        CompilePipelineService compilePipelineService = new CompilePipelineService(
                compilerProperties,
                articleJdbcRepository,
                articleChunkJdbcRepository,
                sourceFileJdbcRepository,
                compilationWalStore
        );

        Path paymentDir = Files.createDirectories(tempDir.resolve("payment"));
        Path fulfillmentDir = Files.createDirectories(tempDir.resolve("fulfillment"));
        Files.writeString(paymentDir.resolve("order.md"), "order-flow", StandardCharsets.UTF_8);
        Files.writeString(fulfillmentDir.resolve("fc.md"), "fc-routing", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> compilePipelineService.compile(tempDir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模拟提交失败");

        String jobId = compilationWalStore.getLastJobId();
        CompileResult retryResult = compilePipelineService.retry(jobId);

        assertThat(jobId).isNotBlank();
        assertThat(retryResult.getPersistedCount()).isEqualTo(1);
        assertThat(articleJdbcRepository.getPersistedConceptIds()).containsExactly("fulfillment", "payment");
    }

    /**
     * 记录提交概念并在指定概念上失败一次的文章仓储替身。
     *
     * @author xiexu
     */
    private static class RecordingArticleJdbcRepository extends ArticleJdbcRepository {

        private final String failOnceConceptId;

        private final List<String> persistedConceptIds = new ArrayList<String>();

        private boolean failed;

        /**
         * 创建文章仓储替身。
         *
         * @param failOnceConceptId 需要失败一次的概念标识
         */
        private RecordingArticleJdbcRepository(String failOnceConceptId) {
            super(null);
            this.failOnceConceptId = failOnceConceptId;
        }

        /**
         * 记录提交概念，并在命中指定概念时失败一次。
         *
         * @param articleRecord 文章记录
         */
        @Override
        public void upsert(ArticleRecord articleRecord) {
            if (!failed && failOnceConceptId.equals(articleRecord.getConceptId())) {
                failed = true;
                throw new IllegalStateException("模拟提交失败: " + articleRecord.getConceptId());
            }
            persistedConceptIds.add(articleRecord.getConceptId());
        }

        /**
         * 获取已提交概念列表。
         *
         * @return 已提交概念列表
         */
        private List<String> getPersistedConceptIds() {
            return persistedConceptIds;
        }
    }

    /**
     * 无操作的 chunk 仓储替身。
     *
     * @author xiexu
     */
    private static class NoOpArticleChunkJdbcRepository extends ArticleChunkJdbcRepository {

        /**
         * 创建 chunk 仓储替身。
         */
        private NoOpArticleChunkJdbcRepository() {
            super(null);
        }

        /**
         * 忽略 chunk 替换。
         *
         * @param conceptId 概念标识
         * @param chunkTexts chunk 文本
         */
        @Override
        public void replaceChunks(String conceptId, List<String> chunkTexts) {
        }
    }

    /**
     * 记录源文件 upsert 的仓储替身。
     *
     * @author xiexu
     */
    private static class RecordingSourceFileJdbcRepository extends SourceFileJdbcRepository {

        /**
         * 创建源文件仓储替身。
         */
        private RecordingSourceFileJdbcRepository() {
            super(null);
        }

        /**
         * 忽略源文件落盘。
         *
         * @param sourceFileRecord 源文件记录
         */
        @Override
        public SourceFileRecord upsert(SourceFileRecord sourceFileRecord) {
            return sourceFileRecord;
        }
    }

    /**
     * 仅供测试使用的 WAL 替身。
     *
     * 职责：在单测中模拟作业暂存与剩余概念恢复
     *
     * @author xiexu
     */
    private static class FakeCompilationWalStore implements CompilationWalStore {

        private final java.util.Map<String, java.util.Map<String, WalEntry>> stagedJobs =
                new java.util.LinkedHashMap<String, java.util.Map<String, WalEntry>>();

        private String lastJobId;

        /**
         * 暂存待提交概念。
         *
         * @param jobId 作业标识
         * @param mergedConcepts 合并概念列表
         */
        @Override
        public void stage(String jobId, List<com.xbk.lattice.compiler.domain.MergedConcept> mergedConcepts) {
            java.util.Map<String, WalEntry> walEntries =
                    new java.util.LinkedHashMap<String, WalEntry>();
            for (com.xbk.lattice.compiler.domain.MergedConcept mergedConcept : mergedConcepts) {
                walEntries.put(mergedConcept.getConceptId(), new WalEntry(mergedConcept));
            }
            stagedJobs.put(jobId, walEntries);
            lastJobId = jobId;
        }

        /**
         * 读取尚未提交的概念。
         *
         * @param jobId 作业标识
         * @return 尚未提交的概念
         */
        @Override
        public List<com.xbk.lattice.compiler.domain.MergedConcept> loadPendingConcepts(String jobId) {
            java.util.Map<String, WalEntry> walEntries = stagedJobs.getOrDefault(jobId, java.util.Map.of());
            List<com.xbk.lattice.compiler.domain.MergedConcept> pendingConcepts =
                    new ArrayList<com.xbk.lattice.compiler.domain.MergedConcept>();
            for (WalEntry walEntry : walEntries.values()) {
                if (!walEntry.isCommitted()) {
                    pendingConcepts.add(walEntry.getMergedConcept());
                }
            }
            return pendingConcepts;
        }

        /**
         * 标记概念已提交。
         *
         * @param jobId 作业标识
         * @param conceptId 概念标识
         */
        @Override
        public void markCommitted(String jobId, String conceptId) {
            java.util.Map<String, WalEntry> walEntries = stagedJobs.get(jobId);
            if (walEntries == null) {
                return;
            }
            WalEntry walEntry = walEntries.get(conceptId);
            if (walEntry != null) {
                walEntry.markCommitted();
            }
        }

        /**
         * 获取最近一次暂存的 jobId。
         *
         * @return 最近一次暂存的 jobId
         */
        private String getLastJobId() {
            return lastJobId;
        }

        /**
         * 测试 WAL 条目。
         *
         * 职责：记录概念与提交状态
         *
         * @author xiexu
         */
        private static class WalEntry {

            private final com.xbk.lattice.compiler.domain.MergedConcept mergedConcept;

            private boolean committed;

            /**
             * 创建 WAL 条目。
             *
             * @param mergedConcept 合并概念
             */
            private WalEntry(com.xbk.lattice.compiler.domain.MergedConcept mergedConcept) {
                this.mergedConcept = mergedConcept;
            }

            /**
             * 获取合并概念。
             *
             * @return 合并概念
             */
            private com.xbk.lattice.compiler.domain.MergedConcept getMergedConcept() {
                return mergedConcept;
            }

            /**
             * 是否已提交。
             *
             * @return 是否已提交
             */
            private boolean isCommitted() {
                return committed;
            }

            /**
             * 标记为已提交。
             */
            private void markCommitted() {
                committed = true;
            }
        }
    }
}
