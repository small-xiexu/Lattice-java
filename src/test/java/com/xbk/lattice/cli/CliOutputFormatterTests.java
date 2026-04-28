package com.xbk.lattice.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.governance.repo.RepoBaselineResult;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CLI 输出格式化器测试
 *
 * 职责：验证 CLI JSON 输出能覆盖 Java Time 等对外 DTO 字段
 *
 * @author xiexu
 */
class CliOutputFormatterTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 验证 repo-baseline 结果中的 OffsetDateTime 可以被序列化为标准 JSON 字符串。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldSerializeRepoBaselineResultWithOffsetDateTime() throws Exception {
        RepoBaselineResult result = new RepoBaselineResult(
                7L,
                OffsetDateTime.parse("2026-04-28T10:15:30+08:00"),
                "manual.baseline",
                "baseline",
                "abc123",
                true,
                2,
                "/tmp/vault",
                3,
                4,
                0
        );

        String json = CliOutputFormatter.toJson(result);
        JsonNode rootNode = OBJECT_MAPPER.readTree(json);

        assertThat(rootNode.path("snapshotId").asLong()).isEqualTo(7L);
        assertThat(rootNode.path("createdAt").asText()).isEqualTo("2026-04-28T10:15:30+08:00");
    }
}
