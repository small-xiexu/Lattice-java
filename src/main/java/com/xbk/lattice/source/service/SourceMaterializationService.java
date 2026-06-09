package com.xbk.lattice.source.service;

import com.xbk.lattice.shared.json.JsonMappers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xbk.lattice.source.config.SourceAdminProperties;
import com.xbk.lattice.source.domain.KnowledgeSource;
import com.xbk.lattice.source.domain.SourceMaterializationResult;
import com.xbk.lattice.source.domain.SourceValidationResult;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 资料源物化服务。
 *
 * 职责：负责将资料源（GIT / INTERNAL_MIRROR）物化到 staging 目录并返回物化元数据
 *
 * @author xiexu
 */
@Service
public class SourceMaterializationService {

    private static final ObjectMapper OBJECT_MAPPER = JsonMappers.moduleAwareMapper();

    /** 默认排除的目录名。 */
    private static final Set<String> DEFAULT_EXCLUDED_DIRS = Set.of(
            ".git", ".svn", ".hg",
            "target", "build", "out", ".gradle",
            "node_modules", "dist", "coverage",
            ".idea", ".vscode"
    );

    /** 默认排除的文件名（精确匹配）。 */
    private static final Set<String> DEFAULT_EXCLUDED_FILES = Set.of(
            ".DS_Store", "Thumbs.db", "Desktop.ini"
    );

    /** 默认排除的文件后缀（含点）。 */
    private static final Set<String> DEFAULT_EXCLUDED_EXTENSIONS = Set.of(
            ".class", ".jar", ".war", ".ear", ".zip", ".tar", ".gz", ".7z",
            ".tmp", ".temp", ".swp", ".bak", ".log",
            ".pem", ".p12", ".jks"
    );

    /** 默认排除的文件名前缀/精确名（密钥和敏感文件）。 */
    private static final Set<String> DEFAULT_EXCLUDED_FILENAMES = Set.of(
            ".env", "id_rsa", "id_dsa"
    );

    /** 默认纳入的文件后缀（含点）。 */
    private static final Set<String> DEFAULT_INCLUDED_EXTENSIONS = Set.of(
            ".java", ".xml", ".yml", ".yaml", ".properties", ".json",
            ".sql", ".md", ".txt", ".sh", ".js", ".ts", ".vue", ".css", ".html",
            ".gradle", ".xlsx", ".xls", ".csv", ".pdf"
    );

    /** 默认纳入的精确文件名（无后缀或特殊文件名）。 */
    private static final Set<String> DEFAULT_INCLUDED_FILENAMES = Set.of(
            "Dockerfile", ".dockerignore", ".gitignore",
            "pom.xml", "build.gradle", "settings.gradle", "gradle.properties"
    );

    private final SourceAdminProperties sourceAdminProperties;

    private final SourceCredentialService sourceCredentialService;

    /**
     * 创建资料源物化服务。
     *
     * @param sourceAdminProperties 资料源后台配置
     * @param sourceCredentialService 资料源凭据服务
     */
    public SourceMaterializationService(
            SourceAdminProperties sourceAdminProperties,
            SourceCredentialService sourceCredentialService
    ) {
        this.sourceAdminProperties = sourceAdminProperties;
        this.sourceCredentialService = sourceCredentialService;
    }

    /**
     * 校验资料源配置。
     *
     * @param source 资料源
     * @return 校验结果
     * @throws IOException IO 异常
     */
    public SourceValidationResult validate(KnowledgeSource source) throws IOException {
        JsonNode configNode = readConfig(source.getConfigJson());
        if ("GIT".equals(source.getSourceType())) {
            return validateGitSource(configNode);
        }
        if ("INTERNAL_MIRROR".equals(source.getSourceType())) {
            return validateInternalMirrorSource(configNode);
        }
        throw new IllegalArgumentException("unsupported source type for materialization: " + source.getSourceType());
    }

    /**
     * 物化资料源到 staging。
     *
     * @param source 资料源
     * @return 物化结果
     * @throws IOException IO 异常
     */
    public SourceMaterializationResult materialize(KnowledgeSource source) throws IOException {
        JsonNode configNode = readConfig(source.getConfigJson());
        Path stagingRootDir = Path.of(sourceAdminProperties.getStagingRootDir()).normalize();
        Files.createDirectories(stagingRootDir);
        Path stagingDir = stagingRootDir.resolve(source.getSourceCode() + "-" + System.currentTimeMillis()).normalize();
        if ("GIT".equals(source.getSourceType())) {
            return materializeGitSource(source, configNode, stagingDir);
        }
        if ("INTERNAL_MIRROR".equals(source.getSourceType())) {
            return materializeInternalMirrorSource(source, configNode, stagingDir);
        }
        throw new IllegalArgumentException("unsupported source type for materialization: " + source.getSourceType());
    }

    private SourceValidationResult validateGitSource(JsonNode configNode) throws IOException {
        String remoteUrl = requireText(configNode, "remoteUrl");
        String branch = textOrDefault(configNode.path("branch").asText(), "main");
        Collection<Ref> refs;
        try {
            org.eclipse.jgit.api.LsRemoteCommand lsRemoteCommand = Git.lsRemoteRepository()
                    .setHeads(true)
                    .setRemote(remoteUrl);
            CredentialsProvider credentialsProvider = resolveCredentials(configNode.path("credentialRef").asText(null));
            if (credentialsProvider != null) {
                lsRemoteCommand.setCredentialsProvider(credentialsProvider);
            }
            refs = lsRemoteCommand.call();
        }
        catch (Exception exception) {
            throw new IOException("校验 Git 资料源失败: " + remoteUrl, exception);
        }
        String gitCommit = null;
        for (Ref ref : refs) {
            if (ref.getName().endsWith("/" + branch) && ref.getObjectId() != null) {
                gitCommit = ref.getObjectId().getName();
                break;
            }
        }
        return new SourceValidationResult(true, "GIT", "Git 资料源可访问", remoteUrl, branch, gitCommit);
    }

    private SourceMaterializationResult materializeGitSource(
            KnowledgeSource source,
            JsonNode configNode,
            Path stagingDir
    ) throws IOException {
        String remoteUrl = requireText(configNode, "remoteUrl");
        String branch = textOrDefault(configNode.path("branch").asText(null), "main");
        CredentialsProvider credentialsProvider = resolveCredentials(configNode.path("credentialRef").asText(null));
        try {
            CloneCommand cloneCommand = Git.cloneRepository()
                    .setURI(remoteUrl)
                    .setDirectory(stagingDir.toFile())
                    .setCloneAllBranches(false);
            if (StringUtils.hasText(branch)) {
                cloneCommand.setBranch("refs/heads/" + branch);
            }
            if (credentialsProvider != null) {
                cloneCommand.setCredentialsProvider(credentialsProvider);
            }
            try (Git git = cloneCommand.call()) {
                String gitCommit = git.getRepository().resolve("HEAD").getName();
                String effectiveBranch = git.getRepository().getBranch();
                ObjectNode metadataNode = OBJECT_MAPPER.createObjectNode();
                metadataNode.put("materializationType", "GIT");
                metadataNode.put("remoteUrl", remoteUrl);
                metadataNode.put("branch", effectiveBranch);
                metadataNode.put("gitCommit", gitCommit);
                metadataNode.put("materializedAt", OffsetDateTime.now().toString());
                metadataNode.put("sourceCode", source.getSourceCode());
                return new SourceMaterializationResult(stagingDir, metadataNode.toString());
            }
        }
        catch (Exception exception) {
            throw new IOException("物化 Git 资料源失败: " + remoteUrl, exception);
        }
    }

    private SourceValidationResult validateInternalMirrorSource(JsonNode configNode) throws IOException {
        String mirrorRootRef = requireText(configNode, "mirrorRootRef");
        String projectPath = requireText(configNode, "projectPath");
        resolveMirrorProjectDir(mirrorRootRef, projectPath);
        return new SourceValidationResult(
                true,
                "INTERNAL_MIRROR",
                "内部镜像源可访问",
                mirrorRootRef,
                projectPath,
                null
        );
    }

    private SourceMaterializationResult materializeInternalMirrorSource(
            KnowledgeSource source,
            JsonNode configNode,
            Path stagingDir
    ) throws IOException {
        String mirrorRootRef = requireText(configNode, "mirrorRootRef");
        String projectPath = requireText(configNode, "projectPath");
        Path projectDir = resolveMirrorProjectDir(mirrorRootRef, projectPath);
        OffsetDateTime scanStartedAt = OffsetDateTime.now();
        List<Path> collectedFiles = new ArrayList<>();
        long[] totalBytes = {0};
        int[] excludedCount = {0};
        Files.walkFileTree(projectDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String dirName = dir.getFileName().toString();
                if (DEFAULT_EXCLUDED_DIRS.contains(dirName)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!shouldIncludeMirrorFile(file)) {
                    excludedCount[0]++;
                    return FileVisitResult.CONTINUE;
                }
                collectedFiles.add(file);
                totalBytes[0] += attrs.size();
                return FileVisitResult.CONTINUE;
            }
        });
        Files.createDirectories(stagingDir);
        for (Path sourceFile : collectedFiles) {
            Path relativePath = projectDir.relativize(sourceFile);
            Path targetFile = stagingDir.resolve(relativePath.toString());
            Files.createDirectories(targetFile.getParent());
            Files.copy(sourceFile, targetFile);
        }
        OffsetDateTime scanFinishedAt = OffsetDateTime.now();
        ObjectNode metadataNode = OBJECT_MAPPER.createObjectNode();
        metadataNode.put("materializationType", "INTERNAL_MIRROR");
        metadataNode.put("mirrorRootRef", mirrorRootRef);
        metadataNode.put("projectPath", projectPath);
        metadataNode.put("resolvedPath", projectDir.toString());
        metadataNode.put("fileCount", collectedFiles.size());
        metadataNode.put("byteCount", totalBytes[0]);
        metadataNode.put("excludedCount", excludedCount[0]);
        metadataNode.put("scanStartedAt", scanStartedAt.toString());
        metadataNode.put("scanFinishedAt", scanFinishedAt.toString());
        metadataNode.put("materializedAt", OffsetDateTime.now().toString());
        metadataNode.put("sourceCode", source.getSourceCode());
        return new SourceMaterializationResult(stagingDir, metadataNode.toString());
    }

    /**
     * 解析并校验镜像项目目录。
     *
     * @param mirrorRootRef 镜像根引用名
     * @param projectPath 相对项目路径
     * @return 规范化的项目绝对路径
     * @throws IOException 路径不合法或越界时抛出
     */
    private Path resolveMirrorProjectDir(String mirrorRootRef, String projectPath) throws IOException {
        Map<String, String> mirrorRoots = sourceAdminProperties.getMirrorRoots();
        if (mirrorRoots.isEmpty()) {
            throw new IllegalArgumentException("未配置镜像根 allowlist，不允许创建 INTERNAL_MIRROR 资料源");
        }
        String mirrorRootPath = mirrorRoots.get(mirrorRootRef);
        if (mirrorRootPath == null || !StringUtils.hasText(mirrorRootPath)) {
            throw new IllegalArgumentException("镜像根引用未在 allowlist 中: " + mirrorRootRef);
        }
        Path mirrorRoot = Path.of(mirrorRootPath).toRealPath();
        if (projectPath.contains("..")) {
            throw new IllegalArgumentException("项目路径不得包含 ..: " + projectPath);
        }
        Path projectDir = mirrorRoot.resolve(projectPath).toRealPath();
        if (!projectDir.startsWith(mirrorRoot)) {
            throw new IllegalArgumentException("项目路径越界，不在镜像根范围内: " + projectPath);
        }
        if (!Files.isDirectory(projectDir)) {
            throw new IllegalArgumentException("项目路径不是目录: " + projectDir);
        }
        return projectDir;
    }

    /**
     * 判断镜像文件是否应纳入扫描。
     *
     * @param file 文件路径
     * @return true 表示纳入
     */
    private boolean shouldIncludeMirrorFile(Path file) {
        String fileName = file.getFileName().toString();
        if (DEFAULT_EXCLUDED_FILES.contains(fileName)) {
            return false;
        }
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        if (DEFAULT_EXCLUDED_FILENAMES.contains(lowerName)) {
            return false;
        }
        if (lowerName.startsWith(".env")) {
            return false;
        }
        int dotIndex = lowerName.lastIndexOf('.');
        if (dotIndex > 0) {
            String ext = lowerName.substring(dotIndex);
            if (DEFAULT_EXCLUDED_EXTENSIONS.contains(ext)) {
                return false;
            }
            return DEFAULT_INCLUDED_EXTENSIONS.contains(ext);
        }
        return DEFAULT_INCLUDED_FILENAMES.contains(fileName);
    }

    private JsonNode readConfig(String configJson) {
        if (!StringUtils.hasText(configJson)) {
            return OBJECT_MAPPER.createObjectNode();
        }
        try {
            return OBJECT_MAPPER.readTree(configJson);
        }
        catch (Exception exception) {
            throw new IllegalArgumentException("source configJson must be valid JSON", exception);
        }
    }

    private String requireText(JsonNode configNode, String fieldName) {
        String value = configNode.path(fieldName).asText(null);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private String textOrDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private CredentialsProvider resolveCredentials(String credentialRef) {
        if (!StringUtils.hasText(credentialRef)) {
            return null;
        }
        String secret = sourceCredentialService.resolveSecret(credentialRef.trim())
                .orElseThrow(() -> new IllegalArgumentException("credential not found: " + credentialRef));
        String username = "oauth2";
        String password = secret;
        if (secret.contains(":")) {
            String[] tokens = secret.split(":", 2);
            username = tokens[0];
            password = tokens[1];
        }
        return new UsernamePasswordCredentialsProvider(username, password);
    }
}
