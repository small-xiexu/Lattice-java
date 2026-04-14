package com.xbk.lattice;

import com.xbk.lattice.mcp.PingTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
})
class LatticeApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoadsWithMcpToolBean() {
        assertThat(applicationContext.getBean(PingTool.class)).isNotNull();
        assertThat(applicationContext.getBeansOfType(OpenAiChatModel.class)).isNotEmpty();
        assertThat(applicationContext.getBeansOfType(AnthropicChatModel.class)).isNotEmpty();
    }
}
