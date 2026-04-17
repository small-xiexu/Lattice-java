package com.xbk.lattice.vault.snapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.service.SynthesisArtifactJdbcStore;
import com.xbk.lattice.compiler.service.SynthesisArtifactRecord;
import com.xbk.lattice.governance.repo.RepoRollbackResult;
import com.xbk.lattice.governance.repo.RepoSnapshotService;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ContributionJdbcRepository;
import com.xbk.lattice.infra.persistence.ContributionRecord;
import com.xbk.lattice.infra.persistence.RepoSnapshotItemRecord;
import com.xbk.lattice.infra.persistence.RepoSnapshotJdbcRepository;
import com.xbk.lattice.infra.persistence.RepoSnapshotRecord;
import com.xbk.lattice.vault.VaultExportService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Vault 双轨快照协调服务
 *
 * 职责：协调 repo snapshot、Vault Git diff 与整库 rollback 闭环
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class VaultSnapshotService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final RepoSnapshotJdbcRepository repoSnapshotJdbcRepository;

    private final RepoSnapshotService repoSnapshotService;

    private final ArticleJdbcRepository articleJdbcRepository;

    private final ContributionJdbcRepository contributionJdbcRepository;

    private final SynthesisArtifactJdbcStore synthesisArtifactJdbcStore;

    private final VaultExportService vaultExportService;

    private final VaultGitService vaultGitService;

    public VaultSnapshotService(
            RepoSnapshotJdbcRepository repoSnapshotJdbcRepository,
            RepoSnapshotService repoSnapshotService,
            ArticleJdbcRepository articleJdbcRepository,
            ContributionJdbcRepository contributionJdbcRepository,
            SynthesisArtifactJdbcStore synthesisArtifactJdbcStore,
            VaultExportService vaultExportService,
            VaultGitService vaultGitService
    ) {
        this.repoSnapshotJdbcRepository = repoSnapshotJdbcRepository;
        this.repoSnapshotService = repoSnapshotService;
        this.articleJdbcRepository = articleJdbcRepository;
        this.contributionJdbcRepository = contributionJdbcRepository;
        this.synthesisArtifactJdbcStore = synthesisArtifactJdbcStore;
        this.vaultExportService = vaultExportService;
        this.vaultGitService = vaultGitService;
    }

    /**
     * 计算目标 repo snapshot 与当前 Vault HEAD 的文件差异。
     *
     * @param vaultDir Vault 目录
     * @param snapshotId 快照标识
     * @return 文件差异摘要
     * @throws IOException IO 异常
     */
    public List<VaultDiffSummary> diff(Path vaultDir, long snapshotId) throws IOException {
        RepoSnapshotRecord snapshotRecord = requireSnapshot(snapshotId);
        if (snapshotRecord.getGitCommit() == null || snapshotRecord.getGitCommit().isBlank()) {
            throw new IllegalArgumentException("目标 repo snapshot 未绑定 Vault Git commit: " + snapshotId);
        }
        String currentCommitId = vaultGitService.headCommitId(vaultDir);
        if (currentCommitId == null || currentCommitId.isBlank()) {
            return List.of();
        }
        if (currentCommitId.equals(snapshotRecord.getGitCommit())) {
            return List.of();
        }
        return vaultGitService.diff(vaultDir, snapshotRecord.getGitCommit(), currentCommitId);
    }

    /**
     * 回滚整库到目标 repo snapshot。
     *
     * @param vaultDir Vault 目录
     * @param snapshotId 目标快照
     * @return 回滚结果
     * @throws IOException IO 异常
     */
    public RepoRollbackResult rollback(Path vaultDir, long snapshotId) throws IOException {
        restoreFromSnapshot(snapshotId);
        vaultExportService.export(vaultDir);
        String commitId = vaultGitService.commitAll(
                vaultDir,
                "[lattice:rollback] restored-to-snapshot=" + snapshotId
        );
        repoSnapshotService.snapshot(
                "rollback",
                "restored-to-snapshot=" + snapshotId,
                commitId
        );
        return new RepoRollbackResult(snapshotId, OffsetDateTime.now());
    }

    /**
     * 返回目标 repo snapshot 主记录。
     *
     * @param snapshotId 快照标识
     * @return 快照主记录
     */
    public RepoSnapshotRecord getSnapshot(long snapshotId) {
        return requireSnapshot(snapshotId);
    }

    @Transactional(rollbackFor = Exception.class)
    protected void restoreFromSnapshot(long snapshotId) {
        RepoSnapshotRecord snapshotRecord = requireSnapshot(snapshotId);
        List<RepoSnapshotItemRecord> itemRecords = repoSnapshotJdbcRepository.findItemsBySnapshotId(snapshotRecord.getId());
        List<ArticleRecord> articleRecords = new ArrayList<ArticleRecord>();
        List<ContributionRecord> contributionRecords = new ArrayList<ContributionRecord>();
        List<SynthesisArtifactRecord> artifactRecords = new ArrayList<SynthesisArtifactRecord>();
        for (RepoSnapshotItemRecord itemRecord : itemRecords) {
            switch (itemRecord.getEntityType()) {
                case "article":
                    articleRecords.add(readArticle(itemRecord.getPayloadJson()));
                    break;
                case "contribution":
                    contributionRecords.add(readContribution(itemRecord.getPayloadJson()));
                    break;
                case "artifact":
                    artifactRecords.add(readArtifact(itemRecord.getPayloadJson()));
                    break;
                default:
                    throw new IllegalArgumentException("未知 repo snapshot entityType: " + itemRecord.getEntityType());
            }
        }

        articleJdbcRepository.deleteAll();
        contributionJdbcRepository.deleteAll();
        synthesisArtifactJdbcStore.deleteAll();

        for (ArticleRecord articleRecord : articleRecords) {
            articleJdbcRepository.upsert(articleRecord);
        }
        for (ContributionRecord contributionRecord : contributionRecords) {
            contributionJdbcRepository.save(contributionRecord);
        }
        for (SynthesisArtifactRecord artifactRecord : artifactRecords) {
            synthesisArtifactJdbcStore.save(artifactRecord);
        }
    }

    private RepoSnapshotRecord requireSnapshot(long snapshotId) {
        return repoSnapshotJdbcRepository.findById(snapshotId)
                .orElseThrow(() -> new IllegalArgumentException("repo snapshot 不存在: " + snapshotId));
    }

    private ArticleRecord readArticle(String payloadJson) {
        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(payloadJson);
            return new ArticleRecord(
                    rootNode.path("conceptId").asText(),
                    rootNode.path("title").asText(),
                    rootNode.path("content").asText(),
                    rootNode.path("lifecycle").asText(),
                    readOffsetDateTime(rootNode.path("compiledAt")),
                    readTextList(rootNode.path("sourcePaths")),
                    rootNode.path("metadataJson").asText("{}"),
                    rootNode.path("summary").asText(""),
                    readTextList(rootNode.path("referentialKeywords")),
                    readTextList(rootNode.path("dependsOn")),
                    readTextList(rootNode.path("related")),
                    rootNode.path("confidence").asText("medium"),
                    rootNode.path("reviewStatus").asText("pending")
            );
        }
        catch (IOException exception) {
            throw new IllegalStateException("解析 repo snapshot payload 失败: ArticleRecord", exception);
        }
    }

    private ContributionRecord readContribution(String payloadJson) {
        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(payloadJson);
            return new ContributionRecord(
                    UUID.fromString(rootNode.path("id").asText()),
                    rootNode.path("question").asText(),
                    rootNode.path("answer").asText(),
                    rootNode.path("correctionsJson").asText("{}"),
                    rootNode.path("confirmedBy").asText(),
                    readOffsetDateTime(rootNode.path("confirmedAt"))
            );
        }
        catch (IOException exception) {
            throw new IllegalStateException("解析 repo snapshot payload 失败: ContributionRecord", exception);
        }
    }

    private SynthesisArtifactRecord readArtifact(String payloadJson) {
        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(payloadJson);
            return new SynthesisArtifactRecord(
                    rootNode.path("artifactType").asText(),
                    rootNode.path("title").asText(),
                    rootNode.path("content").asText(),
                    readOffsetDateTime(rootNode.path("compiledAt"))
            );
        }
        catch (IOException exception) {
            throw new IllegalStateException("解析 repo snapshot payload 失败: SynthesisArtifactRecord", exception);
        }
    }

    private List<String> readTextList(JsonNode arrayNode) {
        List<String> values = new ArrayList<String>();
        if (arrayNode == null || !arrayNode.isArray()) {
            return values;
        }
        for (JsonNode itemNode : arrayNode) {
            values.add(itemNode.asText());
        }
        return values;
    }

    private OffsetDateTime readOffsetDateTime(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            long epochValue = node.asLong();
            if (String.valueOf(Math.abs(epochValue)).length() > 10) {
                return OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochValue), ZoneOffset.UTC);
            }
            return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochValue), ZoneOffset.UTC);
        }
        String text = node.asText();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text);
        }
        catch (Exception ignored) {
            double epochDouble = Double.parseDouble(text);
            long epochValue = (long) epochDouble;
            if (String.valueOf(Math.abs(epochValue)).length() > 10) {
                return OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochValue), ZoneOffset.UTC);
            }
            return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochValue), ZoneOffset.UTC);
        }
    }
}
