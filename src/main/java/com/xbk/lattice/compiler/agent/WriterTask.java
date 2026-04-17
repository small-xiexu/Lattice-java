package com.xbk.lattice.compiler.agent;

import com.xbk.lattice.compiler.model.MergedConcept;

import java.nio.file.Path;

/**
 * WriterAgent 输入任务
 *
 * 职责：承载单个概念草稿编译所需的上下文
 *
 * @author xiexu
 */
public class WriterTask {

    private final MergedConcept mergedConcept;

    private final Path sourceDir;

    private final String scopeId;

    private final String scene;

    /**
     * 创建 WriterAgent 输入任务。
     *
     * @param mergedConcept 合并概念
     * @param sourceDir 源目录
     */
    public WriterTask(MergedConcept mergedConcept, Path sourceDir) {
        this(mergedConcept, sourceDir, null, null);
    }

    /**
     * 创建 WriterAgent 输入任务。
     *
     * @param mergedConcept 合并概念
     * @param sourceDir 源目录
     * @param scopeId 作用域标识
     * @param scene 场景
     */
    public WriterTask(MergedConcept mergedConcept, Path sourceDir, String scopeId, String scene) {
        this.mergedConcept = mergedConcept;
        this.sourceDir = sourceDir;
        this.scopeId = scopeId;
        this.scene = scene;
    }

    /**
     * 返回合并概念。
     *
     * @return 合并概念
     */
    public MergedConcept getMergedConcept() {
        return mergedConcept;
    }

    /**
     * 返回源目录。
     *
     * @return 源目录
     */
    public Path getSourceDir() {
        return sourceDir;
    }

    /**
     * 返回作用域标识。
     *
     * @return 作用域标识
     */
    public String getScopeId() {
        return scopeId;
    }

    /**
     * 返回场景。
     *
     * @return 场景
     */
    public String getScene() {
        return scene;
    }
}
