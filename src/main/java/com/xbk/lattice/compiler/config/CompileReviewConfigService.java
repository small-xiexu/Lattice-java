package com.xbk.lattice.compiler.config;

import com.xbk.lattice.infra.persistence.CompileReviewConfigJdbcRepository;
import com.xbk.lattice.infra.persistence.CompileReviewConfigRecord;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * Compile 审查配置服务
 *
 * 职责：管理后台保存的 compile review 配置，并同步到运行时 CompileReviewProperties
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class CompileReviewConfigService {

    private static final String DEFAULT_SCOPE = "default";

    private final CompileReviewProperties compileReviewProperties;

    private final CompileReviewConfigJdbcRepository compileReviewConfigJdbcRepository;

    /**
     * 创建 Compile 审查配置服务。
     *
     * @param compileReviewProperties 运行时编译审查配置
     * @param compileReviewConfigJdbcRepository 审查配置仓储
     */
    public CompileReviewConfigService(
            CompileReviewProperties compileReviewProperties,
            CompileReviewConfigJdbcRepository compileReviewConfigJdbcRepository
    ) {
        this.compileReviewProperties = compileReviewProperties;
        this.compileReviewConfigJdbcRepository = compileReviewConfigJdbcRepository;
    }

    /**
     * 启动时加载持久化覆盖项。
     */
    @PostConstruct
    public synchronized void initialize() {
        Optional<CompileReviewConfigRecord> persistedConfig = compileReviewConfigJdbcRepository.findDefault();
        persistedConfig.ifPresent(this::apply);
    }

    /**
     * 返回当前有效配置。
     *
     * @return 配置状态
     */
    public synchronized CompileReviewConfigState getCurrentState() {
        Optional<CompileReviewConfigRecord> persistedConfig = compileReviewConfigJdbcRepository.findDefault();
        if (persistedConfig.isPresent()) {
            return toState(persistedConfig.orElseThrow(), "database");
        }
        return fromProperties("properties");
    }

    /**
     * 保存并立即应用配置。
     *
     * @param autoFixEnabled 是否启用自动修复
     * @param maxFixRounds 最大修复轮次
     * @param allowPersistNeedsHumanReview 是否允许需人工复核文章落库
     * @param humanReviewSeverityThreshold 人工复核严重度阈值
     * @param operator 操作人
     * @return 保存后的状态
     */
    public synchronized CompileReviewConfigState save(
            boolean autoFixEnabled,
            int maxFixRounds,
            boolean allowPersistNeedsHumanReview,
            String humanReviewSeverityThreshold,
            String operator
    ) {
        validate(maxFixRounds, humanReviewSeverityThreshold);
        Optional<CompileReviewConfigRecord> existing = compileReviewConfigJdbcRepository.findDefault();
        String normalizedOperator = resolveOperator(operator);
        CompileReviewConfigRecord saved = compileReviewConfigJdbcRepository.saveDefault(new CompileReviewConfigRecord(
                DEFAULT_SCOPE,
                autoFixEnabled,
                maxFixRounds,
                allowPersistNeedsHumanReview,
                normalizeSeverityThreshold(humanReviewSeverityThreshold),
                existing.map(CompileReviewConfigRecord::getCreatedBy).orElse(normalizedOperator),
                normalizedOperator,
                existing.map(CompileReviewConfigRecord::getCreatedAt).orElse(null),
                existing.map(CompileReviewConfigRecord::getUpdatedAt).orElse(null)
        ));
        apply(saved);
        return toState(saved, "database");
    }

    private void apply(CompileReviewConfigRecord record) {
        compileReviewProperties.setAutoFixEnabled(record.isAutoFixEnabled());
        compileReviewProperties.setMaxFixRounds(record.getMaxFixRounds());
        compileReviewProperties.setAllowPersistNeedsHumanReview(record.isAllowPersistNeedsHumanReview());
        compileReviewProperties.setHumanReviewSeverityThreshold(record.getHumanReviewSeverityThreshold());
    }

    private CompileReviewConfigState fromProperties(String configSource) {
        return new CompileReviewConfigState(
                compileReviewProperties.isAutoFixEnabled(),
                compileReviewProperties.getMaxFixRounds(),
                compileReviewProperties.isAllowPersistNeedsHumanReview(),
                normalizeSeverityThreshold(compileReviewProperties.getHumanReviewSeverityThreshold()),
                configSource,
                "",
                "",
                null,
                null
        );
    }

    private CompileReviewConfigState toState(CompileReviewConfigRecord record, String configSource) {
        return new CompileReviewConfigState(
                record.isAutoFixEnabled(),
                record.getMaxFixRounds(),
                record.isAllowPersistNeedsHumanReview(),
                normalizeSeverityThreshold(record.getHumanReviewSeverityThreshold()),
                configSource,
                record.getCreatedBy(),
                record.getUpdatedBy(),
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }

    private void validate(int maxFixRounds, String humanReviewSeverityThreshold) {
        if (maxFixRounds < 0 || maxFixRounds > 5) {
            throw new IllegalArgumentException("maxFixRounds 必须在 0 到 5 之间");
        }
        String normalizedSeverityThreshold = normalizeSeverityThreshold(humanReviewSeverityThreshold);
        if (!"HIGH".equals(normalizedSeverityThreshold)
                && !"MEDIUM".equals(normalizedSeverityThreshold)
                && !"LOW".equals(normalizedSeverityThreshold)) {
            throw new IllegalArgumentException("humanReviewSeverityThreshold 仅支持 HIGH/MEDIUM/LOW");
        }
    }

    private String normalizeSeverityThreshold(String humanReviewSeverityThreshold) {
        if (humanReviewSeverityThreshold == null || humanReviewSeverityThreshold.isBlank()) {
            return "HIGH";
        }
        return humanReviewSeverityThreshold.trim().toUpperCase(Locale.ROOT);
    }

    private String resolveOperator(String operator) {
        if (operator == null || operator.isBlank()) {
            return "system";
        }
        return operator.trim();
    }
}
