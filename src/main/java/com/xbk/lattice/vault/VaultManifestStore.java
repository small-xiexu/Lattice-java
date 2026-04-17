package com.xbk.lattice.vault;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Vault 导出清单存储
 *
 * 职责：读写 export-manifest.json，用于幂等导出与受管文件删除
 *
 * @author xiexu
 */
public class VaultManifestStore {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 读取 manifest。
     *
     * @param manifestPath manifest 路径
     * @return manifest 数据
     * @throws IOException IO 异常
     */
    public Map<String, Object> read(Path manifestPath) throws IOException {
        if (!Files.exists(manifestPath)) {
            return new LinkedHashMap<String, Object>();
        }
        String content = Files.readString(manifestPath, StandardCharsets.UTF_8);
        try {
            return OBJECT_MAPPER.readValue(content, new TypeReference<Map<String, Object>>() {
            });
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("读取 export-manifest.json 失败", exception);
        }
    }

    /**
     * 写入 manifest。
     *
     * @param manifestPath manifest 路径
     * @param manifest manifest 数据
     * @throws IOException IO 异常
     */
    public void write(Path manifestPath, Map<String, Object> manifest) throws IOException {
        Files.createDirectories(manifestPath.getParent());
        try {
            Files.writeString(
                    manifestPath,
                    OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(manifest),
                    StandardCharsets.UTF_8
            );
        }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("写入 export-manifest.json 失败", exception);
        }
    }
}
