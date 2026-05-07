package com.xbk.lattice.governance.repo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.service.SynthesisArtifactJdbcStore;
import com.xbk.lattice.compiler.service.SynthesisArtifactRecord;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ContributionJdbcRepository;
import com.xbk.lattice.infra.persistence.ContributionRecord;
import com.xbk.lattice.infra.persistence.RepoSnapshotItemRecord;
import com.xbk.lattice.infra.persistence.RepoSnapshotJdbcRepository;
import com.xbk.lattice.infra.persistence.RepoSnapshotRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 整库快照服务
 *
 * 职责：生成并查询整库级 repo snapshot
 *
 * @author xiexu
 */
@Service
public class RepoSnapshotService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final RepoSnapshotJdbcRepository repoSnapshotJdbcRepository;

    private final ArticleJdbcRepository articleJdbcRepository;

    private final ContributionJdbcRepository contributionJdbcRepository;

    private final SynthesisArtifactJdbcStore synthesisArtifactJdbcStore;

    /**
     * 创建整库快照服务。
     *
     * @param repoSnapshotJdbcRepository 整库快照仓储
     * @param articleJdbcRepository 文章仓储
     * @param contributionJdbcRepository 贡献仓储
     * @param synthesisArtifactJdbcStore 合成产物仓储
     */
    public RepoSnapshotService(
            RepoSnapshotJdbcRepository repoSnapshotJdbcRepository,
            ArticleJdbcRepository articleJdbcRepository,
            ContributionJdbcRepository contributionJdbcRepository,
            SynthesisArtifactJdbcStore synthesisArtifactJdbcStore
    ) {
        this.repoSnapshotJdbcRepository = repoSnapshotJdbcRepository;
        this.articleJdbcRepository = articleJdbcRepository;
        this.contributionJdbcRepository = contributionJdbcRepository;
        this.synthesisArtifactJdbcStore = synthesisArtifactJdbcStore;
    }

    /**
     * 生成整库快照。
     *
     * @param triggerEvent 触发事件
     * @param description 描述
     * @param gitCommit Git 提交哈希
     * @return 快照记录
     */
    @Transactional(rollbackFor = Exception.class)
    public RepoSnapshotRecord snapshot(String triggerEvent, String description, String gitCommit) {
        List<ArticleRecord> articleRecords = articleJdbcRepository.findAll();
        RepoSnapshotRecord snapshotRecord = new RepoSnapshotRecord(
                0L,
                OffsetDateTime.now(),
                triggerEvent,
                gitCommit,
                description,
                articleRecords.size()
        );
        long snapshotId = repoSnapshotJdbcRepository.save(snapshotRecord);
        repoSnapshotJdbcRepository.saveItems(buildItemRecords(snapshotId, articleRecords));
        return new RepoSnapshotRecord(
                snapshotId,
                snapshotRecord.getCreatedAt(),
                snapshotRecord.getTriggerEvent(),
                snapshotRecord.getGitCommit(),
                snapshotRecord.getDescription(),
                snapshotRecord.getArticleCount()
        );
    }

    /**
     * 查询最近整库快照历史。
     *
     * @param limit 返回数量
     * @return 历史报告
     */
    public RepoHistoryReport history(int limit) {
        return new RepoHistoryReport(repoSnapshotJdbcRepository.findRecent(limit));
    }

    private List<RepoSnapshotItemRecord> buildItemRecords(long snapshotId, List<ArticleRecord> articleRecords) {
        List<RepoSnapshotItemRecord> itemRecords = new ArrayList<RepoSnapshotItemRecord>();
        for (ArticleRecord articleRecord : articleRecords) {
            String payloadJson = toJson(articleRecord);
            itemRecords.add(new RepoSnapshotItemRecord(
                    0L,
                    snapshotId,
                    "article",
                    articleRecord.getConceptId(),
                    hash(payloadJson),
                    payloadJson
            ));
        }
        for (SynthesisArtifactRecord synthesisArtifactRecord : synthesisArtifactJdbcStore.findAll()) {
            String payloadJson = toJson(synthesisArtifactRecord);
            itemRecords.add(new RepoSnapshotItemRecord(
                    0L,
                    snapshotId,
                    "artifact",
                    synthesisArtifactRecord.getArtifactType(),
                    hash(payloadJson),
                    payloadJson
            ));
        }
        for (ContributionRecord contributionRecord : contributionJdbcRepository.findAll()) {
            String payloadJson = toJson(contributionRecord);
            itemRecords.add(new RepoSnapshotItemRecord(
                    0L,
                    snapshotId,
                    "contribution",
                    contributionRecord.getId().toString(),
                    hash(payloadJson),
                    payloadJson
            ));
        }
        return itemRecords;
    }

    private String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化 repo snapshot payload 失败", exception);
        }
    }

    private String hash(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte current : digest) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("计算 repo snapshot 哈希失败", exception);
        }
    }
}
