package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.compiler.config.CompileJobProperties;
import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.compiler.domain.MergedConcept;
import com.xbk.lattice.infra.persistence.CompileJobJdbcRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文章编译进度文案测试
 *
 * 职责：验证 Writer 生成阶段暴露给后台任务页的进度快照可读且包含当前篇数
 *
 * @author xiexu
 */
class ArticleCompileSupportProgressMessageTests {

    /**
     * 验证 Writer 生成阶段不会用内部角色文案覆盖当前篇数。
     *
     * @param sourceDir 临时资料目录
     */
    @Test
    void shouldKeepCurrentArticleIndexWhenWriterProgressIsRefreshed(@TempDir Path sourceDir) {
        RecordingCompileJobLeaseManager compileJobLeaseManager = new RecordingCompileJobLeaseManager();
        ObjectProvider<CompileJobLeaseManager> compileJobLeaseManagerProvider = compileJobLeaseManagerProvider(
                compileJobLeaseManager
        );
        ArticleCompileSupport articleCompileSupport = new ArticleCompileSupport(
                new CompilerProperties(),
                null,
                null,
                null,
                null,
                new LlmProperties(),
                null,
                compileJobLeaseManagerProvider,
                null
        );

        MergedConcept mergedConcept = new MergedConcept(
                "quality-progress-and-lessons",
                "质量打磨经验",
                List.of("docs/quality-progress-and-lessons.md"),
                List.of("质量打磨经验")
        );

        articleCompileSupport.compileDraftArticles(
                List.of(mergedConcept),
                sourceDir,
                null,
                null,
                "job-1",
                "compile"
        );

        List<ProgressSnapshot> snapshots = compileJobLeaseManager.getSnapshots();
        assertThat(snapshots).hasSize(3);
        assertThat(snapshots).extracting(ProgressSnapshot::getJobId).containsOnly("job-1");
        assertThat(snapshots).extracting(ProgressSnapshot::getCurrentStep).containsOnly("compile_new_articles");
        assertThat(snapshots).extracting(ProgressSnapshot::getProgressCurrent).containsOnly(1);
        assertThat(snapshots).extracting(ProgressSnapshot::getProgressTotal).containsOnly(1);
        assertThat(snapshots).extracting(ProgressSnapshot::getProgressMessage).containsExactly(
                "正在生成第 1 / 1 篇文章：quality-progress-and-lessons",
                "正在生成第 1 / 1 篇文章：quality-progress-and-lessons",
                "已生成第 1 / 1 篇文章：quality-progress-and-lessons"
        );
        assertThat(snapshots).extracting(ProgressSnapshot::getProgressMessage)
                .noneMatch(message -> message.contains("Writer"));
        compileJobLeaseManager.destroy();
    }

    /**
     * 创建编译作业租约管理器 Provider。
     *
     * @param compileJobLeaseManager 编译作业租约管理器
     * @return 编译作业租约管理器 Provider
     */
    private ObjectProvider<CompileJobLeaseManager> compileJobLeaseManagerProvider(
            CompileJobLeaseManager compileJobLeaseManager
    ) {
        return new FixedCompileJobLeaseManagerProvider(compileJobLeaseManager);
    }

    /**
     * 固定返回一个编译作业租约管理器的 Provider。
     *
     * 职责：避免测试引入额外 mock 依赖
     */
    private static final class FixedCompileJobLeaseManagerProvider implements ObjectProvider<CompileJobLeaseManager> {

        private final CompileJobLeaseManager compileJobLeaseManager;

        /**
         * 创建固定 Provider。
         *
         * @param compileJobLeaseManager 编译作业租约管理器
         */
        private FixedCompileJobLeaseManagerProvider(CompileJobLeaseManager compileJobLeaseManager) {
            this.compileJobLeaseManager = compileJobLeaseManager;
        }

        /**
         * 返回编译作业租约管理器。
         *
         * @return 编译作业租约管理器
         */
        @Override
        public CompileJobLeaseManager getObject() {
            return compileJobLeaseManager;
        }

        /**
         * 返回编译作业租约管理器。
         *
         * @param args 参数
         * @return 编译作业租约管理器
         */
        @Override
        public CompileJobLeaseManager getObject(Object... args) {
            return compileJobLeaseManager;
        }

        /**
         * 返回可用的编译作业租约管理器。
         *
         * @return 编译作业租约管理器
         */
        @Override
        public CompileJobLeaseManager getIfAvailable() {
            return compileJobLeaseManager;
        }

        /**
         * 返回迭代器。
         *
         * @return 编译作业租约管理器迭代器
         */
        @Override
        public Iterator<CompileJobLeaseManager> iterator() {
            return List.of(compileJobLeaseManager).iterator();
        }

        /**
         * 返回流。
         *
         * @return 编译作业租约管理器流
         */
        @Override
        public Stream<CompileJobLeaseManager> stream() {
            return Stream.of(compileJobLeaseManager);
        }

        /**
         * 返回有序流。
         *
         * @return 编译作业租约管理器流
         */
        @Override
        public Stream<CompileJobLeaseManager> orderedStream() {
            return stream();
        }
    }

    /**
     * 记录进度快照的编译作业租约管理器。
     *
     * 职责：在不访问数据库的情况下捕获 touchProgress 入参
     */
    private static final class RecordingCompileJobLeaseManager extends CompileJobLeaseManager {

        private final List<ProgressSnapshot> snapshots = new ArrayList<ProgressSnapshot>();

        /**
         * 创建记录型编译作业租约管理器。
         */
        private RecordingCompileJobLeaseManager() {
            super(new NoopCompileJobJdbcRepository(), new CompileJobProperties());
        }

        /**
         * 捕获进度快照。
         *
         * @param jobId 作业标识
         * @param currentStep 当前步骤
         * @param progressCurrent 当前进度
         * @param progressTotal 总进度
         * @param progressMessage 进度提示文案
         */
        @Override
        public void touchProgress(
                String jobId,
                String currentStep,
                int progressCurrent,
                int progressTotal,
                String progressMessage
        ) {
            snapshots.add(new ProgressSnapshot(
                    jobId,
                    currentStep,
                    progressCurrent,
                    progressTotal,
                    progressMessage
            ));
        }

        /**
         * 返回进度快照。
         *
         * @return 进度快照
         */
        private List<ProgressSnapshot> getSnapshots() {
            return snapshots;
        }
    }

    /**
     * 空操作编译作业仓储。
     *
     * 职责：满足 CompileJobLeaseManager 构造和关闭流程，不访问数据库
     */
    private static final class NoopCompileJobJdbcRepository extends CompileJobJdbcRepository {

        /**
         * 创建空操作编译作业仓储。
         */
        private NoopCompileJobJdbcRepository() {
            super(null);
        }

        /**
         * 忽略运行中作业重排。
         *
         * @param workerId worker 标识
         * @return 重排数量
         */
        @Override
        public int requeueRunningJobsOwnedByWorker(String workerId) {
            return 0;
        }

        /**
         * 返回空的过期作业列表。
         *
         * @param now 当前时间
         * @return 过期作业列表
         */
        @Override
        public List<String> findExpiredRunningJobIds(OffsetDateTime now) {
            return List.of();
        }
    }

    /**
     * 进度快照。
     *
     * 职责：承载一次 touchProgress 调用的核心参数
     */
    private static final class ProgressSnapshot {

        private final String jobId;

        private final String currentStep;

        private final int progressCurrent;

        private final int progressTotal;

        private final String progressMessage;

        /**
         * 创建进度快照。
         *
         * @param jobId 作业标识
         * @param currentStep 当前步骤
         * @param progressCurrent 当前进度
         * @param progressTotal 总进度
         * @param progressMessage 进度提示文案
         */
        private ProgressSnapshot(
                String jobId,
                String currentStep,
                int progressCurrent,
                int progressTotal,
                String progressMessage
        ) {
            this.jobId = jobId;
            this.currentStep = currentStep;
            this.progressCurrent = progressCurrent;
            this.progressTotal = progressTotal;
            this.progressMessage = progressMessage;
        }

        /**
         * 返回作业标识。
         *
         * @return 作业标识
         */
        private String getJobId() {
            return jobId;
        }

        /**
         * 返回当前步骤。
         *
         * @return 当前步骤
         */
        private String getCurrentStep() {
            return currentStep;
        }

        /**
         * 返回当前进度。
         *
         * @return 当前进度
         */
        private int getProgressCurrent() {
            return progressCurrent;
        }

        /**
         * 返回总进度。
         *
         * @return 总进度
         */
        private int getProgressTotal() {
            return progressTotal;
        }

        /**
         * 返回进度提示文案。
         *
         * @return 进度提示文案
         */
        private String getProgressMessage() {
            return progressMessage;
        }
    }
}
