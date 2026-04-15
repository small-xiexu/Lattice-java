package com.xbk.lattice.compiler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.config.CompilationWalProperties;
import com.xbk.lattice.compiler.model.ConceptSection;
import com.xbk.lattice.compiler.model.MergedConcept;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RedisCompilationWalStore 集成测试
 *
 * 职责：验证真实 Redis 环境下的 WAL 暂存、过滤与 TTL 行为
 *
 * @author xiexu
 */
class RedisCompilationWalStoreIntegrationTests {

    private static final String TEST_KEY_PREFIX = "test:lattice:wal:";

    private LettuceConnectionFactory lettuceConnectionFactory;

    private StringRedisTemplate stringRedisTemplate;

    private RedisCompilationWalStore walStore;

    /**
     * 初始化真实 Redis 连接并清空测试数据库。
     */
    @BeforeEach
    void setUp() {
        lettuceConnectionFactory = new LettuceConnectionFactory("127.0.0.1", 6379);
        lettuceConnectionFactory.afterPropertiesSet();
        stringRedisTemplate = new StringRedisTemplate(lettuceConnectionFactory);
        stringRedisTemplate.afterPropertiesSet();
        stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();

        CompilationWalProperties walProperties = new CompilationWalProperties();
        walProperties.setKeyPrefix(TEST_KEY_PREFIX);
        walProperties.setTtlSeconds(120);

        walStore = new RedisCompilationWalStore(stringRedisTemplate, new ObjectMapper(), walProperties);
    }

    /**
     * 清理 Redis 测试数据并销毁连接。
     */
    @AfterEach
    void tearDown() {
        stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        lettuceConnectionFactory.destroy();
    }

    /**
     * 验证 stage 后 loadPendingConcepts 可返回全部概念。
     */
    @Test
    void shouldLoadAllConceptsAfterStage() {
        String jobId = "job-load-all";
        List<MergedConcept> concepts = buildConcepts();

        walStore.stage(jobId, concepts);
        List<MergedConcept> pending = walStore.loadPendingConcepts(jobId);

        assertThat(pending).hasSize(2);
        List<String> pendingIds = pending.stream().map(MergedConcept::getConceptId).toList();
        assertThat(pendingIds).containsExactlyInAnyOrder("payment", "fulfillment");
    }

    /**
     * 验证 markCommitted 后 loadPendingConcepts 仅返回未提交的概念。
     */
    @Test
    void shouldFilterCommittedConceptsFromPendingList() {
        String jobId = "job-filter-committed";
        List<MergedConcept> concepts = buildConcepts();

        walStore.stage(jobId, concepts);
        walStore.markCommitted(jobId, "payment");
        List<MergedConcept> pending = walStore.loadPendingConcepts(jobId);

        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getConceptId()).isEqualTo("fulfillment");
    }

    /**
     * 验证 stage 后 pending Hash 的 TTL 已设置为正数。
     */
    @Test
    void shouldSetTtlOnPendingHashAfterStage() {
        String jobId = "job-ttl";
        walStore.stage(jobId, buildConcepts());

        Long ttl = stringRedisTemplate.getExpire(TEST_KEY_PREFIX + jobId + ":p");

        assertThat(ttl).isNotNull();
        assertThat(ttl).isPositive();
        assertThat(ttl).isLessThanOrEqualTo(120L);
    }

    /**
     * 验证 markCommitted 后 committed Set 的 TTL 已设置为正数。
     */
    @Test
    void shouldSetTtlOnCommittedSetAfterMarkCommitted() {
        String jobId = "job-ttl-committed";
        walStore.stage(jobId, buildConcepts());
        walStore.markCommitted(jobId, "payment");

        Long ttl = stringRedisTemplate.getExpire(TEST_KEY_PREFIX + jobId + ":c");

        assertThat(ttl).isNotNull();
        assertThat(ttl).isPositive();
        assertThat(ttl).isLessThanOrEqualTo(120L);
    }

    /**
     * 验证 MergedConcept（含 sections）可正确序列化往返。
     */
    @Test
    void shouldPreserveConceptWithSectionsAfterRoundTrip() {
        String jobId = "job-sections";
        ConceptSection section = new ConceptSection(
                "超时配置",
                List.of("retry=3", "timeout=5000ms"),
                List.of("payment/config.md")
        );
        MergedConcept concept = new MergedConcept(
                "payment-timeout",
                "支付超时",
                "支付超时配置说明",
                List.of("payment/config.md"),
                List.of("retry=3"),
                List.of(section)
        );

        walStore.stage(jobId, List.of(concept));
        List<MergedConcept> pending = walStore.loadPendingConcepts(jobId);

        assertThat(pending).hasSize(1);
        MergedConcept loaded = pending.get(0);
        assertThat(loaded.getConceptId()).isEqualTo("payment-timeout");
        assertThat(loaded.getTitle()).isEqualTo("支付超时");
        assertThat(loaded.getDescription()).isEqualTo("支付超时配置说明");
        assertThat(loaded.getSections()).hasSize(1);
        assertThat(loaded.getSections().get(0).getHeading()).isEqualTo("超时配置");
        assertThat(loaded.getSections().get(0).getContentLines()).containsExactly("retry=3", "timeout=5000ms");
        assertThat(loaded.getSections().get(0).getSourceRefs()).containsExactly("payment/config.md");
    }

    /**
     * 构建两个测试用合并概念。
     *
     * @return 合并概念列表
     */
    private List<MergedConcept> buildConcepts() {
        MergedConcept payment = new MergedConcept(
                "payment",
                "支付服务",
                List.of("payment/order.md"),
                List.of("order-flow")
        );
        MergedConcept fulfillment = new MergedConcept(
                "fulfillment",
                "履约服务",
                List.of("fulfillment/fc.md"),
                List.of("fc-routing")
        );
        return List.of(payment, fulfillment);
    }
}
