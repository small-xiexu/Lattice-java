package com.xbk.lattice;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresSmokeTests {

    @Test
    void postgresSupportsVectorAndFtsSmokeQueries() throws Exception {
        String url = System.getProperty("lattice.test.jdbc-url", "jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge");
        String username = System.getProperty("lattice.test.jdbc-username", "postgres");
        String password = System.getProperty("lattice.test.jdbc-password", "postgres");

        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {

            statement.execute("CREATE EXTENSION IF NOT EXISTS vector");
            statement.execute("DROP TABLE IF EXISTS codex_vector_smoke");
            statement.execute("CREATE TABLE codex_vector_smoke (id serial primary key, embedding vector(3))");
            statement.execute("INSERT INTO codex_vector_smoke (embedding) VALUES ('[1,2,3]'), ('[1,2,4]')");

            try (ResultSet vectorResult = statement.executeQuery(
                    "SELECT embedding <=> '[1,2,3]'::vector AS distance FROM codex_vector_smoke ORDER BY distance LIMIT 1")) {
                assertThat(vectorResult.next()).isTrue();
                assertThat(vectorResult.getDouble("distance")).isZero();
            }

            try (ResultSet ftsResult = statement.executeQuery(
                    "SELECT to_tsvector('simple', 'FC 退款 超时 配置')::text AS doc")) {
                assertThat(ftsResult.next()).isTrue();
                assertThat(ftsResult.getString("doc")).contains("退款").contains("配置");
            }

            try (ResultSet queryResult = statement.executeQuery(
                    "SELECT plainto_tsquery('simple', '退款 配置')::text AS query")) {
                assertThat(queryResult.next()).isTrue();
                assertThat(queryResult.getString("query")).contains("退款").contains("配置");
            }

            try (ResultSet arrayResult = statement.executeQuery(
                    "SELECT ARRAY['1210', '1310'] @> ARRAY['1210'] AS matched")) {
                assertThat(arrayResult.next()).isTrue();
                assertThat(arrayResult.getBoolean("matched")).isTrue();
            }

            statement.execute("DROP TABLE codex_vector_smoke");
        }
    }
}
