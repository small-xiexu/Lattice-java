package com.xbk.lattice.source.service;

import com.xbk.lattice.llm.service.LlmSecretCryptoService;
import com.xbk.lattice.source.domain.SourceCredential;
import com.xbk.lattice.source.infra.SourceCredentialJdbcRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 资料源凭据服务。
 *
 * 职责：负责凭据的创建、查询与解密读取
 *
 * @author xiexu
 */
@Service
public class SourceCredentialService {

    private final SourceCredentialJdbcRepository sourceCredentialJdbcRepository;

    private final LlmSecretCryptoService llmSecretCryptoService;

    /**
     * 创建资料源凭据服务。
     *
     * @param sourceCredentialJdbcRepository 资料源凭据仓储
     * @param llmSecretCryptoService 密钥加解密服务
     */
    public SourceCredentialService(
            SourceCredentialJdbcRepository sourceCredentialJdbcRepository,
            LlmSecretCryptoService llmSecretCryptoService
    ) {
        this.sourceCredentialJdbcRepository = sourceCredentialJdbcRepository;
        this.llmSecretCryptoService = llmSecretCryptoService;
    }

    /**
     * 查询全部凭据。
     *
     * @return 凭据列表
     */
    public List<SourceCredential> listCredentials() {
        return sourceCredentialJdbcRepository.findAll();
    }

    /**
     * 按引用值解析凭据。
     *
     * @param credentialRef 凭据引用，可为 id 或 credentialCode
     * @return 凭据
     */
    public Optional<SourceCredential> resolveCredential(String credentialRef) {
        if (!StringUtils.hasText(credentialRef)) {
            return Optional.empty();
        }
        String normalized = credentialRef.trim();
        if (normalized.chars().allMatch(Character::isDigit)) {
            return sourceCredentialJdbcRepository.findById(Long.valueOf(normalized));
        }
        return sourceCredentialJdbcRepository.findByCredentialCode(normalized);
    }

    /**
     * 解密凭据。
     *
     * @param credentialRef 凭据引用
     * @return 明文
     */
    public Optional<String> resolveSecret(String credentialRef) {
        return resolveCredential(credentialRef)
                .filter(SourceCredential::isEnabled)
                .map(SourceCredential::getSecretCiphertext)
                .map(llmSecretCryptoService::decrypt);
    }

    /**
     * 创建或更新凭据。
     *
     * @param credentialCode 凭据编码
     * @param credentialType 凭据类型
     * @param secret 明文密钥
     * @param updatedBy 操作人
     * @return 保存后的凭据
     */
    @Transactional(rollbackFor = Exception.class)
    public SourceCredential save(
            String credentialCode,
            String credentialType,
            String secret,
            String updatedBy
    ) {
        String normalizedCode = normalizeRequired(credentialCode, "credentialCode").toLowerCase(Locale.ROOT);
        String normalizedType = normalizeRequired(credentialType, "credentialType").toUpperCase(Locale.ROOT);
        String normalizedSecret = normalizeRequired(secret, "secret");
        String operator = StringUtils.hasText(updatedBy) ? updatedBy.trim() : "admin";
        SourceCredential existing = sourceCredentialJdbcRepository.findByCredentialCode(normalizedCode).orElse(null);
        String secretCiphertext = llmSecretCryptoService.encrypt(normalizedSecret);
        String secretMask = llmSecretCryptoService.mask(normalizedSecret);
        SourceCredential target = new SourceCredential(
                existing == null ? null : existing.getId(),
                normalizedCode,
                normalizedType,
                secretCiphertext,
                secretMask,
                true,
                existing == null ? operator : existing.getCreatedBy(),
                operator,
                existing == null ? null : existing.getCreatedAt(),
                existing == null ? null : existing.getUpdatedAt()
        );
        return sourceCredentialJdbcRepository.save(target);
    }

    private String normalizeRequired(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
