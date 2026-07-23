import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Pencil, RefreshCw, ShieldCheck, Upload } from "lucide-react";
import { useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";

import { isApiError } from "../../api/api-error";
import {
  sourceImportsApi,
  type SourceRun,
  type UploadFile,
} from "../../api/contracts/source-imports";
import { sourcesApi, type SourcePatchRequest } from "../../api/contracts/sources";
import { queryKeys } from "../../api/query-keys";
import { InlineAlert } from "../../components/inline-alert";
import { PageHeader } from "../../components/page-header";
import { PageState } from "../../components/page-state";
import { SourceDetailSections, type SourceDetailView } from "./source-detail-sections";
import { SourceEditForm } from "./source-edit-form";
import { SourceFilePicker } from "./source-file-picker";

type Notice = {
  tone: "info" | "success" | "warning" | "error";
  title: string;
  description?: string;
};

export default function SourceDetailPage() {
  const queryClient = useQueryClient();
  const { sourceId: sourceIdParameter = "" } = useParams();
  const sourceId = parseSourceId(sourceIdParameter);
  const [searchParams, setSearchParams] = useSearchParams();
  const [editOpen, setEditOpen] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [uploadFiles, setUploadFiles] = useState<UploadFile[]>([]);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [lastRun, setLastRun] = useState<SourceRun | null>(null);
  const view = parseView(searchParams.get("view"));

  const sourceQuery = useQuery({
    enabled: sourceId !== null,
    queryKey: queryKeys.sources.detail(sourceId ?? 0),
    queryFn: ({ signal }) => sourcesApi.detail(sourceId as number, signal),
  });
  const filesQuery = useQuery({
    enabled: sourceId !== null,
    queryKey: queryKeys.sources.files(sourceId ?? 0),
    queryFn: ({ signal }) => sourcesApi.files(sourceId as number, signal),
  });
  const runsQuery = useQuery({
    enabled: sourceId !== null,
    queryKey: queryKeys.sources.runs(sourceId ?? 0),
    queryFn: ({ signal }) => sourceImportsApi.listRuns(sourceId as number, signal),
    refetchInterval: (query) =>
      query.state.data?.some((run) => run.processingActive) ? 5_000 : false,
  });
  const credentialsQuery = useQuery({
    enabled: editOpen && sourceQuery.data?.sourceType === "GIT",
    queryKey: queryKeys.sources.credentials,
    queryFn: ({ signal }) => sourceImportsApi.listCredentials(signal),
  });

  const refreshSourceData = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.sources.root }),
      queryClient.invalidateQueries({ queryKey: queryKeys.sources.files(sourceId as number) }),
      queryClient.invalidateQueries({ queryKey: queryKeys.sources.runs(sourceId as number) }),
    ]);
  };

  const updateMutation = useMutation({
    mutationFn: (request: SourcePatchRequest) => sourcesApi.update(sourceId as number, request),
    onSuccess: async (source) => {
      setEditOpen(false);
      setNotice({ tone: "success", title: "资料源设置已保存", description: source.name });
      await refreshSourceData();
    },
  });
  const validationMutation = useMutation({
    mutationFn: () => sourceImportsApi.validate(sourceId as number),
    onSuccess: (result) => {
      setNotice({
        tone: result.valid ? "success" : "warning",
        title: result.valid ? "配置校验通过" : "配置校验未通过",
        description: [result.message, result.branch, result.gitCommit].filter(Boolean).join(" · "),
      });
    },
  });
  const materializedSyncMutation = useMutation({
    mutationFn: async () => {
      const validation = await sourceImportsApi.validate(sourceId as number);
      if (!validation.valid) throw new Error(validation.message);
      return sourceImportsApi.sync(sourceId as number);
    },
    onSuccess: async (run) => {
      setLastRun(run);
      setNotice({ tone: "success", title: "同步已提交", description: run.operationalNote ?? undefined });
      await refreshSourceData();
    },
  });
  const uploadSyncMutation = useMutation({
    mutationFn: () => sourceImportsApi.upload(uploadFiles, sourceId as number),
    onSuccess: async (run) => {
      setLastRun(run);
      setUploadFiles([]);
      setUploadOpen(false);
      setNotice({ tone: "success", title: "更新文件已提交", description: run.operationalNote ?? undefined });
      await refreshSourceData();
    },
  });

  if (sourceId === null) {
    return (
      <div className="page-frame source-detail-page">
        <PageHeader context={sourceIdParameter} title="资料源详情" />
        <PageState description="资料源编号必须是正整数" status="error" title="无法识别资料源" />
      </div>
    );
  }
  if (sourceQuery.isPending) {
    return (
      <div className="page-frame source-detail-page">
        <PageHeader context={sourceIdParameter} title="资料源详情" />
        <PageState status="loading" title="正在加载资料源" />
      </div>
    );
  }
  if (sourceQuery.isError) {
    return (
      <div className="page-frame source-detail-page">
        <PageHeader context={sourceIdParameter} title="资料源详情" />
        <PageState
          actionLabel="重试"
          description={resolveErrorMessage(sourceQuery.error)}
          onAction={() => void sourceQuery.refetch()}
          status="error"
          title="资料源加载失败"
        />
      </div>
    );
  }

  const source = sourceQuery.data;
  const isMaterializedSource = source.sourceType !== "UPLOAD";
  const operationError =
    updateMutation.error ??
    validationMutation.error ??
    materializedSyncMutation.error ??
    uploadSyncMutation.error;

  return (
    <div className="page-frame source-detail-page">
      <Link className="source-back-link" to="/library/sources">
        <ArrowLeft aria-hidden="true" size={16} />
        返回资料源
      </Link>
      <PageHeader
        actions={
          <>
            {isMaterializedSource ? (
              <button
                className="secondary-button compact-button"
                disabled={validationMutation.isPending}
                onClick={() => {
                  setNotice(null);
                  validationMutation.mutate();
                }}
                type="button"
              >
                <ShieldCheck aria-hidden="true" size={17} />
                {validationMutation.isPending ? "校验中" : "校验配置"}
              </button>
            ) : null}
            <button
              className="secondary-button compact-button"
              onClick={() => setEditOpen((open) => !open)}
              type="button"
            >
              <Pencil aria-hidden="true" size={17} />
              编辑
            </button>
            <button
              className="primary-button"
              disabled={materializedSyncMutation.isPending}
              onClick={() => {
                setNotice(null);
                if (isMaterializedSource) materializedSyncMutation.mutate();
                else setUploadOpen((open) => !open);
              }}
              type="button"
            >
              {isMaterializedSource ? (
                <RefreshCw aria-hidden="true" size={17} />
              ) : (
                <Upload aria-hidden="true" size={17} />
              )}
              {materializedSyncMutation.isPending
                ? "同步中"
                : isMaterializedSource
                  ? "同步资料"
                  : "更新文件"}
            </button>
          </>
        }
        context={source.sourceCode}
        title={source.displayName}
      />

      {notice ? (
        <InlineAlert description={notice.description} title={notice.title} tone={notice.tone} />
      ) : null}
      {operationError ? (
        <InlineAlert
          actionLabel="清除"
          description={resolveErrorMessage(operationError)}
          onAction={() => {
            updateMutation.reset();
            validationMutation.reset();
            materializedSyncMutation.reset();
            uploadSyncMutation.reset();
          }}
          title="操作未完成"
          tone="error"
        />
      ) : null}
      {lastRun ? <RunSubmittedNotice run={lastRun} /> : null}

      {editOpen ? (
        <SourceEditForm
          credentials={credentialsQuery.data ?? []}
          error={updateMutation.error ? resolveErrorMessage(updateMutation.error) : null}
          isPending={updateMutation.isPending}
          key={`${source.id}-${source.updatedAt ?? ""}`}
          onCancel={() => setEditOpen(false)}
          onSubmit={(request) => updateMutation.mutate(request)}
          source={source}
        />
      ) : null}

      {uploadOpen ? (
        <section aria-labelledby="source-upload-sync-heading" className="source-upload-sync">
          <div>
            <h2 id="source-upload-sync-heading">更新本地资料</h2>
            <p>所选文件将作为资料源 {source.sourceCode} 的新一次同步输入。</p>
          </div>
          <SourceFilePicker files={uploadFiles} onChange={setUploadFiles} />
          <div className="source-detail-form-actions">
            <button className="secondary-button compact-button" onClick={() => setUploadOpen(false)} type="button">
              取消
            </button>
            <button
              className="primary-button"
              disabled={uploadFiles.length === 0 || uploadSyncMutation.isPending}
              onClick={() => uploadSyncMutation.mutate()}
              type="button"
            >
              {uploadSyncMutation.isPending ? "正在提交" : "提交更新"}
            </button>
          </div>
        </section>
      ) : null}

      <SourceDetailSections
        files={filesQuery.data}
        filesError={filesQuery.error ? resolveErrorMessage(filesQuery.error) : null}
        filesPending={filesQuery.isPending}
        onFilesRetry={() => void filesQuery.refetch()}
        onRunsRetry={() => void runsQuery.refetch()}
        onViewChange={(nextView) => {
          const next = new URLSearchParams(searchParams);
          if (nextView === "files") next.delete("view");
          else next.set("view", nextView);
          setSearchParams(next);
        }}
        runs={runsQuery.data}
        runsError={runsQuery.error ? resolveErrorMessage(runsQuery.error) : null}
        runsFetching={runsQuery.isFetching}
        runsPending={runsQuery.isPending}
        source={source}
        view={view}
      />
    </div>
  );
}

function RunSubmittedNotice({ run }: { run: SourceRun }) {
  return (
    <div className="source-run-submitted" role="status">
      <span>
        运行 <strong>#{run.runId}</strong> · {run.displayStatusLabel} · {run.currentStepLabel}
      </span>
      <Link to={`/activity?kind=source-run&id=${run.runId}`}>查看处理任务</Link>
    </div>
  );
}

function parseSourceId(value: string) {
  if (!/^\d+$/.test(value)) return null;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null;
}

function parseView(value: string | null): SourceDetailView {
  return value === "runs" || value === "config" ? value : "files";
}

function resolveErrorMessage(error: unknown) {
  if (isApiError(error)) return error.message;
  return error instanceof Error ? error.message : "请求失败，请稍后重试";
}
