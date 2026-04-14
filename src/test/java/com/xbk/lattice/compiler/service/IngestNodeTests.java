package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.compiler.model.RawSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IngestNode 测试
 *
 * 职责：验证文件采集、截断与跳过规则
 *
 * @author xiexu
 */
class IngestNodeTests {

    /**
     * 验证文本文件会被采集，且内容会按配置截断。
     *
     * @param tempDir 临时目录
     * @throws IOException IO 异常
     */
    @Test
    void shouldReadSupportedTextFilesAndTrimContent(@TempDir Path tempDir) throws IOException {
        Path docsDir = Files.createDirectories(tempDir.resolve("docs"));
        Files.writeString(docsDir.resolve("intro.md"), "abcdefghij", StandardCharsets.UTF_8);

        CompilerProperties properties = new CompilerProperties();
        properties.setIngestMaxChars(5);

        IngestNode ingestNode = new IngestNode(properties);
        List<RawSource> rawSources = ingestNode.ingest(tempDir);

        assertThat(rawSources).hasSize(1);
        assertThat(rawSources.get(0).getRelativePath()).isEqualTo("docs/intro.md");
        assertThat(rawSources.get(0).getContent()).isEqualTo("abcde");
        assertThat(rawSources.get(0).getFormat()).isEqualTo("md");
    }

    /**
     * 验证命中跳过规则的目录和文件不会被采集。
     *
     * @param tempDir 临时目录
     * @throws IOException IO 异常
     */
    @Test
    void shouldSkipIgnoredDirectoriesAndUnsupportedFiles(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve(".git"));
        Files.createDirectories(tempDir.resolve("target"));
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve(".git").resolve("ignored.md"), "ignored", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("target").resolve("ignored.java"), "ignored", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("src").resolve("App.java"), "class App {}", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("archive.jar"), "jar-binary", StandardCharsets.UTF_8);

        CompilerProperties properties = new CompilerProperties();
        IngestNode ingestNode = new IngestNode(properties);

        List<RawSource> rawSources = ingestNode.ingest(tempDir);

        assertThat(rawSources).hasSize(1);
        assertThat(rawSources.get(0).getRelativePath()).isEqualTo("src/App.java");
    }
}
