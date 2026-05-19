package com.xbk.lattice.admin.service;

import com.xbk.lattice.api.admin.AdminCompileReviewSummaryResponse;
import com.xbk.lattice.compiler.service.CompileExecutionRequest;
import com.xbk.lattice.infra.persistence.CompileJobStepJdbcRepository;
import com.xbk.lattice.infra.persistence.CompileJobStepRecord;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 管理侧编译审查摘要服务。
 *
 * 职责：从 compile_job_steps 只读汇总 review 与 fix 步骤的展示级可观测信息
 *
 * @author xiexu
 */
@Service
public class AdminCompileReviewSummaryService {

    private static final String REVIEW_STEP_NAME = "review_articles";

    private static final String FIX_STEP_NAME = "fix_review_issues";

    private static final String RULE_BASED_ROUTE = "rule-based";

    private final CompileJobStepJdbcRepository compileJobStepJdbcRepository;

    /**
     * 创建管理侧编译审查摘要服务。
     *
     * @param compileJobStepJdbcRepository 编译步骤仓储
     */
    public AdminCompileReviewSummaryService(CompileJobStepJdbcRepository compileJobStepJdbcRepository) {
        this.compileJobStepJdbcRepository = compileJobStepJdbcRepository;
    }

    /**
     * 解析指定编译作业的审查摘要。
     *
     * @param jobId 编译作业标识
     * @return 审查摘要响应
     */
    public AdminCompileReviewSummaryResponse resolve(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return null;
        }
        List<CompileJobStepRecord> stepRecords = safeSteps(compileJobStepJdbcRepository.findByJobId(jobId));
        CompileJobStepRecord reviewStep = findStep(stepRecords, REVIEW_STEP_NAME);
        List<CompileJobStepRecord> fixSteps = findSteps(stepRecords, FIX_STEP_NAME);
        CompileJobStepRecord latestFixStep = fixSteps.isEmpty() ? null : fixSteps.get(fixSteps.size() - 1);
        Integer acceptedCount = extractCount(reviewStep, "acceptedCount");
        Integer pendingReviewCount = extractCount(reviewStep, "pendingReviewCount");
        Integer needsHumanReviewCount = extractCount(reviewStep, "needsHumanReviewCount");
        boolean fixStepPresent = latestFixStep != null;
        Integer fixAttemptCount = resolveFixAttemptCount(reviewStep, latestFixStep, fixSteps.size());
        return new AdminCompileReviewSummaryResponse(
                reviewStep != null,
                reviewStep == null ? null : reviewStep.getStepName(),
                reviewStep == null ? null : reviewStep.getAgentRole(),
                extractReviewMode(reviewStep),
                reviewStep == null ? null : reviewStep.getModelRoute(),
                resolveReviewModeLabel(reviewStep),
                acceptedCount,
                pendingReviewCount,
                needsHumanReviewCount,
                fixStepPresent,
                latestFixStep == null ? null : latestFixStep.getStepName(),
                fixAttemptCount,
                latestFixStep == null ? null : latestFixStep.getModelRoute(),
                resolveFixDisplayMessage(reviewStep, fixStepPresent),
                resolveReviewDisplayWarning(reviewStep)
        );
    }

    /**
     * 构建可直接放入处理任务步骤详情的展示文案。
     *
     * @param summary 审查摘要
     * @return 展示文案
     */
    public String buildStepDetail(AdminCompileReviewSummaryResponse summary) {
        if (summary == null || !summary.isReviewStepPresent()) {
            return null;
        }
        if (hasPositiveCount(summary.getNeedsHumanReviewCount())) {
            return "质量检查后需要人工确认";
        }
        if (summary.isFixStepPresent()) {
            return "已根据检查结果修正内容";
        }
        if (hasNoReviewIssue(summary)) {
            return "未发现需要修复的问题";
        }
        return "质量检查已完成";
    }

    /**
     * 空安全返回步骤列表。
     *
     * @param stepRecords 原始步骤列表
     * @return 非空步骤列表
     */
    private List<CompileJobStepRecord> safeSteps(List<CompileJobStepRecord> stepRecords) {
        return stepRecords == null ? Collections.emptyList() : stepRecords;
    }

    /**
     * 查找指定名称的最后一次步骤记录。
     *
     * @param stepRecords 步骤列表
     * @param stepName 步骤名称
     * @return 最后一次步骤记录
     */
    private CompileJobStepRecord findStep(List<CompileJobStepRecord> stepRecords, String stepName) {
        List<CompileJobStepRecord> matchedSteps = findSteps(stepRecords, stepName);
        return matchedSteps.isEmpty() ? null : matchedSteps.get(matchedSteps.size() - 1);
    }

    /**
     * 查找指定名称的全部步骤记录。
     *
     * @param stepRecords 步骤列表
     * @param stepName 步骤名称
     * @return 匹配步骤列表
     */
    private List<CompileJobStepRecord> findSteps(List<CompileJobStepRecord> stepRecords, String stepName) {
        if (stepRecords == null || stepRecords.isEmpty()) {
            return Collections.emptyList();
        }
        List<CompileJobStepRecord> matchedSteps = new ArrayList<CompileJobStepRecord>();
        for (CompileJobStepRecord stepRecord : stepRecords) {
            if (stepRecord == null || stepRecord.getStepName() == null) {
                continue;
            }
            if (stepRecord.getStepName().equalsIgnoreCase(stepName)) {
                matchedSteps.add(stepRecord);
            }
        }
        return matchedSteps;
    }

    /**
     * 解析审查模式展示文案。
     *
     * @param reviewStep 审查步骤
     * @return 审查模式展示文案
     */
    private String resolveReviewModeLabel(CompileJobStepRecord reviewStep) {
        if (reviewStep == null) {
            return "未记录审查步骤";
        }
        String requestedReviewMode = extractReviewMode(reviewStep);
        if (CompileExecutionRequest.REVIEW_MODE_LLM.equals(requestedReviewMode)) {
            return "LLM 审查";
        }
        String route = normalize(reviewStep.getModelRoute());
        if (RULE_BASED_ROUTE.equalsIgnoreCase(route)) {
            return "规则审查（不是 LLM 内容审查）";
        }
        if (route == null) {
            return "未记录审查路由";
        }
        return "LLM 审查";
    }

    /**
     * 从审查步骤摘要中提取请求审查模式。
     *
     * @param reviewStep 审查步骤
     * @return 请求审查模式
     */
    private String extractReviewMode(CompileJobStepRecord reviewStep) {
        if (reviewStep == null) {
            return null;
        }
        String reviewMode = extractText(reviewStep.getSummary(), "reviewMode");
        if (reviewMode == null) {
            reviewMode = extractText(reviewStep.getInputSummary(), "reviewMode");
        }
        return reviewMode == null ? null : CompileExecutionRequest.normalizeReviewMode(reviewMode);
    }

    /**
     * 解析审查展示警示文案。
     *
     * @param reviewStep 审查步骤
     * @return 审查展示警示文案
     */
    private String resolveReviewDisplayWarning(CompileJobStepRecord reviewStep) {
        if (reviewStep == null) {
            return "未记录 review_articles 步骤。";
        }
        String route = normalize(reviewStep.getModelRoute());
        if (RULE_BASED_ROUTE.equalsIgnoreCase(route)) {
            return "当前为规则审查，不是 LLM 内容审查。";
        }
        return null;
    }

    /**
     * 解析自动修复展示文案。
     *
     * @param reviewStep 审查步骤
     * @param fixStepPresent 是否存在自动修复步骤
     * @return 自动修复展示文案
     */
    private String resolveFixDisplayMessage(CompileJobStepRecord reviewStep, boolean fixStepPresent) {
        if (fixStepPresent) {
            return "已触发自动修复";
        }
        if (reviewStep == null) {
            return "未触发自动修复：未记录审查步骤";
        }
        return "未触发自动修复：无 fixable issue";
    }

    /**
     * 解析自动修复尝试次数。
     *
     * @param reviewStep 审查步骤
     * @param latestFixStep 最近自动修复步骤
     * @param fixStepCount 自动修复步骤数量
     * @return 自动修复尝试次数
     */
    private Integer resolveFixAttemptCount(
            CompileJobStepRecord reviewStep,
            CompileJobStepRecord latestFixStep,
            int fixStepCount
    ) {
        Integer fixAttemptCount = extractCount(latestFixStep, "fixAttemptCount");
        if (fixAttemptCount != null) {
            return fixAttemptCount;
        }
        fixAttemptCount = extractCount(reviewStep, "fixAttemptCount");
        if (fixAttemptCount != null) {
            return fixAttemptCount;
        }
        return Integer.valueOf(fixStepCount);
    }

    /**
     * 从步骤记录中提取计数字段。
     *
     * @param stepRecord 步骤记录
     * @param key 计数字段名
     * @return 计数值
     */
    private Integer extractCount(CompileJobStepRecord stepRecord, String key) {
        if (stepRecord == null) {
            return null;
        }
        Integer outputCount = extractCount(stepRecord.getOutputSummary(), key);
        if (outputCount != null) {
            return outputCount;
        }
        return extractCount(stepRecord.getSummary(), key);
    }

    /**
     * 从摘要文本中提取计数字段。
     *
     * @param text 摘要文本
     * @param key 计数字段名
     * @return 计数值
     */
    private Integer extractCount(String text, String key) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Pattern pattern = Pattern.compile("(?i)(?:\\b|\\\")" + Pattern.quote(key) + "(?:\\b|\\\")\\s*[:=]\\s*(\\d+)");
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return Integer.valueOf(matcher.group(1));
    }

    /**
     * 从摘要文本中提取文本字段。
     *
     * @param text 摘要文本
     * @param key 字段名
     * @return 文本字段
     */
    private String extractText(String text, String key) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Pattern pattern = Pattern.compile("(?i)(?:\\b|\\\")" + Pattern.quote(key)
                + "(?:\\b|\\\")\\s*[:=]\\s*([A-Z_\\-]+)");
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    /**
     * 规范化字符串。
     *
     * @param value 原始字符串
     * @return 规范化字符串
     */
    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * 判断计数是否为正数。
     *
     * @param value 计数值
     * @return 是否为正数
     */
    private boolean hasPositiveCount(Integer value) {
        return value != null && value.intValue() > 0;
    }

    /**
     * 判断审查结果是否没有待处理问题。
     *
     * @param summary 审查摘要
     * @return 是否没有待处理问题
     */
    private boolean hasNoReviewIssue(AdminCompileReviewSummaryResponse summary) {
        boolean reviewCountsRecorded = summary.getPendingReviewCount() != null || summary.getNeedsHumanReviewCount() != null;
        return reviewCountsRecorded
                && !hasPositiveCount(summary.getPendingReviewCount())
                && !hasPositiveCount(summary.getNeedsHumanReviewCount());
    }
}
