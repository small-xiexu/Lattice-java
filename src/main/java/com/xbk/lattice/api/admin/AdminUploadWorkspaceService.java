package com.xbk.lattice.api.admin;

import com.xbk.lattice.compiler.config.CompileJobProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 管理侧上传暂存服务
 *
 * 职责：将上传的源文件保存到临时工作目录，供 compile job 使用
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
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
}
