package com.xbk.lattice.llm.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Deep Research 绑定启动检查测试
 *
 * 职责：验证启动期只提示绑定状态，运行期严格校验仍由快照服务负责
 *
 * @author xiexu
 */
class DeepResearchBindingValidatorTests {

    /**
     * 验证启动期缺少 deep_research 绑定时不再阻塞应用启动。
     */
    @Test
    void shouldNotBlockStartupWhenDeepResearchBindingsAreMissing() {
        FailingSnapshotService snapshotService = new FailingSnapshotService();
        DeepResearchBindingValidator validator = new DeepResearchBindingValidator(snapshotService);

        assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
    }

    private static final class FailingSnapshotService extends ExecutionLlmSnapshotService {

        /**
         * 创建固定失败的快照服务。
         */
        private FailingSnapshotService() {
            super(null, null, null, null, null, null);
        }

        /**
         * 模拟 deep_research 绑定缺失。
         *
         * @param scene 场景
         */
        @Override
        public void validateSceneBindings(String scene) {
            throw new IllegalStateException("deep_research scene 缺少启用中的 agent_model_bindings");
        }
    }
}
