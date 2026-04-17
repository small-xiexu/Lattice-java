package com.xbk.lattice.vault.snapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VaultGitService 测试
 *
 * 职责：验证 Vault Git 仓库初始化、提交、历史和 diff 行为
 *
 * @author xiexu
 */
class VaultGitServiceTests {

    /**
     * 验证服务可初始化仓库并返回历史与差异摘要。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldInitCommitAndDiffVaultRepository(@TempDir Path tempDir) throws Exception {
        VaultGitService vaultGitService = new VaultGitService();
        Path vaultDir = tempDir.resolve("vault");
        Files.createDirectories(vaultDir);

        boolean initialized = vaultGitService.ensureRepository(vaultDir);
        Files.writeString(vaultDir.resolve("index.md"), "# Index\n", StandardCharsets.UTF_8);
        String firstCommit = vaultGitService.commitAll(vaultDir, "[lattice:manual] init");

        Files.writeString(vaultDir.resolve("index.md"), "# Index\n\nupdated\n", StandardCharsets.UTF_8);
        Files.writeString(vaultDir.resolve("gaps.md"), "# Gaps\n", StandardCharsets.UTF_8);
        String secondCommit = vaultGitService.commitAll(vaultDir, "[lattice:manual] update");

        List<VaultHistoryItem> historyItems = vaultGitService.history(vaultDir, 10);
        List<VaultDiffSummary> diffSummaries = vaultGitService.diff(vaultDir, firstCommit, secondCommit);

        assertThat(initialized).isTrue();
        assertThat(Files.exists(vaultDir.resolve(".git"))).isTrue();
        assertThat(firstCommit).isNotBlank();
        assertThat(secondCommit).isNotBlank();
        assertThat(historyItems).hasSize(2);
        assertThat(historyItems.get(0).getMessage()).contains("[lattice:manual] update");
        assertThat(diffSummaries).extracting(VaultDiffSummary::getFilePath)
                .contains("index.md", "gaps.md");
    }
}
