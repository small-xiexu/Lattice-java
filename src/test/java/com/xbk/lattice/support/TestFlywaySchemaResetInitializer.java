package com.xbk.lattice.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试态 Flyway schema 预清理初始化器
 *
 * 职责：在 JDBC/Flyway 集成测试启动应用上下文前重建目标 schema，确保测试始终从单基线迁移起跑
 *
 * @author xiexu
 */
public class TestFlywaySchemaResetInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger log = LoggerFactory.getLogger(TestFlywaySchemaResetInitializer.class);

    /**
     * 在 Spring 上下文刷新前按测试配置重建目标 schema。
     *
     * @param applicationContext Spring 可配置应用上下文
     */
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        if (!containsJdbcProfile(environment.getActiveProfiles())) {
            return;
        }

        boolean flywayEnabled = environment.getProperty("spring.flyway.enabled", Boolean.class, false);
        String schemaProperty = environment.getProperty("spring.flyway.schemas");
        String datasourceUrl = environment.getProperty("spring.datasource.url");
        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");

        if (!flywayEnabled || !StringUtils.hasText(schemaProperty) || !StringUtils.hasText(datasourceUrl)) {
            return;
        }

        if (!datasourceUrl.startsWith("jdbc:postgresql://")) {
            return;
        }

        List<String> schemas = parseSchemas(schemaProperty);
        if (schemas.isEmpty()) {
            return;
        }

        String adminUrl = stripCurrentSchema(datasourceUrl);
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            for (String schema : schemas) {
                String normalizedSchema = normalizeSchema(schema);
                statement.execute("DROP SCHEMA IF EXISTS \"" + normalizedSchema + "\" CASCADE");
                statement.execute("CREATE SCHEMA \"" + normalizedSchema + "\"");
            }
            log.info("TestFlywaySchemaResetInitializer rebuilt schemas: {}", schemas);
        }
        catch (SQLException ex) {
            throw new IllegalStateException("重建测试 Flyway schema 失败: " + schemas, ex);
        }
    }

    /**
     * 解析 `spring.flyway.schemas` 中声明的 schema 列表。
     *
     * @param schemaProperty schema 配置原文
     * @return 规范化后的 schema 列表
     */
    private List<String> parseSchemas(String schemaProperty) {
        List<String> schemas = new ArrayList<>();
        for (String item : schemaProperty.split(",")) {
            if (StringUtils.hasText(item)) {
                schemas.add(item.trim());
            }
        }
        return schemas;
    }

    /**
     * 去掉 JDBC URL 中的 `currentSchema` 参数，避免在待删除 schema 上建立连接。
     *
     * @param datasourceUrl 原始 JDBC URL
     * @return 去掉 `currentSchema` 后的 JDBC URL
     */
    private String stripCurrentSchema(String datasourceUrl) {
        int queryIndex = datasourceUrl.indexOf('?');
        if (queryIndex < 0) {
            return datasourceUrl;
        }

        String baseUrl = datasourceUrl.substring(0, queryIndex);
        String query = datasourceUrl.substring(queryIndex + 1);
        List<String> retained = new ArrayList<>();
        for (String part : query.split("&")) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            if (part.startsWith("currentSchema=")) {
                continue;
            }
            retained.add(part);
        }
        if (retained.isEmpty()) {
            return baseUrl;
        }
        return baseUrl + "?" + String.join("&", retained);
    }

    /**
     * 判断当前测试是否显式启用了 `jdbc` profile。
     *
     * @param activeProfiles 当前激活 profile 列表
     * @return 仅当存在 `jdbc` profile 时返回 `true`
     */
    private boolean containsJdbcProfile(String[] activeProfiles) {
        for (String activeProfile : activeProfiles) {
            if ("jdbc".equals(activeProfile)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验 schema 名称仅包含测试约定允许的字符。
     *
     * @param schema 原始 schema 名
     * @return 可安全拼入 SQL 的 schema 名
     */
    private String normalizeSchema(String schema) {
        if (!schema.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("非法测试 schema 名称: " + schema);
        }
        return schema;
    }
}
