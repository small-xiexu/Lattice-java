package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.ContributionJdbcRepository;
import com.xbk.lattice.infra.persistence.ContributionRecord;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ContributionSearchService 测试
 *
 * 职责：验证已确认用户反馈可以被中文语义问句召回
 *
 * @author xiexu
 */
class ContributionSearchServiceTests {

    /**
     * 验证纯中文追问可以命中已确认 contribution。
     */
    @Test
    void shouldMatchContributionForChineseSemanticQuestion() {
        ContributionSearchService contributionSearchService = new ContributionSearchService(
                new FakeContributionJdbcRepository(List.of(
                        new ContributionRecord(
                                UUID.randomUUID(),
                                "retry=3 是什么配置",
                                "这是用户确认的运维口径，重试间隔固定为30s。",
                                "[]",
                                "system",
                                OffsetDateTime.now()
                        )
                ))
        );

        List<QueryArticleHit> hits = contributionSearchService.search("用户确认的运维口径说重试间隔是什么", 5);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getEvidenceType()).isEqualTo(QueryEvidenceType.CONTRIBUTION);
        assertThat(hits.get(0).getContent()).contains("重试间隔固定为30s");
    }

    /**
     * Contribution 仓储替身。
     *
     * @author xiexu
     */
    private static class FakeContributionJdbcRepository extends ContributionJdbcRepository {

        private final List<ContributionRecord> records;

        /**
         * 创建 contribution 仓储替身。
         *
         * @param records 预置记录
         */
        private FakeContributionJdbcRepository(List<ContributionRecord> records) {
            super(new JdbcTemplate());
            this.records = records;
        }

        /**
         * 返回预置 contribution 记录。
         *
         * @return contribution 记录列表
         */
        @Override
        public List<ContributionRecord> findAll() {
            return records;
        }
    }
}
