import { RefreshCw } from "lucide-react";
import { Link } from "react-router-dom";

import type { SourceRun } from "../../api/contracts/source-imports";
import type { SourceDetail, SourceFile } from "../../api/contracts/sources";
import { PageState } from "../../components/page-state";

export type SourceDetailView = "files" | "runs" | "config";

interface SourceDetailSectionsProps {
  source: SourceDetail;
  files: SourceFile[] | undefined;
  filesPending: boolean;
  filesError: string | null;
  runs: SourceRun[] | undefined;
  runsPending: boolean;
  runsFetching: boolean;
  runsError: string | null;
  view: SourceDetailView;
  onViewChange: (view: SourceDetailView) => void;
  onFilesRetry: () => void;
  onRunsRetry: () => void;
}

const SOURCE_TYPE_LABELS: Record<SourceDetail["sourceType"], string> = {
  UPLOAD: "本地上传",
  GIT: "Git",
  INTERNAL_MIRROR: "内部镜像",
};

const SOURCE_STATUS_LABELS: Record<SourceDetail["status"], string> = {
  ACTIVE: "启用",
  DISABLED: "停用",
  ARCHIVED: "已归档",
};

export function SourceDetailSections(props: SourceDetailSectionsProps) {
  return (
    <>
      <SourceSummary source={props.source} />
      <div aria-label="资料源详情视图" className="source-detail-tabs" role="tablist">
        <Tab active={props.view === "files"} label={`文件 ${props.files?.length ?? "--"}`} onClick={() => props.onViewChange("files")} />
        <Tab active={props.view === "runs"} label={`同步历史 ${props.runs?.length ?? "--"}`} onClick={() => props.onViewChange("runs")} />
        <Tab active={props.view === "config"} label="配置" onClick={() => props.onViewChange("config")} />
      </div>
      <section aria-label={viewLabel(props.view)} className="source-detail-panel" role="tabpanel">
        {props.view === "files" ? (
          <SourceFiles
            error={props.filesError}
            files={props.files}
            onRetry={props.onFilesRetry}
            pending={props.filesPending}
          />
        ) : props.view === "runs" ? (
          <SourceRuns
            error={props.runsError}
            fetching={props.runsFetching}
            onRetry={props.onRunsRetry}
            pending={props.runsPending}
            runs={props.runs}
          />
        ) : (
          <SourceConfiguration source={props.source} />
        )}
      </section>
    </>
  );
}

function Tab({ active, label, onClick }: { active: boolean; label: string; onClick: () => void }) {
  return (
    <button aria-selected={active} onClick={onClick} role="tab" type="button">
      {label}
    </button>
  );
}

function SourceSummary({ source }: { source: SourceDetail }) {
  return (
    <dl className="source-detail-summary">
      <SummaryItem label="类型" value={SOURCE_TYPE_LABELS[source.sourceType]} />
      <SummaryItem label="状态" value={SOURCE_STATUS_LABELS[source.status]} />
      <SummaryItem label="内容类型" value={source.contentProfile} />
      <SummaryItem label="可见性" value={source.visibility === "ADMIN_ONLY" ? "仅管理侧" : "普通"} />
      <SummaryItem label="同步模式" value={source.defaultSyncMode} />
      <SummaryItem label="最近同步" value={formatDateTime(source.lastSyncAt)} />
    </dl>
  );
}

function SummaryItem({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}

function SourceFiles({
  files,
  pending,
  error,
  onRetry,
}: {
  files: SourceFile[] | undefined;
  pending: boolean;
  error: string | null;
  onRetry: () => void;
}) {
  if (pending) return <PageState status="loading" title="正在加载源文件" />;
  if (error) return <PageState actionLabel="重试" description={error} onAction={onRetry} status="error" title="源文件加载失败" />;
  if (!files?.length) return <PageState status="empty" title="该资料源暂无文件" />;
  return (
    <div className="data-table-scroll">
      <table aria-label="资料源文件" className="data-table source-files-table">
        <thead>
          <tr>
            <th scope="col">文件路径</th>
            <th scope="col">格式</th>
            <th scope="col">解析方式</th>
            <th scope="col">大小</th>
            <th scope="col">内容预览</th>
          </tr>
        </thead>
        <tbody>
          {files.map((file) => (
            <tr key={file.id}>
              <td data-label="文件路径"><code>{file.relativePath}</code></td>
              <td data-label="格式">{file.format || "--"}</td>
              <td data-label="解析方式">
                <span>{file.parseMode ?? "未解析"}</span>
                {file.parseProvider ? <small>{file.parseProvider}</small> : null}
              </td>
              <td data-label="大小">{formatBytes(file.fileSize)}</td>
              <td data-label="内容预览">
                {file.contentPreview ? (
                  <details className="source-file-preview">
                    <summary>查看预览</summary>
                    <pre>{file.contentPreview}</pre>
                  </details>
                ) : (
                  "--"
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function SourceRuns({
  runs,
  pending,
  fetching,
  error,
  onRetry,
}: {
  runs: SourceRun[] | undefined;
  pending: boolean;
  fetching: boolean;
  error: string | null;
  onRetry: () => void;
}) {
  if (pending) return <PageState status="loading" title="正在加载同步历史" />;
  if (error) return <PageState actionLabel="重试" description={error} onAction={onRetry} status="error" title="同步历史加载失败" />;
  if (!runs?.length) return <PageState status="empty" title="尚无同步运行" />;
  return (
    <div className="source-run-list">
      <div className="source-run-list-heading">
        <span>服务端返回 {runs.length} 次运行</span>
        {fetching ? <span><RefreshCw aria-hidden="true" className="state-loading-icon" size={14} />正在刷新</span> : null}
      </div>
      <ol>
        {runs.map((run) => (
          <li className={`source-run-item is-${normalizeTone(run.displayTone)}`} key={run.runId}>
            <div className="source-run-heading">
              <div>
                <strong>运行 #{run.runId}</strong>
                <span>{run.displayStatusLabel}</span>
              </div>
              <time dateTime={run.requestedAt ?? undefined}>{formatDateTime(run.requestedAt)}</time>
            </div>
            <p>{run.operationalNote ?? run.reasonSummary ?? run.message ?? "服务端未提供运行说明"}</p>
            <ol aria-label={`运行 ${run.runId} 步骤`} className="source-run-steps">
              {run.progressSteps.map((step) => (
                <li className={`is-${step.status.toLowerCase()}`} key={step.key}>
                  <span>{step.label}</span>
                  <small>{step.status}</small>
                </li>
              ))}
            </ol>
            {run.errorMessage ? <p className="source-run-error">{run.errorMessage}</p> : null}
            <Link to={`/activity?kind=source-run&id=${run.runId}`}>查看处理任务</Link>
          </li>
        ))}
      </ol>
    </div>
  );
}

function SourceConfiguration({ source }: { source: SourceDetail }) {
  return (
    <div className="source-configuration">
      <section>
        <h2>资料源配置</h2>
        <pre>{formatSafeJson(source.configJson)}</pre>
      </section>
      <dl>
        <SummaryItem label="资料编码" value={source.sourceCode} />
        <SummaryItem label="原始名称" value={source.name} />
        <SummaryItem label="主要标题" value={source.primaryDocumentTitle ?? "--"} />
        <SummaryItem label="最近清单哈希" value={source.latestManifestHash ?? "--"} />
        <SummaryItem label="创建时间" value={formatDateTime(source.createdAt)} />
        <SummaryItem label="更新时间" value={formatDateTime(source.updatedAt)} />
      </dl>
    </div>
  );
}

function formatSafeJson(value: string) {
  try {
    return JSON.stringify(redactSensitive(JSON.parse(value) as unknown), null, 2);
  } catch {
    return "配置不是可展示的 JSON";
  }
}

function redactSensitive(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(redactSensitive);
  if (!value || typeof value !== "object") return value;
  return Object.fromEntries(
    Object.entries(value).map(([key, entry]) => [
      key,
      /(secret|password|token|api.?key)/i.test(key) ? "***" : redactSensitive(entry),
    ]),
  );
}

function normalizeTone(tone: string) {
  if (tone === "danger" || tone === "error") return "danger";
  if (tone === "success") return "success";
  if (tone === "warning") return "warning";
  return "info";
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function formatDateTime(value: string | null) {
  if (!value) return "--";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "numeric",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function viewLabel(view: SourceDetailView) {
  if (view === "runs") return "同步历史";
  if (view === "config") return "资料源配置";
  return "资料源文件";
}
