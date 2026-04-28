package com.xbk.lattice.vault.snapshot;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Vault Git 服务
 *
 * 职责：封装 Vault 目录上的 JGit 初始化、提交、历史和 diff 操作
 *
 * @author xiexu
 */
@Service
public class VaultGitService {

    /**
     * 确保 Vault 目录已初始化 Git 仓库。
     *
     * @param vaultDir Vault 目录
     * @return 是否本次新建仓库
     * @throws IOException IO 异常
     */
    public boolean ensureRepository(Path vaultDir) throws IOException {
        Path gitDir = vaultDir.resolve(".git");
        if (Files.exists(gitDir)) {
            return false;
        }
        try {
            Files.createDirectories(vaultDir);
            Git.init().setDirectory(vaultDir.toFile()).call().close();
            return true;
        }
        catch (Exception exception) {
            throw new IOException("初始化 Vault Git 仓库失败: " + vaultDir, exception);
        }
    }

    /**
     * 提交 Vault 当前工作区变更。
     *
     * @param vaultDir Vault 目录
     * @param message 提交说明
     * @return 提交哈希；无变更时返回 null
     * @throws IOException IO 异常
     */
    public String commitAll(Path vaultDir, String message) throws IOException {
        ensureRepository(vaultDir);
        try (Git git = Git.open(vaultDir.toFile())) {
            git.add().addFilepattern(".").call();
            git.add().setUpdate(true).addFilepattern(".").call();
            Status status = git.status().call();
            if (status.isClean()) {
                return null;
            }
            RevCommit revCommit = git.commit()
                    .setMessage(message)
                    .setAuthor("lattice-java", "noreply@lattice.local")
                    .call();
            return revCommit.getName();
        }
        catch (Exception exception) {
            throw new IOException("提交 Vault Git 变更失败: " + vaultDir, exception);
        }
    }

    /**
     * 查询最近 Vault Git 历史。
     *
     * @param vaultDir Vault 目录
     * @param limit 返回条数
     * @return 历史条目
     * @throws IOException IO 异常
     */
    public List<VaultHistoryItem> history(Path vaultDir, int limit) throws IOException {
        ensureRepository(vaultDir);
        int safeLimit = Math.max(1, limit);
        try (Git git = Git.open(vaultDir.toFile())) {
            Iterable<RevCommit> commits = git.log().setMaxCount(safeLimit).call();
            List<VaultHistoryItem> items = new ArrayList<VaultHistoryItem>();
            for (RevCommit commit : commits) {
                OffsetDateTime committedAt = OffsetDateTime.ofInstant(
                        Instant.ofEpochSecond(commit.getCommitTime()),
                        ZoneOffset.UTC
                );
                items.add(new VaultHistoryItem(
                        commit.getName(),
                        commit.getName().substring(0, Math.min(8, commit.getName().length())),
                        commit.getFullMessage(),
                        committedAt
                ));
            }
            return items;
        }
        catch (Exception exception) {
            throw new IOException("读取 Vault Git 历史失败: " + vaultDir, exception);
        }
    }

    /**
     * 返回当前 Vault Git HEAD 提交哈希。
     *
     * @param vaultDir Vault 目录
     * @return HEAD 提交哈希；无提交时返回 null
     * @throws IOException IO 异常
     */
    public String headCommitId(Path vaultDir) throws IOException {
        ensureRepository(vaultDir);
        try (Repository repository = openRepository(vaultDir)) {
            ObjectId headObjectId = repository.resolve("HEAD");
            if (headObjectId == null) {
                return null;
            }
            return headObjectId.getName();
        }
    }

    /**
     * 将 Vault 工作树恢复到指定提交，并清理未跟踪文件。
     *
     * @param vaultDir Vault 目录
     * @param commitId 目标提交
     * @throws IOException IO 异常
     */
    public void restoreWorkTreeToCommit(Path vaultDir, String commitId) throws IOException {
        ensureRepository(vaultDir);
        if (commitId == null || commitId.isBlank()) {
            throw new IllegalArgumentException("commitId 不能为空");
        }
        try (Git git = Git.open(vaultDir.toFile())) {
            ObjectId targetCommit = git.getRepository().resolve(commitId);
            if (targetCommit == null) {
                throw new IllegalArgumentException("Vault Git commit 不存在: " + commitId);
            }
            git.reset()
                    .setMode(ResetCommand.ResetType.HARD)
                    .setRef(targetCommit.getName())
                    .call();
            git.clean()
                    .setCleanDirectories(true)
                    .setForce(true)
                    .call();
        }
        catch (Exception exception) {
            throw new IOException("恢复 Vault Git 工作树失败: " + vaultDir + ", commit=" + commitId, exception);
        }
    }

    /**
     * 计算两次提交之间的文件级差异。
     *
     * @param vaultDir Vault 目录
     * @param oldCommitId 旧提交
     * @param newCommitId 新提交
     * @return 文件级差异摘要
     * @throws IOException IO 异常
     */
    public List<VaultDiffSummary> diff(Path vaultDir, String oldCommitId, String newCommitId) throws IOException {
        ensureRepository(vaultDir);
        try (Repository repository = openRepository(vaultDir);
             RevWalk revWalk = new RevWalk(repository);
             DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            RevCommit oldCommit = revWalk.parseCommit(ObjectId.fromString(oldCommitId));
            RevCommit newCommit = revWalk.parseCommit(ObjectId.fromString(newCommitId));
            CanonicalTreeParser oldTree = new CanonicalTreeParser();
            CanonicalTreeParser newTree = new CanonicalTreeParser();
            try (ObjectReader oldReader = repository.newObjectReader();
                 ObjectReader newReader = repository.newObjectReader()) {
                oldTree.reset(oldReader, oldCommit.getTree());
                newTree.reset(newReader, newCommit.getTree());
            }
            diffFormatter.setRepository(repository);
            List<DiffEntry> diffEntries = diffFormatter.scan(oldTree, newTree);
            List<VaultDiffSummary> summaries = new ArrayList<VaultDiffSummary>();
            for (DiffEntry diffEntry : diffEntries) {
                String filePath = diffEntry.getNewPath();
                if (DiffEntry.ChangeType.DELETE == diffEntry.getChangeType()) {
                    filePath = diffEntry.getOldPath();
                }
                summaries.add(new VaultDiffSummary(filePath, diffEntry.getChangeType().name()));
            }
            return summaries;
        }
        catch (Exception exception) {
            throw new IOException("计算 Vault Git diff 失败: " + vaultDir, exception);
        }
    }

    private Repository openRepository(Path vaultDir) throws IOException {
        return new FileRepositoryBuilder()
                .setGitDir(vaultDir.resolve(".git").toFile())
                .readEnvironment()
                .findGitDir()
                .build();
    }
}
