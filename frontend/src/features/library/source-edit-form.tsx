import { useState, type FormEvent } from "react";

import type { SourceCredential } from "../../api/contracts/source-imports";
import type { SourceDetail, SourcePatchRequest } from "../../api/contracts/sources";
import { InlineAlert } from "../../components/inline-alert";

interface SourceEditFormProps {
  source: SourceDetail;
  credentials: SourceCredential[];
  isPending: boolean;
  error: string | null;
  onSubmit: (request: SourcePatchRequest) => void;
  onCancel: () => void;
}

export function SourceEditForm(props: SourceEditFormProps) {
  const sourceConfig = parseConfig(props.source.configJson);
  const [name, setName] = useState(props.source.name);
  const [status, setStatus] = useState(props.source.status);
  const [visibility, setVisibility] = useState(props.source.visibility);
  const [defaultSyncMode, setDefaultSyncMode] = useState(props.source.defaultSyncMode);
  const [remoteUrl, setRemoteUrl] = useState(readString(sourceConfig, "remoteUrl"));
  const [branch, setBranch] = useState(readString(sourceConfig, "branch") || "main");
  const [credentialRef, setCredentialRef] = useState(readString(sourceConfig, "credentialRef"));
  const [mirrorRootRef, setMirrorRootRef] = useState(readString(sourceConfig, "mirrorRootRef"));
  const [projectPath, setProjectPath] = useState(readString(sourceConfig, "projectPath"));
  const [archiveConfirmed, setArchiveConfirmed] = useState(false);

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const configJson = { ...sourceConfig };
    if (props.source.sourceType === "GIT") {
      configJson.remoteUrl = remoteUrl.trim();
      configJson.branch = branch.trim() || "main";
      if (credentialRef) configJson.credentialRef = credentialRef;
      else delete configJson.credentialRef;
    }
    if (props.source.sourceType === "INTERNAL_MIRROR") {
      configJson.mirrorRootRef = mirrorRootRef.trim();
      configJson.projectPath = projectPath.trim();
    }
    props.onSubmit({
      name: name.trim(),
      status,
      visibility,
      defaultSyncMode,
      configJson,
    });
  };
  const archiveRequiresConfirmation = props.source.status !== "ARCHIVED" && status === "ARCHIVED";

  return (
    <section aria-labelledby="source-edit-heading" className="source-edit-panel">
      <div>
        <h2 id="source-edit-heading">编辑资料源</h2>
        <p>保存配置不会自动触发同步；可先校验，再单独发起同步。</p>
      </div>
      <form className="source-edit-form" onSubmit={submit}>
        <label className="form-field field-span-2">
          <span>名称</span>
          <input onChange={(event) => setName(event.target.value)} required type="text" value={name} />
        </label>
        <label className="form-field">
          <span>状态</span>
          <select onChange={(event) => setStatus(event.target.value as SourceDetail["status"])} value={status}>
            {statusOptions(props.source.status).map((option) => (
              <option key={option} value={option}>{statusLabel(option)}</option>
            ))}
          </select>
        </label>
        <label className="form-field">
          <span>可见性</span>
          <select onChange={(event) => setVisibility(event.target.value as SourceDetail["visibility"])} value={visibility}>
            <option value="NORMAL">普通</option>
            <option value="ADMIN_ONLY">仅管理侧</option>
          </select>
        </label>
        <label className="form-field">
          <span>默认同步模式</span>
          <select onChange={(event) => setDefaultSyncMode(event.target.value as SourceDetail["defaultSyncMode"])} value={defaultSyncMode}>
            <option value="AUTO">自动</option>
            <option value="FULL">全量</option>
            <option value="INCREMENTAL">增量</option>
          </select>
        </label>
        {props.source.sourceType === "GIT" ? (
          <>
            <label className="form-field field-span-2">
              <span>仓库地址</span>
              <input onChange={(event) => setRemoteUrl(event.target.value)} required type="url" value={remoteUrl} />
            </label>
            <label className="form-field">
              <span>分支</span>
              <input onChange={(event) => setBranch(event.target.value)} required type="text" value={branch} />
            </label>
            <label className="form-field">
              <span>访问凭据</span>
              <select onChange={(event) => setCredentialRef(event.target.value)} value={credentialRef}>
                <option value="">公开仓库</option>
                {props.credentials.map((credential) => (
                  <option key={credential.credentialCode} value={credential.credentialCode}>
                    {credential.credentialCode} ({credential.secretMask})
                  </option>
                ))}
              </select>
            </label>
          </>
        ) : null}
        {props.source.sourceType === "INTERNAL_MIRROR" ? (
          <>
            <label className="form-field">
              <span>镜像根引用</span>
              <input onChange={(event) => setMirrorRootRef(event.target.value)} required type="text" value={mirrorRootRef} />
            </label>
            <label className="form-field">
              <span>项目路径</span>
              <input onChange={(event) => setProjectPath(event.target.value)} required type="text" value={projectPath} />
            </label>
          </>
        ) : null}
        {archiveRequiresConfirmation ? (
          <label className="source-archive-confirm field-span-2">
            <input checked={archiveConfirmed} onChange={(event) => setArchiveConfirmed(event.target.checked)} type="checkbox" />
            <span>确认归档后该资料源不能直接恢复为启用状态</span>
          </label>
        ) : null}
        {props.error ? <div className="field-span-2"><InlineAlert description={props.error} title="保存失败" tone="error" /></div> : null}
        <div className="source-detail-form-actions field-span-2">
          <button className="secondary-button compact-button" onClick={props.onCancel} type="button">取消</button>
          <button className="primary-button" disabled={props.isPending || (archiveRequiresConfirmation && !archiveConfirmed)} type="submit">
            {props.isPending ? "正在保存" : "保存设置"}
          </button>
        </div>
      </form>
    </section>
  );
}

function parseConfig(value: string): Record<string, unknown> {
  try {
    const parsed = JSON.parse(value) as unknown;
    return parsed && typeof parsed === "object" && !Array.isArray(parsed)
      ? { ...(parsed as Record<string, unknown>) }
      : {};
  } catch {
    return {};
  }
}

function readString(config: Record<string, unknown>, key: string) {
  return typeof config[key] === "string" ? config[key] : "";
}

function statusOptions(current: SourceDetail["status"]): SourceDetail["status"][] {
  if (current === "ARCHIVED") return ["ARCHIVED"];
  return current === "ACTIVE"
    ? ["ACTIVE", "DISABLED", "ARCHIVED"]
    : ["DISABLED", "ACTIVE", "ARCHIVED"];
}

function statusLabel(status: SourceDetail["status"]) {
  if (status === "ACTIVE") return "启用";
  if (status === "DISABLED") return "停用";
  return "归档";
}
