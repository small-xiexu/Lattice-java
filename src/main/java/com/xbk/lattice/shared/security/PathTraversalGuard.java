package com.xbk.lattice.shared.security;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * 路径穿越防护工具。
 *
 * 职责：校验用户提供的文件系统路径，阻止路径穿越攻击
 *
 * @author xiexu
 */
public final class PathTraversalGuard {

    private PathTraversalGuard() {
    }

    /**
     * 校验路径不包含穿越序列，并返回归一化后的绝对路径。
     *
     * @param userInput 用户输入的路径字符串
     * @param paramName 参数名（用于异常消息）
     * @return 归一化后的绝对路径
     * @throws IllegalArgumentException 路径为空、包含穿越序列或不合法时抛出
     */
    public static Path validateAndNormalize(String userInput, String paramName) {
        if (userInput == null || userInput.isBlank()) {
            throw new IllegalArgumentException(paramName + " 不能为空");
        }
        String trimmed = userInput.trim();
        if (trimmed.contains("..")) {
            throw new IllegalArgumentException(paramName + " 不允许包含路径穿越序列 '..'");
        }
        try {
            Path normalized = Path.of(trimmed).toAbsolutePath().normalize();
            if (normalized.toString().contains("..")) {
                throw new IllegalArgumentException(paramName + " 包含不合法的路径穿越序列");
            }
            return normalized;
        }
        catch (InvalidPathException exception) {
            throw new IllegalArgumentException(paramName + " 路径格式不合法: " + exception.getMessage());
        }
    }
}
