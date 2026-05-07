package com.xbk.lattice.llm.service;

import com.xbk.lattice.compiler.config.LlmProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * LLM 密钥加解密服务
 *
 * 职责：负责后台 API Key 的 AES-GCM 加解密与脱敏展示
 *
 * @author xiexu
 */
@Service
public class LlmSecretCryptoService {

    private static final String AES_ALGORITHM = "AES";

    private static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";

    private static final int GCM_TAG_BITS = 128;

    private static final int IV_BYTES = 12;

    private final LlmProperties llmProperties;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 创建 LLM 密钥加解密服务。
     *
     * @param llmProperties LLM 配置
     */
    public LlmSecretCryptoService(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
    }

    /**
     * 加密 API Key。
     *
     * @param plainText 明文
     * @return 密文
     */
    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return "";
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            SecretKeySpec secretKey = new SecretKeySpec(resolveSecretKey(), AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, payload, iv.length, encryptedBytes.length);
            return Base64.getEncoder().encodeToString(payload);
        }
        catch (Exception exception) {
            throw new IllegalStateException("Failed to encrypt llm secret", exception);
        }
    }

    /**
     * 解密 API Key。
     *
     * @param cipherText 密文
     * @return 明文
     */
    public String decrypt(String cipherText) {
        if (!StringUtils.hasText(cipherText)) {
            return "";
        }
        try {
            byte[] payload = Base64.getDecoder().decode(cipherText);
            if (payload.length <= IV_BYTES) {
                throw new IllegalStateException("Invalid llm secret payload");
            }
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_BYTES);
            byte[] encryptedBytes = Arrays.copyOfRange(payload, IV_BYTES, payload.length);
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            SecretKeySpec secretKey = new SecretKeySpec(resolveSecretKey(), AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plainBytes = cipher.doFinal(encryptedBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        }
        catch (Exception exception) {
            throw new IllegalStateException("Failed to decrypt llm secret", exception);
        }
    }

    /**
     * 构建默认脱敏值。
     *
     * @param plainText 明文
     * @return 脱敏值
     */
    public String mask(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return "";
        }
        String normalized = plainText.trim();
        if (normalized.length() <= 8) {
            return "****";
        }
        int prefixLength = Math.min(6, normalized.length() - 4);
        return normalized.substring(0, prefixLength) + "****" + normalized.substring(normalized.length() - 4);
    }

    private byte[] resolveSecretKey() {
        String configuredKey = llmProperties.getSecretEncryptionKey();
        if (!StringUtils.hasText(configuredKey)) {
            throw new IllegalStateException("lattice.llm.secret-encryption-key must not be blank");
        }
        byte[] rawKey = configuredKey.getBytes(StandardCharsets.UTF_8);
        if (rawKey.length == 16 || rawKey.length == 24 || rawKey.length == 32) {
            return rawKey;
        }
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return messageDigest.digest(rawKey);
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}
