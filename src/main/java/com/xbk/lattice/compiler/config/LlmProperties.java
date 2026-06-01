package com.xbk.lattice.compiler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * LLM 调用配置
 *
 * 职责：承载编译/审查模型、后台配置中心回退参数与缓存预算配置
 *
 * @author xiexu
 */
@ConfigurationProperties(prefix = "lattice.llm")
public class LlmProperties {

    /** 编译模型标识。默认 {@code "openai"}。 */
    private String compileModel = "openai";

    /** 审查模型标识。默认 {@code "anthropic"}。 */
    private String reviewerModel = "anthropic";

    /**
     * LLM 配置源模式。
     *
     * <p>默认 {@code "hybrid"}——优先使用数据库持久化配置，回退到本地 bootstrap。</p>
     */
    private String configSource = "hybrid";

    /**
     * 是否允许使用本地 bootstrap 配置兜底。
     *
     * <p>默认 {@code true}（fail-open）。数据库配置不可用时不会启动失败，
     * 以本地配置运行。生产环境建议设为 {@code false} 以避免配置漂移。</p>
     */
    private boolean bootstrapEnabled = true;

    /**
     * 密钥加密种子。
     *
     * <p><b>安全警告：</b>默认值为空，未通过环境变量设置时应用启动将 fail-fast。
     * 此值用于派生 {@code LlmSecretCryptoService} 的加密密钥。</p>
     */
    private String secretEncryptionKey = "";

    /**
     * LLM 调用预算上限（美元）。
     *
     * <p>默认 10.0。超预算后停止 LLM 调用（fail-closed），所有需要 LLM 的编译步骤中止。
     * 设为 0 表示不限制。</p>
     */
    private double budgetUsd = 10.0D;

    /** LLM 响应缓存 TTL 秒数。默认 86400（24 小时）。 */
    private long cacheTtlSeconds = 86400L;

    /** 缓存 Redis Key 前缀。默认 {@code "llm:cache:"}。 */
    private String cacheKeyPrefix = "llm:cache:";

    /**
     * 是否启用真实 LLM 审查。
     *
     * <p>默认 {@code false}（安全默认）。{@code true} 时编译流程会调用 LLM
     * 进行审查和自动修复，增加成本和延迟。</p>
     */
    private boolean reviewEnabled = false;

    /** 单次 LLM 调用的最大输入字符数。默认 64000。 */
    private int maxInputChars = 64000;

    /** Admin 密钥加密/脱敏配置。 */
    private final Admin admin = new Admin();

    /** ChatClient 渐进式迁移灰度配置。 */
    private final ChatClient chatClient = new ChatClient();

    /** 编译角色默认超时配置。 */
    private final CompileTimeout compileTimeout = new CompileTimeout();

    /** Bootstrap fallback 定价配置。 */
    private final Pricing pricing = new Pricing();

    /**
     * 获取编译模型标识。
     *
     * @return 编译模型标识
     */
    public String getCompileModel() {
        return compileModel;
    }

    /**
     * 设置编译模型标识。
     *
     * @param compileModel 编译模型标识
     */
    public void setCompileModel(String compileModel) {
        this.compileModel = compileModel;
    }

    /**
     * 获取审查模型标识。
     *
     * @return 审查模型标识
     */
    public String getReviewerModel() {
        return reviewerModel;
    }

    /**
     * 设置审查模型标识。
     *
     * @param reviewerModel 审查模型标识
     */
    public void setReviewerModel(String reviewerModel) {
        this.reviewerModel = reviewerModel;
    }

    /**
     * 获取配置源模式。
     *
     * @return 配置源模式
     */
    public String getConfigSource() {
        return configSource;
    }

    /**
     * 设置配置源模式。
     *
     * @param configSource 配置源模式
     */
    public void setConfigSource(String configSource) {
        this.configSource = configSource;
    }

    /**
     * 返回是否允许使用本地 bootstrap 配置兜底。
     *
     * @return 是否允许 bootstrap 回退
     */
    public boolean isBootstrapEnabled() {
        return bootstrapEnabled;
    }

    /**
     * 设置是否允许使用本地 bootstrap 配置兜底。
     *
     * @param bootstrapEnabled 是否允许 bootstrap 回退
     */
    public void setBootstrapEnabled(boolean bootstrapEnabled) {
        this.bootstrapEnabled = bootstrapEnabled;
    }

    /**
     * 获取密钥加密种子。
     *
     * @return 密钥加密种子
     */
    public String getSecretEncryptionKey() {
        return secretEncryptionKey;
    }

    /**
     * 设置密钥加密种子。
     *
     * @param secretEncryptionKey 密钥加密种子
     */
    public void setSecretEncryptionKey(String secretEncryptionKey) {
        this.secretEncryptionKey = secretEncryptionKey;
    }

    /**
     * 获取预算上限（美元）。
     *
     * @return 预算上限
     */
    public double getBudgetUsd() {
        return budgetUsd;
    }

    /**
     * 设置预算上限（美元）。
     *
     * @param budgetUsd 预算上限
     */
    public void setBudgetUsd(double budgetUsd) {
        this.budgetUsd = budgetUsd;
    }

    /**
     * 获取缓存 TTL 秒数。
     *
     * @return 缓存 TTL 秒数
     */
    public long getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    /**
     * 设置缓存 TTL 秒数。
     *
     * @param cacheTtlSeconds 缓存 TTL 秒数
     */
    public void setCacheTtlSeconds(long cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    /**
     * 获取缓存 key 前缀。
     *
     * @return 缓存 key 前缀
     */
    public String getCacheKeyPrefix() {
        return cacheKeyPrefix;
    }

    /**
     * 设置缓存 key 前缀。
     *
     * @param cacheKeyPrefix 缓存 key 前缀
     */
    public void setCacheKeyPrefix(String cacheKeyPrefix) {
        this.cacheKeyPrefix = cacheKeyPrefix;
    }

    /**
     * 是否启用真实审查。
     *
     * @return 是否启用真实审查
     */
    public boolean isReviewEnabled() {
        return reviewEnabled;
    }

    /**
     * 设置是否启用真实审查。
     *
     * @param reviewEnabled 是否启用真实审查
     */
    public void setReviewEnabled(boolean reviewEnabled) {
        this.reviewEnabled = reviewEnabled;
    }

    /**
     * 获取单次调用的最大输入字符数。
     *
     * @return 最大输入字符数
     */
    public int getMaxInputChars() {
        return maxInputChars;
    }

    /**
     * 设置单次调用的最大输入字符数。
     *
     * @param maxInputChars 最大输入字符数
     */
    public void setMaxInputChars(int maxInputChars) {
        this.maxInputChars = maxInputChars;
    }

    /**
     * 获取 Admin 配置。
     *
     * @return Admin 配置
     */
    public Admin getAdmin() {
        return admin;
    }

    /**
     * 获取 ChatClient 渐进式迁移配置。
     *
     * @return ChatClient 配置
     */
    public ChatClient getChatClient() {
        return chatClient;
    }

    /**
     * 获取编译角色默认超时配置。
     *
     * @return 编译角色默认超时配置
     */
    public CompileTimeout getCompileTimeout() {
        return compileTimeout;
    }

    /**
     * 获取价格配置。
     *
     * @return 价格配置
     */
    public Pricing getPricing() {
        return pricing;
    }

    /**
     * Admin 配置。
     *
     * 职责：承载密钥加密与脱敏相关开关
     *
     * @author xiexu
     */
    public static class Admin {

        /** 是否启用密钥加密存储。默认 {@code true}。 */
        private boolean encryptSecrets = true;

        /** 是否启用密钥脱敏展示。默认 {@code true}。 */
        private boolean maskSecrets = true;

        /**
         * 返回是否启用密钥加密。
         *
         * @return 是否启用密钥加密
         */
        public boolean isEncryptSecrets() {
            return encryptSecrets;
        }

        /**
         * 设置是否启用密钥加密。
         *
         * @param encryptSecrets 是否启用密钥加密
         */
        public void setEncryptSecrets(boolean encryptSecrets) {
            this.encryptSecrets = encryptSecrets;
        }

        /**
         * 返回是否启用默认脱敏。
         *
         * @return 是否启用默认脱敏
         */
        public boolean isMaskSecrets() {
            return maskSecrets;
        }

        /**
         * 设置是否启用默认脱敏。
         *
         * @param maskSecrets 是否启用默认脱敏
         */
        public void setMaskSecrets(boolean maskSecrets) {
            this.maskSecrets = maskSecrets;
        }
    }

    /**
     * ChatClient 渐进式迁移配置。
     *
     * 职责：按 purpose 控制新旧执行栈灰度，避免所有 OpenAI 路由一次性切换
     *
     * @author xiexu
     */
    public static class ChatClient {

        /** ChatClient 路径总开关。默认 {@code true}。{@code false} 时全部回退旧栈。 */
        private boolean enabled = true;

        /**
         * Query answer 灰度开关。
         *
         * <p>默认 {@code true}。{@code false} 时 answer 场景回退到旧执行栈。</p>
         */
        private boolean queryAnswerEnabled = true;

        /** Query rewrite 灰度开关。默认 {@code true}。 */
        private boolean queryRewriteEnabled = true;

        /** Query review 灰度开关。默认 {@code true}。 */
        private boolean queryReviewEnabled = true;

        /** Compile review 灰度开关。默认 {@code true}。 */
        private boolean compileReviewEnabled = true;

        /** 治理侧 JSON 处理灰度开关。默认 {@code true}。 */
        private boolean governanceJsonEnabled = true;

        /**
         * 返回是否启用 ChatClient 路径。
         *
         * @return 是否启用 ChatClient 路径
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用 ChatClient 路径。
         *
         * @param enabled 是否启用 ChatClient 路径
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 返回是否启用 Query answer 路径。
         *
         * @return 是否启用 Query answer 路径
         */
        public boolean isQueryAnswerEnabled() {
            return queryAnswerEnabled;
        }

        /**
         * 设置是否启用 Query answer 路径。
         *
         * @param queryAnswerEnabled 是否启用 Query answer 路径
         */
        public void setQueryAnswerEnabled(boolean queryAnswerEnabled) {
            this.queryAnswerEnabled = queryAnswerEnabled;
        }

        /**
         * 返回是否启用 Query rewrite 路径。
         *
         * @return 是否启用 Query rewrite 路径
         */
        public boolean isQueryRewriteEnabled() {
            return queryRewriteEnabled;
        }

        /**
         * 设置是否启用 Query rewrite 路径。
         *
         * @param queryRewriteEnabled 是否启用 Query rewrite 路径
         */
        public void setQueryRewriteEnabled(boolean queryRewriteEnabled) {
            this.queryRewriteEnabled = queryRewriteEnabled;
        }

        /**
         * 返回是否启用 Query review 路径。
         *
         * @return 是否启用 Query review 路径
         */
        public boolean isQueryReviewEnabled() {
            return queryReviewEnabled;
        }

        /**
         * 设置是否启用 Query review 路径。
         *
         * @param queryReviewEnabled 是否启用 Query review 路径
         */
        public void setQueryReviewEnabled(boolean queryReviewEnabled) {
            this.queryReviewEnabled = queryReviewEnabled;
        }

        /**
         * 返回是否启用 compile review 路径。
         *
         * @return 是否启用 compile review 路径
         */
        public boolean isCompileReviewEnabled() {
            return compileReviewEnabled;
        }

        /**
         * 设置是否启用 compile review 路径。
         *
         * @param compileReviewEnabled 是否启用 compile review 路径
         */
        public void setCompileReviewEnabled(boolean compileReviewEnabled) {
            this.compileReviewEnabled = compileReviewEnabled;
        }

        /**
         * 返回是否启用治理侧 JSON 路径。
         *
         * @return 是否启用治理侧 JSON 路径
         */
        public boolean isGovernanceJsonEnabled() {
            return governanceJsonEnabled;
        }

        /**
         * 设置是否启用治理侧 JSON 路径。
         *
         * @param governanceJsonEnabled 是否启用治理侧 JSON 路径
         */
        public void setGovernanceJsonEnabled(boolean governanceJsonEnabled) {
            this.governanceJsonEnabled = governanceJsonEnabled;
        }
    }

    /**
     * 编译角色默认超时配置。
     *
     * 职责：为 writer / reviewer / fixer 提供显式超时秒数
     *
     * @author xiexu
     */
    public static class CompileTimeout {

        /** writer 角色默认超时秒数。默认 90。 */
        private int writerSeconds = 90;

        /** reviewer 角色默认超时秒数。默认 60。 */
        private int reviewerSeconds = 60;

        /** fixer 角色默认超时秒数。默认 60。 */
        private int fixerSeconds = 60;

        /**
         * 返回 writer 默认超时秒数。
         *
         * @return writer 默认超时秒数
         */
        public int getWriterSeconds() {
            return writerSeconds;
        }

        /**
         * 设置 writer 默认超时秒数。
         *
         * @param writerSeconds writer 默认超时秒数
         */
        public void setWriterSeconds(int writerSeconds) {
            this.writerSeconds = writerSeconds;
        }

        /**
         * 返回 reviewer 默认超时秒数。
         *
         * @return reviewer 默认超时秒数
         */
        public int getReviewerSeconds() {
            return reviewerSeconds;
        }

        /**
         * 设置 reviewer 默认超时秒数。
         *
         * @param reviewerSeconds reviewer 默认超时秒数
         */
        public void setReviewerSeconds(int reviewerSeconds) {
            this.reviewerSeconds = reviewerSeconds;
        }

        /**
         * 返回 fixer 默认超时秒数。
         *
         * @return fixer 默认超时秒数
         */
        public int getFixerSeconds() {
            return fixerSeconds;
        }

        /**
         * 设置 fixer 默认超时秒数。
         *
         * @param fixerSeconds fixer 默认超时秒数
         */
        public void setFixerSeconds(int fixerSeconds) {
            this.fixerSeconds = fixerSeconds;
        }
    }

    /**
     * 定价配置。
     *
     * 职责：为 bootstrap fallback 提供不依赖 provider 名称硬编码的估算费率
     *
     * @author xiexu
     */
    public static class Pricing {

        /** 编译输入单价（每千 token）。默认 $0.0025。 */
        private BigDecimal compileInputPricePer1kTokens = new BigDecimal("0.002500");

        /** 编译输出单价（每千 token）。默认 $0.01。 */
        private BigDecimal compileOutputPricePer1kTokens = new BigDecimal("0.010000");

        /** 审查输入单价（每千 token）。默认 $0.003。 */
        private BigDecimal reviewerInputPricePer1kTokens = new BigDecimal("0.003000");

        /** 审查输出单价（每千 token）。默认 $0.015。 */
        private BigDecimal reviewerOutputPricePer1kTokens = new BigDecimal("0.015000");

        /**
         * 获取编译输入单价。
         *
         * @return 编译输入单价
         */
        public BigDecimal getCompileInputPricePer1kTokens() {
            return compileInputPricePer1kTokens;
        }

        /**
         * 设置编译输入单价。
         *
         * @param compileInputPricePer1kTokens 编译输入单价
         */
        public void setCompileInputPricePer1kTokens(BigDecimal compileInputPricePer1kTokens) {
            this.compileInputPricePer1kTokens = compileInputPricePer1kTokens;
        }

        /**
         * 获取编译输出单价。
         *
         * @return 编译输出单价
         */
        public BigDecimal getCompileOutputPricePer1kTokens() {
            return compileOutputPricePer1kTokens;
        }

        /**
         * 设置编译输出单价。
         *
         * @param compileOutputPricePer1kTokens 编译输出单价
         */
        public void setCompileOutputPricePer1kTokens(BigDecimal compileOutputPricePer1kTokens) {
            this.compileOutputPricePer1kTokens = compileOutputPricePer1kTokens;
        }

        /**
         * 获取审查输入单价。
         *
         * @return 审查输入单价
         */
        public BigDecimal getReviewerInputPricePer1kTokens() {
            return reviewerInputPricePer1kTokens;
        }

        /**
         * 设置审查输入单价。
         *
         * @param reviewerInputPricePer1kTokens 审查输入单价
         */
        public void setReviewerInputPricePer1kTokens(BigDecimal reviewerInputPricePer1kTokens) {
            this.reviewerInputPricePer1kTokens = reviewerInputPricePer1kTokens;
        }

        /**
         * 获取审查输出单价。
         *
         * @return 审查输出单价
         */
        public BigDecimal getReviewerOutputPricePer1kTokens() {
            return reviewerOutputPricePer1kTokens;
        }

        /**
         * 设置审查输出单价。
         *
         * @param reviewerOutputPricePer1kTokens 审查输出单价
         */
        public void setReviewerOutputPricePer1kTokens(BigDecimal reviewerOutputPricePer1kTokens) {
            this.reviewerOutputPricePer1kTokens = reviewerOutputPricePer1kTokens;
        }
    }
}
