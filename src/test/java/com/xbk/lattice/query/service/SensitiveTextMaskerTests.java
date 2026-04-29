package com.xbk.lattice.query.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SensitiveTextMasker 测试
 *
 * 职责：验证问答证据中的常见密钥类赋值会被脱敏
 *
 * @author xiexu
 */
class SensitiveTextMaskerTests {

    /**
     * 验证配置赋值、JSON 字段与 Authorization 头都会脱敏。
     */
    @Test
    void shouldMaskCommonSecretAssignments() {
        String maskedText = SensitiveTextMasker.mask("""
                external.service.apiKey: plain-api-key-123456
                {"secretKey":"json-secret-123456","normal":"keep"}
                Authorization: Bearer abcdefghijklmnopqrstuvwxyz
                modelKey=sk-test-openai-123456
                """);

        assertThat(maskedText).contains("external.service.apiKey: <masked>");
        assertThat(maskedText).contains("\"secretKey\":\"<masked>\"");
        assertThat(maskedText).contains("Authorization: Bearer <masked>");
        assertThat(maskedText).contains("modelKey=<masked>");
        assertThat(maskedText).contains("\"normal\":\"keep\"");
        assertThat(maskedText).doesNotContain("plain-api-key-123456");
        assertThat(maskedText).doesNotContain("json-secret-123456");
        assertThat(maskedText).doesNotContain("abcdefghijklmnopqrstuvwxyz");
        assertThat(maskedText).doesNotContain("sk-test-openai-123456");
    }
}
