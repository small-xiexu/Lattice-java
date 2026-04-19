package com.xbk.lattice.source.service;

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
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * 资料源物化服务。
 *
 * 职责：负责将 Git / SERVER_DIR 资料源物化到 staging 目录并返回物化元数据
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class SourceMaterializationService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

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
        if ("SERVER_DIR".equals(source.getSourceType())) {
            return validateServerDirSource(configNode);
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
        if ("SERVER_DIR".equals(source.getSourceType())) {
            return materializeServerDirSource(source, configNode, stagingDir);
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

    private SourceValidationResult validateServerDirSource(JsonNode configNode) throws IOException {
        Path serverDir = resolveAllowedServerDir(requireText(configNode, "serverDir"));
        if (!Files.isDirectory(serverDir)) {
            throw new IllegalArgumentException("serverDir is not a directory: " + serverDir);
        }
        return new SourceValidationResult(true, "SERVER_DIR", "服务器目录可访问", serverDir.toString(), null, null);
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

    private SourceMaterializationResult materializeServerDirSource(
            KnowledgeSource source,
            JsonNode configNode,
            Path stagingDir
    ) throws IOException {
        Path serverDir = resolveAllowedServerDir(requireText(configNode, "serverDir"));
        Files.createDirectories(stagingDir);
        copyDirectory(serverDir, stagingDir);
        ObjectNode metadataNode = OBJECT_MAPPER.createObjectNode();
        metadataNode.put("materializationType", "SERVER_DIR");
        metadataNode.put("serverDir", serverDir.toString());
        metadataNode.put("materializedAt", OffsetDateTime.now().toString());
        metadataNode.put("sourceCode", source.getSourceCode());
        return new SourceMaterializationResult(stagingDir, metadataNode.toString());
    }

    private void copyDirectory(Path sourceDir, Path targetDir) throws IOException {
        try (Stream<Path> pathStream = Files.walk(sourceDir)) {
            for (Path current : (Iterable<Path>) pathStream::iterator) {
                Path relativePath = sourceDir.relativize(current);
                Path targetPath = targetDir.resolve(relativePath.toString()).normalize();
                if (Files.isDirectory(current)) {
                    Files.createDirectories(targetPath);
                    continue;
                }
                Path parent = targetPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(current, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private Path resolveAllowedServerDir(String serverDir) throws IOException {
        Path normalizedTarget = Path.of(serverDir).toRealPath().normalize();
        List<String> allowedServerDirs = sourceAdminProperties.getAllowedServerDirs();
        if (allowedServerDirs == null || allowedServerDirs.isEmpty()) {
            throw new IllegalStateException("allowedServerDirs is empty");
        }
        for (String allowedServerDir : allowedServerDirs) {
            if (!StringUtils.hasText(allowedServerDir)) {
                continue;
            }
            Path normalizedAllowedDir = Path.of(allowedServerDir).toRealPath().normalize();
            if (normalizedTarget.startsWith(normalizedAllowedDir)) {
                return normalizedTarget;
            }
        }
        throw new IllegalArgumentException("serverDir is not allowed: " + serverDir);
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
