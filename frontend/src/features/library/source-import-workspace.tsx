import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  FileArchive,
  FolderCog,
  GitBranch,
  HardDriveDownload,
  RefreshCw,
  ServerCog,
  X,
  type LucideIcon,
} from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";

import { isApiError } from "../../api/api-error";
import {
  sourceImportsApi,
  type CompileJob,
  type SourceCreateRequest,
  type SourceRun,
  type SourceValidation,
  type UploadFile,
} from "../../api/contracts/source-imports";
import type { SourceDetail } from "../../api/contracts/sources";
import { queryKeys } from "../../api/query-keys";
import { InlineAlert } from "../../components/inline-alert";
import { SourceFilePicker } from "./source-file-picker";

type ImportMode = "local" | "git" | "mirror" | "server-directory" | "direct-compile";
type ImportStep = 1 | 2 | 3;

type ImportResult =
  | { kind: "source-run"; run: SourceRun }
  | {
      kind: "source";
      source: SourceDetail;
      validation: SourceValidation | null;
      run: SourceRun | null;
      issue: string | null;
    }
  | { kind: "compile-job"; job: CompileJob };

interface SourceImportWorkspaceProps {
  onClose: () => void;
}

const MODE_OPTIONS: Array<{
  value: ImportMode;
  label: string;
  description: string;
  icon: LucideIcon;
}> = [
  { value: "local", label: "本地资料", description: "文件或目录", icon: HardDriveDownload },
  { value: "git", label: "Git 仓库", description: "公开或私有", icon: GitBranch },
  { value: "mirror", label: "内部镜像", description: "镜像根引用", icon: FileArchive },
  { value: "server-directory", label: "服务端目录", description: "目录编译", icon: ServerCog },
  { value: "direct-compile", label: "直接编译", description: "上传后编译", icon: FolderCog },
];

export function SourceImportWorkspace({ onClose }: SourceImportWorkspaceProps) {
  const queryClient = useQueryClient();
  const headingRef = useRef<HTMLHeadingElement>(null);
  const [step, setStep] = useState<ImportStep>(1);
  const [mode, setMode] = useState<ImportMode>("local");
  const [files, setFiles] = useState<UploadFile[]>([]);
  const [incremental, setIncremental] = useState(false);
  const [serverDirectory, setServerDirectory] = useState("");
  const [sourceName, setSourceName] = useState("");
  const [sourceCode, setSourceCode] = useState("");
  const [contentProfile, setContentProfile] = useState<"DOCUMENT" | "CODE">("DOCUMENT");
  const [visibility, setVisibility] = useState<"NORMAL" | "ADMIN_ONLY">("NORMAL");
  const [syncMode, setSyncMode] = useState<"AUTO" | "FULL" | "INCREMENTAL">("AUTO");
  const [remoteUrl, setRemoteUrl] = useState("");
  const [branch, setBranch] = useState("main");
  const [privateRepository, setPrivateRepository] = useState(false);
  const [credentialRef, setCredentialRef] = useState("");
  const [mirrorRootRef, setMirrorRootRef] = useState("");
  const [projectPath, setProjectPath] = useState("");
  const [credentialCode, setCredentialCode] = useState("");
  const [credentialType, setCredentialType] = useState<"GIT_TOKEN" | "GIT_HTTP_BASIC">("GIT_TOKEN");
  const [credentialSecret, setCredentialSecret] = useState("");
  const [credentialUpdatedBy, setCredentialUpdatedBy] = useState("");
  const [credentialNotice, setCredentialNotice] = useState<string | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);

  useEffect(() => headingRef.current?.focus(), []);

  const credentialsQuery = useQuery({
    queryKey: queryKeys.sources.credentials,
    queryFn: ({ signal }) => sourceImportsApi.listCredentials(signal),
    enabled: step === 2 && mode === "git",
  });

  const credentialMutation = useMutation({
    mutationFn: () => {
      if (!credentialCode.trim()) throw new Error("请输入凭据编码");
      if (!credentialSecret) throw new Error("请输入凭据明文");
      return sourceImportsApi.saveCredential({
        credentialCode: credentialCode.trim(),
        credentialType,
        secret: credentialSecret,
        updatedBy: credentialUpdatedBy.trim() || undefined,
      });
    },
    onSuccess: (credential) => {
      queryClient.setQueryData(queryKeys.sources.credentials, (current: unknown) => {
        const credentials = Array.isArray(current) ? current : [];
        return [...credentials.filter((item) => isCredentialWithCode(item, credential.credentialCode)), credential];
      });
      setCredentialRef(credential.credentialCode);
      setCredentialSecret("");
      setCredentialNotice(`${credential.credentialCode} 已保存，返回值为 ${credential.secretMask}`);
    },
  });

  const importMutation = useMutation({
    mutationFn: () => submitImport(),
    onSuccess: (nextResult) => {
      setResult(nextResult);
      setStep(3);
    },
    onSettled: () => void invalidateImportQueries(queryClient),
  });

  const recoveryMutation = useMutation({
    mutationFn: async (source: SourceDetail) => synchronizeCreatedSource(source),
    onSuccess: (nextResult) => setResult(nextResult),
    onSettled: () => void invalidateImportQueries(queryClient),
  });

  async function submitImport(): Promise<ImportResult> {
    if (mode === "local") {
      requireFiles(files);
      return { kind: "source-run", run: await sourceImportsApi.upload(files) };
    }
    if (mode === "direct-compile") {
      requireFiles(files);
      return {
        kind: "compile-job",
        job: await sourceImportsApi.compileUpload(files, incremental),
      };
    }
    if (mode === "server-directory") {
      if (!serverDirectory.trim()) throw new Error("请输入服务端目录");
      return {
        kind: "compile-job",
        job: await sourceImportsApi.compileDirectory({
          sourceDir: serverDirectory.trim(),
          incremental,
          async: true,
        }),
      };
    }

    const request = buildSourceRequest();
    const source =
      mode === "git"
        ? await sourceImportsApi.createGit(request)
        : await sourceImportsApi.createInternalMirror(request);
    return synchronizeCreatedSource(source);
  }

  function buildSourceRequest(): SourceCreateRequest {
    const baseRequest: SourceCreateRequest = {
      name: resolveSourceName(mode, sourceName, remoteUrl, projectPath),
      sourceCode: sourceCode.trim() || undefined,
      contentProfile,
      visibility,
      defaultSyncMode: syncMode,
    };
    if (mode === "git") {
      if (!remoteUrl.trim()) throw new Error("请输入 Git 仓库地址");
      if (privateRepository && !credentialRef) throw new Error("私有仓库必须选择访问凭据");
      return {
        ...baseRequest,
        remoteUrl: remoteUrl.trim(),
        branch: branch.trim() || "main",
        credentialRef: privateRepository ? credentialRef : undefined,
      };
    }
    if (!mirrorRootRef.trim()) throw new Error("请输入镜像根引用");
    if (!projectPath.trim()) throw new Error("请输入镜像项目相对路径");
    return {
      ...baseRequest,
      mirrorRootRef: mirrorRootRef.trim(),
      projectPath: projectPath.trim(),
    };
  }

  async function synchronizeCreatedSource(source: SourceDetail): Promise<ImportResult> {
    try {
      const validation = await sourceImportsApi.validate(source.id);
      if (!validation.valid) {
        return { kind: "source", source, validation, run: null, issue: validation.message };
      }
      const run = await sourceImportsApi.sync(source.id);
      return { kind: "source", source, validation, run, issue: null };
    } catch (error) {
      return {
        kind: "source",
        source,
        validation: null,
        run: null,
        issue: resolveErrorMessage(error),
      };
    }
  }

  const reset = () => {
    importMutation.reset();
    recoveryMutation.reset();
    setResult(null);
    setFiles([]);
    setStep(1);
  };

  return (
    <section aria-labelledby="source-import-heading" className="source-import-workspace">
      <header className="source-import-header">
        <div>
          <h2 id="source-import-heading" ref={headingRef} tabIndex={-1}>
            导入资料
          </h2>
          <ol aria-label="导入步骤" className="source-import-steps">
            {["选择入口", "填写配置", "查看结果"].map((label, index) => {
              const position = (index + 1) as ImportStep;
              return (
                <li aria-current={position === step ? "step" : undefined} key={label}>
                  <span>{position}</span>
                  {label}
                </li>
              );
            })}
          </ol>
        </div>
        <button
          aria-label="关闭导入"
          className="icon-button"
          onClick={onClose}
          title="关闭"
          type="button"
        >
          <X aria-hidden="true" size={18} />
        </button>
      </header>

      {step === 1 ? (
        <ImportModeStep mode={mode} onModeChange={setMode} onNext={() => setStep(2)} />
      ) : null}
      {step === 2 ? (
        <form
          className="source-import-form"
          onSubmit={(event) => {
            event.preventDefault();
            importMutation.mutate();
          }}
        >
          {mode === "local" || mode === "direct-compile" ? (
            <SourceFilePicker files={files} onChange={setFiles} />
          ) : null}
          {mode === "server-directory" ? (
            <label className="form-field field-span-2">
              <span>服务端目录</span>
              <input
                onChange={(event) => setServerDirectory(event.target.value)}
                placeholder="/srv/lattice/sources/project"
                required
                type="text"
                value={serverDirectory}
              />
            </label>
          ) : null}
          {mode === "git" ? (
            <GitFields
              branch={branch}
              credentialError={credentialsQuery.isError ? resolveErrorMessage(credentialsQuery.error) : null}
              credentialRef={credentialRef}
              credentials={credentialsQuery.data ?? []}
              privateRepository={privateRepository}
              remoteUrl={remoteUrl}
              onBranchChange={setBranch}
              onCredentialRefChange={setCredentialRef}
              onPrivateRepositoryChange={setPrivateRepository}
              onRemoteUrlChange={setRemoteUrl}
            />
          ) : null}
          {mode === "mirror" ? (
            <>
              <label className="form-field">
                <span>镜像根引用</span>
                <input
                  onChange={(event) => setMirrorRootRef(event.target.value)}
                  required
                  type="text"
                  value={mirrorRootRef}
                />
              </label>
              <label className="form-field">
                <span>项目相对路径</span>
                <input
                  onChange={(event) => setProjectPath(event.target.value)}
                  placeholder="team/project"
                  required
                  type="text"
                  value={projectPath}
                />
              </label>
            </>
          ) : null}
          {mode === "git" || mode === "mirror" ? (
            <SourceMetadataFields
              contentProfile={contentProfile}
              sourceCode={sourceCode}
              sourceName={sourceName}
              syncMode={syncMode}
              visibility={visibility}
              onContentProfileChange={setContentProfile}
              onSourceCodeChange={setSourceCode}
              onSourceNameChange={setSourceName}
              onSyncModeChange={setSyncMode}
              onVisibilityChange={setVisibility}
            />
          ) : null}
          {mode === "git" && privateRepository ? (
            <CredentialEditor
              code={credentialCode}
              error={credentialMutation.isError ? resolveErrorMessage(credentialMutation.error) : null}
              isPending={credentialMutation.isPending}
              notice={credentialNotice}
              secret={credentialSecret}
              type={credentialType}
              updatedBy={credentialUpdatedBy}
              onCodeChange={setCredentialCode}
              onSave={() => credentialMutation.mutate()}
              onSecretChange={setCredentialSecret}
              onTypeChange={setCredentialType}
              onUpdatedByChange={setCredentialUpdatedBy}
            />
          ) : null}
          {mode === "server-directory" || mode === "direct-compile" ? (
            <label className="checkbox-field field-span-2">
              <input
                checked={incremental}
                onChange={(event) => setIncremental(event.target.checked)}
                type="checkbox"
              />
              增量编译
            </label>
          ) : null}

          {importMutation.isError ? (
            <div className="field-span-2">
              <InlineAlert
                description={resolveErrorMessage(importMutation.error)}
                title="导入未提交"
                tone="error"
              />
            </div>
          ) : null}
          <div className="source-import-actions field-span-2">
            <button className="secondary-button" onClick={() => setStep(1)} type="button">
              返回
            </button>
            <button className="primary-button" disabled={importMutation.isPending} type="submit">
              {importMutation.isPending ? "正在提交" : submitLabel(mode)}
            </button>
          </div>
        </form>
      ) : null}
      {step === 3 && result ? (
        <ImportResultView
          isRecovering={recoveryMutation.isPending}
          result={result}
          onRecover={(source) => recoveryMutation.mutate(source)}
          onReset={reset}
        />
      ) : null}
    </section>
  );
}

function ImportModeStep({
  mode,
  onModeChange,
  onNext,
}: {
  mode: ImportMode;
  onModeChange: (mode: ImportMode) => void;
  onNext: () => void;
}) {
  return (
    <div className="source-import-mode-step">
      <div aria-label="导入入口" className="source-import-modes" role="group">
        {MODE_OPTIONS.map((option) => {
          const Icon = option.icon;
          return (
            <button
              aria-pressed={mode === option.value}
              className={mode === option.value ? "is-selected" : undefined}
              key={option.value}
              onClick={() => onModeChange(option.value)}
              type="button"
            >
              <Icon aria-hidden="true" size={19} />
              <strong>{option.label}</strong>
              <span>{option.description}</span>
            </button>
          );
        })}
      </div>
      <div className="source-import-actions">
        <button className="primary-button" onClick={onNext} type="button">
          下一步
        </button>
      </div>
    </div>
  );
}

function GitFields(props: {
  remoteUrl: string;
  branch: string;
  privateRepository: boolean;
  credentialRef: string;
  credentials: Array<{ credentialCode: string; secretMask: string; enabled: boolean }>;
  credentialError: string | null;
  onRemoteUrlChange: (value: string) => void;
  onBranchChange: (value: string) => void;
  onPrivateRepositoryChange: (value: boolean) => void;
  onCredentialRefChange: (value: string) => void;
}) {
  return (
    <>
      <label className="form-field field-span-2">
        <span>仓库地址</span>
        <input
          onChange={(event) => props.onRemoteUrlChange(event.target.value)}
          placeholder="https://github.com/org/repo.git"
          required
          type="url"
          value={props.remoteUrl}
        />
      </label>
      <fieldset className="segmented-field">
        <legend>访问方式</legend>
        <div className="segmented-control">
          <button
            aria-pressed={!props.privateRepository}
            onClick={() => props.onPrivateRepositoryChange(false)}
            type="button"
          >
            公开仓库
          </button>
          <button
            aria-pressed={props.privateRepository}
            onClick={() => props.onPrivateRepositoryChange(true)}
            type="button"
          >
            私有仓库
          </button>
        </div>
      </fieldset>
      <label className="form-field">
        <span>分支</span>
        <input onChange={(event) => props.onBranchChange(event.target.value)} type="text" value={props.branch} />
      </label>
      {props.privateRepository ? (
        <label className="form-field">
          <span>访问凭据</span>
          <select
            onChange={(event) => props.onCredentialRefChange(event.target.value)}
            required
            value={props.credentialRef}
          >
            <option value="">选择凭据</option>
            {props.credentials.filter((item) => item.enabled).map((item) => (
              <option key={item.credentialCode} value={item.credentialCode}>
                {item.credentialCode} ({item.secretMask})
              </option>
            ))}
          </select>
        </label>
      ) : null}
      {props.credentialError ? (
        <div className="field-span-2">
          <InlineAlert description={props.credentialError} title="凭据列表加载失败" tone="error" />
        </div>
      ) : null}
    </>
  );
}

function SourceMetadataFields(props: {
  sourceName: string;
  sourceCode: string;
  contentProfile: "DOCUMENT" | "CODE";
  visibility: "NORMAL" | "ADMIN_ONLY";
  syncMode: "AUTO" | "FULL" | "INCREMENTAL";
  onSourceNameChange: (value: string) => void;
  onSourceCodeChange: (value: string) => void;
  onContentProfileChange: (value: "DOCUMENT" | "CODE") => void;
  onVisibilityChange: (value: "NORMAL" | "ADMIN_ONLY") => void;
  onSyncModeChange: (value: "AUTO" | "FULL" | "INCREMENTAL") => void;
}) {
  return (
    <>
      <label className="form-field">
        <span>显示名称</span>
        <input onChange={(event) => props.onSourceNameChange(event.target.value)} type="text" value={props.sourceName} />
      </label>
      <label className="form-field">
        <span>资料编码</span>
        <input onChange={(event) => props.onSourceCodeChange(event.target.value)} type="text" value={props.sourceCode} />
      </label>
      <label className="form-field">
        <span>内容类型</span>
        <select
          onChange={(event) => props.onContentProfileChange(event.target.value as "DOCUMENT" | "CODE")}
          value={props.contentProfile}
        >
          <option value="DOCUMENT">文档</option>
          <option value="CODE">代码</option>
        </select>
      </label>
      <label className="form-field">
        <span>可见性</span>
        <select
          onChange={(event) => props.onVisibilityChange(event.target.value as "NORMAL" | "ADMIN_ONLY")}
          value={props.visibility}
        >
          <option value="NORMAL">普通</option>
          <option value="ADMIN_ONLY">仅管理侧</option>
        </select>
      </label>
      <label className="form-field">
        <span>同步模式</span>
        <select
          onChange={(event) => props.onSyncModeChange(event.target.value as "AUTO" | "FULL" | "INCREMENTAL")}
          value={props.syncMode}
        >
          <option value="AUTO">自动</option>
          <option value="FULL">全量</option>
          <option value="INCREMENTAL">增量</option>
        </select>
      </label>
    </>
  );
}

function CredentialEditor(props: {
  code: string;
  type: "GIT_TOKEN" | "GIT_HTTP_BASIC";
  secret: string;
  updatedBy: string;
  isPending: boolean;
  error: string | null;
  notice: string | null;
  onCodeChange: (value: string) => void;
  onTypeChange: (value: "GIT_TOKEN" | "GIT_HTTP_BASIC") => void;
  onSecretChange: (value: string) => void;
  onUpdatedByChange: (value: string) => void;
  onSave: () => void;
}) {
  return (
    <details className="credential-editor field-span-2">
      <summary>新增访问凭据</summary>
      <div className="credential-editor-fields">
        <label className="form-field">
          <span>凭据编码</span>
          <input onChange={(event) => props.onCodeChange(event.target.value)} type="text" value={props.code} />
        </label>
        <label className="form-field">
          <span>凭据类型</span>
          <select
            onChange={(event) => props.onTypeChange(event.target.value as "GIT_TOKEN" | "GIT_HTTP_BASIC")}
            value={props.type}
          >
            <option value="GIT_TOKEN">Git Token</option>
            <option value="GIT_HTTP_BASIC">Git HTTP Basic</option>
          </select>
        </label>
        <label className="form-field">
          <span>凭据明文</span>
          <input
            autoComplete="new-password"
            onChange={(event) => props.onSecretChange(event.target.value)}
            type="password"
            value={props.secret}
          />
        </label>
        <label className="form-field">
          <span>更新人</span>
          <input onChange={(event) => props.onUpdatedByChange(event.target.value)} type="text" value={props.updatedBy} />
        </label>
        <button className="secondary-button" disabled={props.isPending} onClick={props.onSave} type="button">
          {props.isPending ? "正在保存" : "保存凭据"}
        </button>
      </div>
      {props.notice ? <InlineAlert title={props.notice} tone="success" /> : null}
      {props.error ? <InlineAlert description={props.error} title="凭据保存失败" tone="error" /> : null}
    </details>
  );
}

function ImportResultView({
  result,
  isRecovering,
  onRecover,
  onReset,
}: {
  result: ImportResult;
  isRecovering: boolean;
  onRecover: (source: SourceDetail) => void;
  onReset: () => void;
}) {
  const source = result.kind === "source" ? result.source : null;
  const run = result.kind === "source-run" ? result.run : result.kind === "source" ? result.run : null;
  const job = result.kind === "compile-job" ? result.job : null;
  const issue = result.kind === "source" ? result.issue : null;
  return (
    <div className="source-import-result">
      <InlineAlert
        description={issue ?? run?.nextStepHint ?? job?.progressMessage ?? undefined}
        title={issue ? "资料源已创建，尚未同步" : "导入已提交"}
        tone={issue ? "warning" : "success"}
      />
      <dl>
        {source ? (
          <>
            <div><dt>资料源</dt><dd>{source.displayName}</dd></div>
            <div><dt>资料编码</dt><dd><code>{source.sourceCode}</code></dd></div>
          </>
        ) : null}
        {run ? (
          <>
            <div><dt>运行编号</dt><dd>{run.runId}</dd></div>
            <div><dt>当前状态</dt><dd>{run.displayStatusLabel}</dd></div>
          </>
        ) : null}
        {job ? (
          <>
            <div><dt>作业编号</dt><dd><code>{job.jobId}</code></dd></div>
            <div><dt>当前状态</dt><dd>{job.derivedStatus}</dd></div>
          </>
        ) : null}
      </dl>
      <div className="source-import-actions">
        <button className="secondary-button" onClick={onReset} type="button">
          继续导入
        </button>
        {source ? <Link className="primary-button" to={`/library/sources/${source.id}`}>查看资料源</Link> : null}
        {!source && run?.sourceId ? (
          <Link className="primary-button" to={`/library/sources/${run.sourceId}`}>查看资料源</Link>
        ) : null}
        {run ? (
          <Link className="secondary-button" to={`/activity?kind=source-run&id=${run.runId}`}>查看处理任务</Link>
        ) : null}
        {job ? (
          <Link className="secondary-button" to={`/activity?kind=compile-job&id=${job.jobId}`}>查看编译作业</Link>
        ) : null}
        {source && issue ? (
          <button className="primary-button" disabled={isRecovering} onClick={() => onRecover(source)} type="button">
            <RefreshCw aria-hidden="true" size={17} />
            {isRecovering ? "正在重试" : "重新校验并同步"}
          </button>
        ) : null}
      </div>
    </div>
  );
}

function submitLabel(mode: ImportMode) {
  if (mode === "server-directory" || mode === "direct-compile") return "提交编译";
  return "开始导入";
}

function requireFiles(files: UploadFile[]) {
  if (files.length === 0) throw new Error("请至少选择一个支持的文件");
}

function resolveSourceName(mode: ImportMode, name: string, remoteUrl: string, projectPath: string) {
  if (name.trim()) return name.trim();
  const fallback = mode === "git" ? remoteUrl : projectPath;
  const normalized = fallback.replace(/\/?\.git$/i, "").replace(/\/+$/, "");
  return normalized.split("/").pop()?.trim() || "资料源";
}

function resolveErrorMessage(error: unknown) {
  if (isApiError(error)) return error.message;
  return error instanceof Error ? error.message : "请求未能完成，请重试。";
}

function isCredentialWithCode(item: unknown, code: string) {
  return !(
    typeof item === "object" &&
    item !== null &&
    "credentialCode" in item &&
    item.credentialCode === code
  );
}

async function invalidateImportQueries(queryClient: ReturnType<typeof useQueryClient>) {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: queryKeys.sources.root }),
    queryClient.invalidateQueries({ queryKey: ["admin", "processing-tasks"] }),
    queryClient.invalidateQueries({ queryKey: queryKeys.activity.compileJobs }),
  ]);
}
