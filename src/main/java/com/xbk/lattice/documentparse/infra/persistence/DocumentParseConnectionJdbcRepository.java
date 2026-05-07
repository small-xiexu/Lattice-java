package com.xbk.lattice.documentparse.infra.persistence;

import com.xbk.lattice.documentparse.domain.model.ProviderConnection;
import com.xbk.lattice.documentparse.infra.persistence.mapper.DocumentParseConnectionMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 文档解析连接 JDBC 仓储
 *
 * 职责：提供 document_parse_connections 表的增删改查能力
 *
 * @author xiexu
 */
@Repository
public class DocumentParseConnectionJdbcRepository {

    private final DocumentParseConnectionMapper documentParseConnectionMapper;

    /**
     * 创建文档解析连接 JDBC 仓储。
     *
     * @param documentParseConnectionMapper 文档解析连接 Mapper
     */
    public DocumentParseConnectionJdbcRepository(DocumentParseConnectionMapper documentParseConnectionMapper) {
        this.documentParseConnectionMapper = documentParseConnectionMapper;
    }

    /**
     * 保存连接配置。
     *
     * @param connection 连接配置
     * @return 保存后的连接配置
     */
    public ProviderConnection save(ProviderConnection connection) {
        if (connection.getId() == null) {
            return insert(connection);
        }
        update(connection);
        return findById(connection.getId()).orElseThrow();
    }

    /**
     * 查询全部连接配置。
     *
     * @return 连接配置列表
     */
    public List<ProviderConnection> findAll() {
        return documentParseConnectionMapper.findAll();
    }

    /**
     * 按主键查询连接配置。
     *
     * @param id 主键
     * @return 连接配置
     */
    public Optional<ProviderConnection> findById(Long id) {
        return Optional.ofNullable(documentParseConnectionMapper.findById(id));
    }

    /**
     * 删除连接配置。
     *
     * @param id 主键
     */
    public void deleteById(Long id) {
        documentParseConnectionMapper.deleteById(id);
    }

    /**
     * 插入连接配置。
     *
     * @param connection 连接配置
     * @return 保存后的连接配置
     */
    private ProviderConnection insert(ProviderConnection connection) {
        Long id = documentParseConnectionMapper.insert(connection);
        if (id == null) {
            throw new IllegalStateException("Failed to insert document_parse_connections");
        }
        return findById(id).orElseThrow();
    }

    /**
     * 更新连接配置。
     *
     * @param connection 连接配置
     */
    private void update(ProviderConnection connection) {
        documentParseConnectionMapper.update(connection);
    }
}
