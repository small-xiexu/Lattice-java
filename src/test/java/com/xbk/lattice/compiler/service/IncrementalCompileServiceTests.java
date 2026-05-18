package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.infra.persistence.ArticleChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IncrementalCompileService 测试
 *
 * 职责：验证增量编译 helper 方法行为
 *
 * @author xiexu
 */
class IncrementalCompileServiceTests {

    /**
     * 验证 metadata JSON 仅存在格式差异时，不应误判为源文件变化。
     */
    @Test
    void shouldIgnoreMetadataJsonFormattingDifferencesWhenFilteringChangedSources() {
        FakeArticleJdbcRepository articleJdbcRepository = new FakeArticleJdbcRepository();
        FakeArticleChunkJdbcRepository articleChunkJdbcRepository = new FakeArticleChunkJdbcRepository();
        FakeSourceFileJdbcRepository sourceFileJdbcRepository = new FakeSourceFileJdbcRepository();
        sourceFileJdbcRepository.upsert(new SourceFileRecord(
                null,
                1L,
                "integrations/guide.pdf",
                "integrations/guide.pdf",
                null,
                "preview",
                "pdf",
                1024L,
                "=== Page: 1 ===\ncontent",
                "{\"pageCount\": 3}",
                true,
                "integrations/guide.pdf"
        ));
        IncrementalCompileService incrementalCompileService = new IncrementalCompileService(
                createCompilerProperties(),
                null,
                null,
                null,
                new RecordingSynthesisArtifactsService(),
                articleJdbcRepository,
                articleChunkJdbcRepository,
                sourceFileJdbcRepository
        );

        List<RawSource> changedRawSources = incrementalCompileService.filterChangedRawSources(List.of(
                RawSource.parsed(
                        1L,
                        "integrations/guide.pdf",
                        "=== Page: 1 ===\ncontent",
                        "pdf",
                        1024L,
                        "{\"pageCount\":3}",
                        true,
                        "integrations/guide.pdf",
                        "document-extract",
                        "apache-pdfbox"
                )
        ));

        assertThat(changedRawSources).isEmpty();
    }

    /**
     * 创建编译配置。
     *
     * @return 编译配置
     */
    private CompilerProperties createCompilerProperties() {
        CompilerProperties compilerProperties = new CompilerProperties();
        compilerProperties.setIngestMaxChars(4096);
        compilerProperties.setBatchMaxChars(4096);
        return compilerProperties;
    }

    /**
     * 文章仓储测试替身。
     *
     * @author xiexu
     */
    private static class FakeArticleJdbcRepository extends ArticleJdbcRepository {

        private final Map<String, ArticleRecord> records = new LinkedHashMap<String, ArticleRecord>();

        private FakeArticleJdbcRepository() {
            super(null);
        }

        @Override
        public void upsert(ArticleRecord articleRecord) {
            records.put(articleRecord.getConceptId(), articleRecord);
        }

        @Override
        public Optional<ArticleRecord> findByConceptId(String conceptId) {
            return Optional.ofNullable(records.get(conceptId));
        }

        @Override
        public Optional<ArticleRecord> findByArticleKey(String articleKey) {
            return Optional.ofNullable(records.get(articleKey));
        }

        @Override
        public Optional<ArticleRecord> findBySourceIdAndConceptId(Long sourceId, String conceptId) {
            return findByConceptId(conceptId);
        }

        @Override
        public List<ArticleRecord> findAll() {
            return new ArrayList<ArticleRecord>(records.values());
        }
    }

    /**
     * 文章 chunk 仓储测试替身。
     *
     * @author xiexu
     */
    private static class FakeArticleChunkJdbcRepository extends ArticleChunkJdbcRepository {

        private final Map<String, List<String>> chunks = new LinkedHashMap<String, List<String>>();

        private FakeArticleChunkJdbcRepository() {
            super(null);
        }

        @Override
        public void replaceChunks(String conceptId, List<String> chunkTexts) {
            chunks.put(conceptId, new ArrayList<String>(chunkTexts));
        }

        @Override
        public void replaceChunks(String articleKey, String conceptId, List<String> chunkTexts) {
            chunks.put(conceptId, new ArrayList<String>(chunkTexts));
        }

        @Override
        public void replaceChunksFromContent(String conceptId, String content) {
            chunks.put(conceptId, List.of(content));
        }

        @Override
        public void replaceChunksFromContent(String articleKey, String conceptId, String content) {
            chunks.put(conceptId, List.of(content));
        }

        @Override
        public List<String> findChunkTexts(String conceptId) {
            return chunks.getOrDefault(conceptId, List.of());
        }
    }

    /**
     * 源文件仓储测试替身。
     *
     * @author xiexu
     */
    private static class FakeSourceFileJdbcRepository extends SourceFileJdbcRepository {

        private final Map<String, SourceFileRecord> recordsByPath = new LinkedHashMap<String, SourceFileRecord>();

        private final Map<String, SourceFileRecord> recordsBySourcePath = new LinkedHashMap<String, SourceFileRecord>();

        private FakeSourceFileJdbcRepository() {
            super(null);
        }

        @Override
        public SourceFileRecord upsert(SourceFileRecord sourceFileRecord) {
            recordsByPath.put(sourceFileRecord.getFilePath(), sourceFileRecord);
            if (sourceFileRecord.getRelativePath() != null) {
                recordsByPath.put(sourceFileRecord.getRelativePath(), sourceFileRecord);
            }
            if (sourceFileRecord.getSourceId() != null && sourceFileRecord.getRelativePath() != null) {
                recordsBySourcePath.put(buildSourcePathKey(sourceFileRecord.getSourceId(), sourceFileRecord.getRelativePath()), sourceFileRecord);
            }
            return sourceFileRecord;
        }

        @Override
        public Optional<SourceFileRecord> findByPath(String filePath) {
            return Optional.ofNullable(recordsByPath.get(filePath));
        }

        @Override
        public Optional<SourceFileRecord> findBySourceIdAndRelativePath(Long sourceId, String relativePath) {
            return Optional.ofNullable(recordsBySourcePath.get(buildSourcePathKey(sourceId, relativePath)));
        }

        private String buildSourcePathKey(Long sourceId, String relativePath) {
            return sourceId + "::" + relativePath;
        }
    }

    /**
     * 合成产物服务测试替身。
     *
     * @author xiexu
     */
    private static class RecordingSynthesisArtifactsService extends SynthesisArtifactsService {

        private RecordingSynthesisArtifactsService() {
            super(null, null);
        }

        @Override
        public void generateAll(List<com.xbk.lattice.compiler.domain.MergedConcept> mergedConcepts) {
        }

        @Override
        public void generateAll(String scopeId, List<com.xbk.lattice.compiler.domain.MergedConcept> mergedConcepts) {
        }
    }
}
