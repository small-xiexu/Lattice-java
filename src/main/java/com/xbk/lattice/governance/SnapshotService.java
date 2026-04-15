package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.ArticleSnapshotJdbcRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 快照服务
 *
 * 职责：返回最近文章快照摘要，供治理与 MCP 查询
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class SnapshotService {

    private final ArticleSnapshotJdbcRepository articleSnapshotJdbcRepository;

    /**
     * 创建快照服务。
     *
     * @param articleSnapshotJdbcRepository 文章快照仓储
     */
    public SnapshotService(ArticleSnapshotJdbcRepository articleSnapshotJdbcRepository) {
        this.articleSnapshotJdbcRepository = articleSnapshotJdbcRepository;
    }

    /**
     * 查询最近文章快照。
     *
     * @param limit 返回数量
     * @return 快照报告
     */
    public SnapshotReport snapshot(int limit) {
        if (articleSnapshotJdbcRepository == null) {
            return new SnapshotReport(List.of());
        }
        return new SnapshotReport(articleSnapshotJdbcRepository.findRecent(limit));
    }
}
