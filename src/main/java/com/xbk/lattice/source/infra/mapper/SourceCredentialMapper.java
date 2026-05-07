package com.xbk.lattice.source.infra.mapper;

import com.xbk.lattice.source.domain.SourceCredential;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 资料源凭据 MyBatis Mapper
 *
 * 职责：通过 XML SQL 访问 source_credentials 表
 *
 * @author xiexu
 */
@Mapper
public interface SourceCredentialMapper {

    /**
     * 查询全部凭据。
     *
     * @return 凭据列表
     */
    List<SourceCredential> findAll();

    /**
     * 按主键查询凭据。
     *
     * @param id 主键
     * @return 凭据
     */
    SourceCredential findById(@Param("id") Long id);

    /**
     * 按编码查询凭据。
     *
     * @param credentialCode 凭据编码
     * @return 凭据
     */
    SourceCredential findByCredentialCode(@Param("credentialCode") String credentialCode);

    /**
     * 插入凭据。
     *
     * @param credential 凭据
     * @return 主键
     */
    Long insert(@Param("credential") SourceCredential credential);

    /**
     * 更新凭据。
     *
     * @param credential 凭据
     * @return 影响行数
     */
    int update(@Param("credential") SourceCredential credential);
}
