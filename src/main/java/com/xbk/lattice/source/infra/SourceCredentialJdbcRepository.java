package com.xbk.lattice.source.infra;

import com.xbk.lattice.source.domain.SourceCredential;
import com.xbk.lattice.source.infra.mapper.SourceCredentialMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 资料源凭据 JDBC 仓储。
 *
 * 职责：负责 source_credentials 表的读写与查询
 *
 * @author xiexu
 */
@Repository
public class SourceCredentialJdbcRepository {

    private final SourceCredentialMapper sourceCredentialMapper;

    /**
     * 创建资料源凭据 JDBC 仓储。
     *
     * @param sourceCredentialMapper 资料源凭据 Mapper
     */
    public SourceCredentialJdbcRepository(SourceCredentialMapper sourceCredentialMapper) {
        this.sourceCredentialMapper = sourceCredentialMapper;
    }

    /**
     * 查询全部凭据。
     *
     * @return 凭据列表
     */
    public List<SourceCredential> findAll() {
        return sourceCredentialMapper.findAll();
    }

    /**
     * 按主键查询凭据。
     *
     * @param id 主键
     * @return 凭据
     */
    public Optional<SourceCredential> findById(Long id) {
        return Optional.ofNullable(sourceCredentialMapper.findById(id));
    }

    /**
     * 按编码查询凭据。
     *
     * @param credentialCode 凭据编码
     * @return 凭据
     */
    public Optional<SourceCredential> findByCredentialCode(String credentialCode) {
        return Optional.ofNullable(sourceCredentialMapper.findByCredentialCode(credentialCode));
    }

    /**
     * 保存凭据。
     *
     * @param sourceCredential 凭据
     * @return 已保存凭据
     */
    public SourceCredential save(SourceCredential sourceCredential) {
        if (sourceCredential.getId() == null) {
            return insert(sourceCredential);
        }
        update(sourceCredential);
        return findById(sourceCredential.getId()).orElseThrow();
    }

    private SourceCredential insert(SourceCredential sourceCredential) {
        Long id = sourceCredentialMapper.insert(sourceCredential);
        if (id == null) {
            throw new IllegalStateException("failed to insert source_credentials");
        }
        return findById(id).orElseThrow();
    }

    private void update(SourceCredential sourceCredential) {
        sourceCredentialMapper.update(sourceCredential);
    }
}
