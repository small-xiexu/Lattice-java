import { Link } from "react-router-dom";

import type { ProcessingTask } from "../../api/contracts/activity";
import type { CompileJob, SourceRun } from "../../api/contracts/source-imports";
import { CompileJobActions, SourceRunActions } from "./activity-actions";
import {
  ActivityFact,
  ActivityFacts,
  ActivitySteps,
} from "./activity-shared";
import { formatDateTime } from "./activity-utils";

type SourceTask = SourceRun | ProcessingTask;

export function SourceTaskDetail({ task }: { task: SourceTask }) {
  const title = "title" in task ? task.title : task.sourceName || `同步运行 #${task.runId}`;
  return (
    <article className="activity-detail">
      <header>
        <div>
          <span className={`activity-status is-${normalizeTone(task.displayTone)}`}>
            {task.displayStatusLabel}
          </span>
          <h2 tabIndex={-1}>{title}</h2>
          <code>{"taskId" in task ? task.taskId : `source-run:${task.runId}`}</code>
        </div>
        {task.processingActive ? <span className="activity-live">自动刷新中</span> : null}
      </header>

      <ActivityFacts>
        <ActivityFact label="当前步骤">{task.currentStepLabel}</ActivityFact>
        <ActivityFact label="进度">{task.progressText || "--"}</ActivityFact>
        <ActivityFact label="资料源">
          {task.sourceId ? <Link to={`/library/sources/${task.sourceId}`}>{task.sourceName || `#${task.sourceId}`}</Link> : "--"}
        </ActivityFact>
        <ActivityFact label="编译作业">
          {task.compileJobId ? <Link to={`/activity?kind=compile-job&id=${encodeURIComponent(task.compileJobId)}`}>{task.compileJobId}</Link> : "--"}
        </ActivityFact>
        <ActivityFact label="提交时间">{formatDateTime(task.requestedAt)}</ActivityFact>
        <ActivityFact label="完成时间">{formatDateTime(task.finishedAt)}</ActivityFact>
      </ActivityFacts>

      {task.reasonSummary || task.nextStepHint ? (
        <section className="activity-detail-section">
          <h3>状态说明</h3>
          {task.reasonSummary ? <p>{task.reasonSummary}</p> : null}
          {task.nextStepHint ? <p><strong>下一步：</strong>{task.nextStepHint}</p> : null}
        </section>
      ) : null}

      <section className="activity-detail-section">
        <h3>执行步骤</h3>
        <ActivitySteps steps={task.progressSteps} />
      </section>

      {task.sourceNames.length ? (
        <section className="activity-detail-section">
          <h3>输入文件</h3>
          <ul className="activity-file-list" tabIndex={0}>
            {task.sourceNames.map((name) => <li key={name}><code>{name}</code></li>)}
          </ul>
        </section>
      ) : null}

      {task.errorMessage || task.compileErrorCode ? (
        <details className="activity-diagnostic">
          <summary>错误与诊断信息</summary>
          {task.compileErrorCode ? <p><strong>错误码：</strong>{task.compileErrorCode}</p> : null}
          {task.errorMessage ? <pre tabIndex={0}>{task.errorMessage}</pre> : null}
        </details>
      ) : null}

      {task.evidenceJson ? (
        <details className="activity-diagnostic">
          <summary>任务证据</summary>
          <pre tabIndex={0}>{formatJson(task.evidenceJson)}</pre>
        </details>
      ) : null}

      {task.runId ? <SourceRunActions target={{ ...task, runId: task.runId }} /> : null}
    </article>
  );
}

export function CompileJobDetail({ job }: { job: CompileJob }) {
  const review = job.reviewSummary;
  return (
    <article className="activity-detail">
      <header>
        <div>
          <span className={`activity-status is-${job.status === "FAILED" ? "danger" : job.status === "SUCCEEDED" ? "success" : "info"}`}>
            {job.derivedStatus}
          </span>
          <h2 tabIndex={-1}>编译作业</h2>
          <code>{job.jobId}</code>
        </div>
        {job.status === "RUNNING" || job.status === "QUEUED" ? <span className="activity-live">自动刷新中</span> : null}
      </header>

      <ActivityFacts>
        <ActivityFact label="当前步骤">{job.currentStep || "等待执行"}</ActivityFact>
        <ActivityFact label="进度">{formatProgress(job.progressCurrent, job.progressTotal)}</ActivityFact>
        <ActivityFact label="尝试次数">{job.attemptCount}</ActivityFact>
        <ActivityFact label="已写入文章">{job.persistedCount}</ActivityFact>
        <ActivityFact label="审查模式">{job.reviewMode || "--"}</ActivityFact>
        <ActivityFact label="提交时间">{formatDateTime(job.requestedAt)}</ActivityFact>
      </ActivityFacts>

      <section className="activity-detail-section">
        <h3>运行信息</h3>
        <p>{job.progressMessage || "暂无进度说明"}</p>
        <p className="activity-muted">最近心跳：{formatDateTime(job.lastHeartbeatAt)} · 租约到期：{formatDateTime(job.runningExpiresAt)}</p>
      </section>

      {review?.reviewStepPresent ? (
        <section className="activity-detail-section">
          <h3>编译审查</h3>
          <ActivityFacts>
            <ActivityFact label="审查路由">{review.reviewRoute || "--"}</ActivityFact>
            <ActivityFact label="已通过">{review.acceptedCount ?? 0}</ActivityFact>
            <ActivityFact label="待审查">{review.pendingReviewCount ?? 0}</ActivityFact>
            <ActivityFact label="待人工确认">{review.needsHumanReviewCount ?? 0}</ActivityFact>
          </ActivityFacts>
          {review.reviewDisplayWarning ? <p>{review.reviewDisplayWarning}</p> : null}
        </section>
      ) : null}

      {job.sourceNames.length ? (
        <section className="activity-detail-section">
          <h3>输入文件</h3>
          <ul className="activity-file-list" tabIndex={0}>
            {job.sourceNames.map((name) => <li key={name}><code>{name}</code></li>)}
          </ul>
        </section>
      ) : null}

      {job.errorMessage || job.errorCode ? (
        <details className="activity-diagnostic">
          <summary>错误与诊断信息</summary>
          {job.errorCode ? <p><strong>错误码：</strong>{job.errorCode}</p> : null}
          {job.errorMessage ? <pre tabIndex={0}>{job.errorMessage}</pre> : null}
        </details>
      ) : null}

      <CompileJobActions job={job} />
    </article>
  );
}

function normalizeTone(tone: string) {
  if (tone === "error") return "danger";
  return ["success", "warning", "danger", "info"].includes(tone) ? tone : "neutral";
}

function formatJson(value: string) {
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function formatProgress(current: number, total: number) {
  return total > 0 ? `${current}/${total}` : current > 0 ? String(current) : "--";
}
