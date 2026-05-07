package com.xbk.lattice.admin.service;

import com.xbk.lattice.compiler.config.CompileJobProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 管理侧上传暂存服务
 *
 * 职责：将上传的源文件保存到临时工作目录，供 compile job 使用
 *
 * @author xiexu
 */
@Service
public class AdminUploadWorkspaceService {

    private final CompileJobProperties compileJobProperties;

    /**
     * 创建管理侧上传暂存服务。
     *
     * @param compileJobProperties 编译作业配置
     */
    public AdminUploadWorkspaceService(CompileJobProperties compileJobProperties) {
        this.compileJobProperties = compileJobProperties;
    }

    /**
     * 保存上传文件并返回工作目录。
     *
     * @param files 上传文件
     * @return 工作目录
     * @throws IOException IO 异常
     */
    public Path save(MultipartFile[] files) throws IOException {
        Path uploadRootDir = Path.of(compileJobProperties.getUploadRootDir());
        Files.createDirectories(uploadRootDir);

        Path workspaceDir = uploadRootDir.resolve(UUID.randomUUID().toString());
        Files.createDirectories(workspaceDir);
        for (int index = 0; index < files.length; index++) {
            MultipartFile multipartFile = files[index];
            saveSingleFile(workspaceDir, multipartFile, index);
        }
        return workspaceDir;
    }

    /**
     * 保存单个上传文件。
     *
     * @param workspaceDir 工作目录
     * @param multipartFile 上传文件
     * @param index 文件序号
     * @throws IOException IO 异常
     */
    private void saveSingleFile(Path workspaceDir, MultipartFile multipartFile, int index) throws IOException {
        String originalFilename = multipartFile.getOriginalFilename();
        String cleanedPath = StringUtils.cleanPath(originalFilename == null ? "" : originalFilename);
        if (cleanedPath.isBlank()) {
            cleanedPath = "upload-" + index;
        }
        Path targetPath = workspaceDir.resolve(cleanedPath).normalize();
        if (!targetPath.startsWith(workspaceDir)) {
            throw new IllegalArgumentException("illegal upload path: " + cleanedPath);
        }
        Path parentPath = targetPath.getParent();
        if (parentPath != null) {
            Files.createDirectories(parentPath);
        }
        try (InputStream inputStream = multipartFile.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 判断路径是否属于管理侧上传工作目录。
     *
     * @param sourceDir 源目录字符串
     * @return 是否属于上传工作目录
     */
    public boolean isUploadWorkspace(String sourceDir) {
        if (!StringUtils.hasText(sourceDir)) {
            return false;
        }
        Path uploadRootDir = Path.of(compileJobProperties.getUploadRootDir()).normalize();
        Path workspaceDir = Path.of(sourceDir).normalize();
        return workspaceDir.startsWith(uploadRootDir);
    }

    /**
     * 列出上传工作目录中的相对文件名。
     *
     * @param sourceDir 源目录字符串
     * @return 相对文件名列表
     */
    public List<String> listRelativeFileNames(String sourceDir) {
        if (!isUploadWorkspace(sourceDir)) {
            return Collections.emptyList();
        }
        Path workspaceDir = Path.of(sourceDir).normalize();
        if (!Files.isDirectory(workspaceDir)) {
            return Collections.emptyList();
        }
        List<String> relativeFileNames = new ArrayList<String>();
        try (Stream<Path> pathStream = Files.walk(workspaceDir)) {
            pathStream.filter(Files::isRegularFile)
                    .map(path -> workspaceDir.relativize(path).toString().replace("\\", "/"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(relativeFileNames::add);
        }
        catch (IOException ex) {
            return Collections.emptyList();
        }
        return relativeFileNames;
    }
}
