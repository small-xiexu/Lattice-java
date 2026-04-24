package com.xbk.lattice.llm.service;

import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.llm.domain.AgentModelBinding;
import com.xbk.lattice.llm.domain.ExecutionLlmSnapshot;
import com.xbk.lattice.llm.domain.LlmModelProfile;
import com.xbk.lattice.llm.domain.LlmProviderConnection;
import com.xbk.lattice.llm.infra.AgentModelBindingJdbcRepository;
import com.xbk.lattice.llm.infra.ExecutionLlmSnapshotJdbcRepository;
import com.xbk.lattice.llm.infra.LlmModelProfileJdbcRepository;
import com.xbk.lattice.llm.infra.LlmProviderConnectionJdbcRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ExecutionLlmSnapshotService 测试
 *
 * 职责：验证快照冻结与快照路由解析的核心契约
 *
 * @author xiexu
 */
class ExecutionLlmSnapshotServiceTests {

    /**
     * 验证冻结快照时会把绑定、模型与连接信息固化进快照表。
     */
    @Test
    void shouldFreezeSnapshotsFromActiveBindings() {
        LlmProperties llmProperties = createProperties();
        StubBindingRepository bindingRepository = new StubBindingRepository();
        StubModelRepository modelRepository = new StubModelRepository();
        StubConnectionRepository connectionRepository = new StubConnectionRepository();
        StubSnapshotRepository snapshotRepository = new StubSnapshotRepository();
        LlmSecretCryptoService cryptoService = new LlmSecretCryptoService(llmProperties);
        ExecutionLlmSnapshotService snapshotService = new ExecutionLlmSnapshotService(
                llmProperties,
                bindingRepository,
                modelRepository,
                connectionRepository,
                snapshotRepository,
                cryptoService
        );
        AgentModelBinding binding = new AgentModelBinding(
                Long.valueOf(1L),
                "compile",
                "writer",
                Long.valueOf(11L),
                null,
                "compile.writer.gpt54",
                true,
                null,
                "admin",
                "admin",
                null,
                null
        );
        LlmModelProfile modelProfile = new LlmModelProfile(
                Long.valueOf(11L),
                "gpt54-compile",
                Long.valueOf(21L),
                "gpt-5.4",
                LlmModelProfile.MODEL_KIND_CHAT,
                null,
                false,
                new BigDecimal("0.2"),
                Integer.valueOf(4096),
                Integer.valueOf(300),
                new BigDecimal("0.002500"),
                new BigDecimal("0.010000"),
                "{\"top_p\":0.9}",
                true,
                null,
                "admin",
                "admin",
                null,
                null
        );
        LlmProviderConnection connection = new LlmProviderConnection(
                Long.valueOf(21L),
                "openai-main",
                "openai",
                "http://localhost:8888",
                cryptoService.encrypt("sk-writer-123456"),
                "sk-wr****3456",
                true,
                null,
                "admin",
                "admin",
                null,
                null
        );
        bindingRepository.items = List.of(binding);
        modelRepository.modelProfile = modelProfile;
        connectionRepository.connection = connection;

        List<ExecutionLlmSnapshot> snapshots = snapshotService.freezeSnapshots("compile_job", "job-1", "compile");

        assertThat(snapshotRepository.savedSnapshots).hasSize(1);
        assertThat(snapshotRepository.savedSnapshots.get(0).getRouteLabel()).isEqualTo("compile.writer.gpt54");
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).getModelName()).isEqualTo("gpt-5.4");
    }

    /**
     * 验证解析快照路由时会解密连接中的 API Key。
     */
    @Test
    void shouldResolveRouteFromSnapshotAndDecryptApiKey() {
        LlmProperties llmProperties = createProperties();
        StubBindingRepository bindingRepository = new StubBindingRepository();
        StubModelRepository modelRepository = new StubModelRepository();
        StubConnectionRepository connectionRepository = new StubConnectionRepository();
        StubSnapshotRepository snapshotRepository = new StubSnapshotRepository();
        LlmSecretCryptoService cryptoService = new LlmSecretCryptoService(llmProperties);
        ExecutionLlmSnapshotService snapshotService = new ExecutionLlmSnapshotService(
                llmProperties,
                bindingRepository,
                modelRepository,
                connectionRepository,
                snapshotRepository,
                cryptoService
        );
        ExecutionLlmSnapshot snapshot = new ExecutionLlmSnapshot(
                Long.valueOf(31L),
                "compile_job",
                "job-1",
                "compile",
                "reviewer",
                Long.valueOf(1L),
                Long.valueOf(11L),
                Long.valueOf(21L),
                "compile.reviewer.claude",
                "anthropic",
                "http://localhost:8888",
                "claude-3-7-sonnet",
                new BigDecimal("0.1"),
                Integer.valueOf(2048),
                Integer.valueOf(300),
                "{}",
                new BigDecimal("0.003000"),
                new BigDecimal("0.015000"),
                Integer.valueOf(1),
                null
        );
        String plainApiKey = "sk-review-123456";
        LlmProviderConnection connection = new LlmProviderConnection(
                Long.valueOf(21L),
                "anthropic-main",
                "anthropic",
                "http://localhost:8888",
                cryptoService.encrypt(plainApiKey),
                "sk-rev****3456",
                true,
                null,
                "admin",
                "admin",
                null,
                null
        );
        snapshotRepository.savedSnapshots = new ArrayList<ExecutionLlmSnapshot>(List.of(snapshot));
        connectionRepository.connection = connection;

        Optional<LlmRouteResolution> route = snapshotService.resolveRoute("compile_job", "job-1", "compile", "reviewer");

        assertThat(route).isPresent();
        assertThat(route.orElseThrow().getRouteLabel()).isEqualTo("compile.reviewer.claude");
        assertThat(route.orElseThrow().getApiKey()).isEqualTo(plainApiKey);
    }

    /**
     * 验证冻结 compile writer 快照时，会为缺省超时补显式默认值。
     */
    @Test
    void shouldApplyCompileWriterTimeoutDefaultWhenModelProfileTimeoutIsMissing() {
        LlmProperties llmProperties = createProperties();
        StubBindingRepository bindingRepository = new StubBindingRepository();
        StubModelRepository modelRepository = new StubModelRepository();
        StubConnectionRepository connectionRepository = new StubConnectionRepository();
        StubSnapshotRepository snapshotRepository = new StubSnapshotRepository();
        LlmSecretCryptoService cryptoService = new LlmSecretCryptoService(llmProperties);
        ExecutionLlmSnapshotService snapshotService = new ExecutionLlmSnapshotService(
                llmProperties,
                bindingRepository,
                modelRepository,
                connectionRepository,
                snapshotRepository,
                cryptoService
        );
        bindingRepository.items = List.of(new AgentModelBinding(
                Long.valueOf(1L),
                "compile",
                "writer",
                Long.valueOf(11L),
                null,
                "compile.writer.gpt54",
                true,
                null,
                "admin",
                "admin",
                null,
                null
        ));
        modelRepository.modelProfile = new LlmModelProfile(
                Long.valueOf(11L),
                "gpt54-compile",
                Long.valueOf(21L),
                "gpt-5.4",
                LlmModelProfile.MODEL_KIND_CHAT,
                null,
                false,
                new BigDecimal("0.2"),
                Integer.valueOf(4096),
                null,
                new BigDecimal("0.002500"),
                new BigDecimal("0.010000"),
                "{}",
                true,
                null,
                "admin",
                "admin",
                null,
                null
        );
        connectionRepository.connection = new LlmProviderConnection(
                Long.valueOf(21L),
                "openai-main",
                "openai",
                "http://localhost:8888",
                cryptoService.encrypt("sk-writer-123456"),
                "sk-wr****3456",
                true,
                null,
                "admin",
                "admin",
                null,
                null
        );

        List<ExecutionLlmSnapshot> snapshots = snapshotService.freezeSnapshots("compile_job", "job-1", "compile");

        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.get(0).getTimeoutSeconds()).isEqualTo(Integer.valueOf(90));
        assertThat(snapshotRepository.savedSnapshots.get(0).getTimeoutSeconds()).isEqualTo(Integer.valueOf(90));
    }

    /**
     * 验证 bootstrap compile reviewer 路由会带出显式默认超时。
     */
    @Test
    void shouldApplyCompileReviewerTimeoutDefaultToBootstrapRoute() {
        LlmProperties llmProperties = createProperties();
        ExecutionLlmSnapshotService snapshotService = new ExecutionLlmSnapshotService(
                llmProperties,
                new StubBindingRepository(),
                new StubModelRepository(),
                new StubConnectionRepository(),
                new StubSnapshotRepository(),
                new LlmSecretCryptoService(llmProperties)
        );

        LlmRouteResolution routeResolution = snapshotService.bootstrapRoute(
                ExecutionLlmSnapshotService.COMPILE_SCENE,
                ExecutionLlmSnapshotService.ROLE_REVIEWER,
                "http://writer-base",
                "writer-key",
                "http://reviewer-base",
                "reviewer-key"
        );

        assertThat(routeResolution.getTimeoutSeconds()).isEqualTo(Integer.valueOf(60));
    }

    /**
     * 验证 bootstrap compile fixer 路由会带出显式默认超时。
     */
    @Test
    void shouldApplyCompileFixerTimeoutDefaultToBootstrapRoute() {
        LlmProperties llmProperties = createProperties();
        ExecutionLlmSnapshotService snapshotService = new ExecutionLlmSnapshotService(
                llmProperties,
                new StubBindingRepository(),
                new StubModelRepository(),
                new StubConnectionRepository(),
                new StubSnapshotRepository(),
                new LlmSecretCryptoService(llmProperties)
        );

        LlmRouteResolution routeResolution = snapshotService.bootstrapRoute(
                ExecutionLlmSnapshotService.COMPILE_SCENE,
                ExecutionLlmSnapshotService.ROLE_FIXER,
                "http://writer-base",
                "writer-key",
                "http://reviewer-base",
                "reviewer-key"
        );

        assertThat(routeResolution.getTimeoutSeconds()).isEqualTo(Integer.valueOf(60));
    }

    private LlmProperties createProperties() {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setCompileModel("openai");
        llmProperties.setReviewerModel("anthropic");
        llmProperties.setConfigSource("hybrid");
        llmProperties.setBootstrapEnabled(true);
        llmProperties.setSecretEncryptionKey("test-phase8-key-0123456789abcdef");
        return llmProperties;
    }

    /**
     * Agent 绑定仓储替身。
     *
     * @author xiexu
     */
    private static class StubBindingRepository extends AgentModelBindingJdbcRepository {

        private List<AgentModelBinding> items = List.of();

        private StubBindingRepository() {
            super(null);
        }

        @Override
        public List<AgentModelBinding> findEnabledByScene(String scene) {
            return items;
        }
    }

    /**
     * 模型仓储替身。
     *
     * @author xiexu
     */
    private static class StubModelRepository extends LlmModelProfileJdbcRepository {

        private LlmModelProfile modelProfile;

        private StubModelRepository() {
            super(null);
        }

        @Override
        public Optional<LlmModelProfile> findEnabledById(Long id) {
            return Optional.ofNullable(modelProfile);
        }
    }

    /**
     * 连接仓储替身。
     *
     * @author xiexu
     */
    private static class StubConnectionRepository extends LlmProviderConnectionJdbcRepository {

        private LlmProviderConnection connection;

        private StubConnectionRepository() {
            super(null);
        }

        @Override
        public Optional<LlmProviderConnection> findEnabledById(Long id) {
            return Optional.ofNullable(connection);
        }

        @Override
        public Optional<LlmProviderConnection> findById(Long id) {
            return Optional.ofNullable(connection);
        }
    }

    /**
     * 快照仓储替身。
     *
     * @author xiexu
     */
    private static class StubSnapshotRepository extends ExecutionLlmSnapshotJdbcRepository {

        private List<ExecutionLlmSnapshot> savedSnapshots = new ArrayList<ExecutionLlmSnapshot>();

        private StubSnapshotRepository() {
            super(null);
        }

        @Override
        public void saveAll(List<ExecutionLlmSnapshot> snapshots) {
            savedSnapshots = new ArrayList<ExecutionLlmSnapshot>(snapshots);
        }

        @Override
        public List<ExecutionLlmSnapshot> findByScope(String scopeType, String scopeId, String scene) {
            List<ExecutionLlmSnapshot> matches = new ArrayList<ExecutionLlmSnapshot>();
            for (ExecutionLlmSnapshot snapshot : savedSnapshots) {
                if (scopeType.equals(snapshot.getScopeType())
                        && scopeId.equals(snapshot.getScopeId())
                        && scene.equals(snapshot.getScene())) {
                    matches.add(snapshot);
                }
            }
            return matches;
        }

        @Override
        public Optional<ExecutionLlmSnapshot> findByScopeAndRole(
                String scopeType,
                String scopeId,
                String scene,
                String agentRole
        ) {
            for (ExecutionLlmSnapshot snapshot : savedSnapshots) {
                if (scopeType.equals(snapshot.getScopeType())
                        && scopeId.equals(snapshot.getScopeId())
                        && scene.equals(snapshot.getScene())
                        && agentRole.equals(snapshot.getAgentRole())) {
                    return Optional.of(snapshot);
                }
            }
            return Optional.empty();
        }
    }
}
