import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  AlertTriangle,
  Check,
  CircleAlert,
  DatabaseZap,
  RefreshCw,
  RotateCcw,
  Save,
} from "lucide-react";
import { useState, type FormEvent } from "react";

import { llmSettingsApi, type LlmModel } from "../../api/contracts/llm-settings";
import {
  vectorSettingsApi,
  type VectorConfig,
  type VectorIndexRebuildResult,
  type VectorIndexStatus,
} from "../../api/contracts/vector-settings";
import { queryKeys } from "../../api/query-keys";
import { PageHeader } from "../../components/page-header";
import { PageState } from "../../components/page-state";
import { ArticleGovernanceDialog } from "../library/article-governance-dialog";
import {
  confirmDiscardChanges,
  formatLlmDateTime,
  llmErrorMessage,
  useBeforeUnloadWarning,
} from "./llm-settings-utils";

interface VectorConfigForm {
  vectorEnabled: boolean;
  embeddingModelProfileId: string;
  operator: string;
}

const EMPTY_FORM: VectorConfigForm = {
  vectorEnabled: false,
  embeddingModelProfileId: "",
  operator: "admin",
};

export default function VectorSettingsPage() {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<VectorConfigForm>(EMPTY_FORM);
  const [baseline, setBaseline] = useState<VectorConfigForm>(EMPTY_FORM);
  const [loadedKey, setLoadedKey] = useState("initial");
  const [formError, setFormError] = useState("");
  const [saveMessage, setSaveMessage] = useState("");
  const [truncateFirst, setTruncateFirst] = useState(false);
  const [rebuildOperator, setRebuildOperator] = useState("admin");
  const [rebuildOpen, setRebuildOpen] = useState(false);
  const [rebuildError, setRebuildError] = useState("");
  const [rebuildResult, setRebuildResult] = useState<VectorIndexRebuildResult | null>(null);

  const configQuery = useQuery({
    queryKey: queryKeys.settings.vector.config,
    queryFn: ({ signal }) => vectorSettingsApi.getConfig(signal),
  });
  const statusQuery = useQuery({
    queryKey: queryKeys.settings.vector.status,
    queryFn: ({ signal }) => vectorSettingsApi.getStatus(signal),
  });
  const modelsQuery = useQuery({
    queryKey: queryKeys.settings.llm.models,
    queryFn: ({ signal }) => llmSettingsApi.listModels(signal),
  });
  const config = configQuery.data;
  const status = statusQuery.data;
  const embeddingModels = (modelsQuery.data?.items ?? []).filter((model) => model.modelKind === "EMBEDDING");
  const dirty = JSON.stringify(form) !== JSON.stringify(baseline);
  useBeforeUnloadWarning(dirty);

  const saveMutation = useMutation({
    mutationFn: () => {
      const validationError = validateConfigForm(form, embeddingModels);
      if (validationError) throw new Error(validationError);
      return vectorSettingsApi.updateConfig({
        vectorEnabled: form.vectorEnabled,
        embeddingModelProfileId: Number(form.embeddingModelProfileId),
        operator: form.operator.trim(),
      });
    },
    onSuccess: (saved) => {
      const next = configToForm(saved, form.operator);
      queryClient.setQueryData(queryKeys.settings.vector.config, saved);
      setLoadedKey(configIdentity(saved));
      setForm(next);
      setBaseline(next);
      setFormError("");
      setSaveMessage("向量配置已保存并应用到运行时");
      setRebuildResult(null);
      void statusQuery.refetch();
    },
    onError: (error) => {
      setSaveMessage("");
      setFormError(llmErrorMessage(error));
    },
  });

  const rebuildMutation = useMutation({
    mutationFn: () => vectorSettingsApi.rebuild({
      truncateFirst,
      operator: rebuildOperator.trim(),
    }),
    onSuccess: (result) => {
      setRebuildOpen(false);
      setRebuildError("");
      setRebuildResult(result);
      void queryClient.invalidateQueries({ queryKey: queryKeys.settings.vector.root });
    },
    onError: (error) => setRebuildError(llmErrorMessage(error)),
  });

  if (config && loadedKey !== configIdentity(config)) {
    const next = configToForm(config);
    setLoadedKey(configIdentity(config));
    setForm(next);
    setBaseline(next);
    setRebuildOperator(config.updatedBy?.trim() || "admin");
    setFormError("");
    setSaveMessage("");
  }

  const loading = configQuery.isLoading || statusQuery.isLoading || modelsQuery.isLoading;
  const queryError = configQuery.error ?? statusQuery.error ?? modelsQuery.error;
  if (loading) return <PageState status="loading" title="正在读取向量配置" />;
  if (queryError || !config || !status) {
    return (
      <PageState
        actionLabel="重新加载"
        description={queryError ? llmErrorMessage(queryError) : "服务端未返回完整向量状态"}
        onAction={() => void refreshQueries(configQuery.refetch, statusQuery.refetch, modelsQuery.refetch)}
        status="error"
        title="向量配置读取失败"
      />
    );
  }

  const selectedModel = embeddingModels.find((model) => model.id === Number(form.embeddingModelProfileId));
  const selectedDimensions = selectedModel?.expectedDimensions ?? null;
  const dimensionsWillChange = selectedDimensions !== null
    && status.schemaDimensions !== null
    && selectedDimensions !== status.schemaDimensions;

  const refresh = () => {
    if (!confirmDiscardChanges(dirty)) return;
    setLoadedKey("manual-refresh");
    setRebuildResult(null);
    void queryClient.invalidateQueries({ queryKey: queryKeys.settings.vector.root });
    void queryClient.invalidateQueries({ queryKey: queryKeys.settings.llm.models });
  };

  const prepareRebuild = () => {
    const error = validateRebuild(status, truncateFirst, rebuildOperator);
    if (error) {
      setRebuildError(error);
      return;
    }
    rebuildMutation.reset();
    setRebuildError("");
    setRebuildOpen(true);
  };

  return (
    <div className="page-frame vector-settings-page">
      <PageHeader
        actions={(
          <button
            aria-label="刷新向量配置"
            className="icon-button vector-refresh-button"
            disabled={configQuery.isFetching || statusQuery.isFetching || modelsQuery.isFetching}
            onClick={refresh}
            title="刷新向量配置"
            type="button"
          >
            <RefreshCw aria-hidden="true" size={18} />
          </button>
        )}
        context="runtime config · schema diagnostics · synchronous rebuild"
        title="向量索引"
      />

      <VectorStatusSection status={status} />

      <section aria-labelledby="vector-config-title" className="vector-section vector-config-section">
        <header className="vector-section-header">
          <div>
            <h2 id="vector-config-title">运行配置</h2>
            <p>{config.configSource} · 最近更新 {formatLlmDateTime(config.updatedAt)}</p>
          </div>
          {dirty ? <span className="llm-dirty-mark">未保存</span> : null}
        </header>
        <form className="vector-config-form" noValidate onSubmit={(event) => submit(event, saveMutation.mutate)}>
          <label className="form-field">
            <span>Embedding 模型</span>
            <select
              onChange={(event) => setForm({ ...form, embeddingModelProfileId: event.target.value })}
              required
              value={form.embeddingModelProfileId}
            >
              <option value="">请选择</option>
              {config.embeddingModelProfileId && !embeddingModels.some((model) => model.id === config.embeddingModelProfileId) ? (
                <option value={config.embeddingModelProfileId}>当前配置 #{config.embeddingModelProfileId}（档案不可用）</option>
              ) : null}
              {embeddingModels.map((model) => (
                <option disabled={!model.enabled} key={model.id} value={model.id}>
                  {model.modelCode} · {model.modelName} · {model.expectedDimensions ?? "未配置"} 维{model.enabled ? "" : "（已停用）"}
                </option>
              ))}
            </select>
          </label>
          <label className="form-field">
            <span>操作人</span>
            <input
              autoComplete="off"
              onChange={(event) => setForm({ ...form, operator: event.target.value })}
              required
              value={form.operator}
            />
          </label>
          <label className="checkbox-field vector-enabled-toggle">
            <input
              checked={form.vectorEnabled}
              onChange={(event) => setForm({ ...form, vectorEnabled: event.target.checked })}
              type="checkbox"
            />
            启用向量检索
          </label>
          <dl className="vector-selected-model">
            <div><dt>档案类型</dt><dd>{selectedModel ? selectedModel.modelKind : "EMBEDDING"}</dd></div>
            <div><dt>模型名</dt><dd>{selectedModel?.modelName ?? config.modelName ?? "--"}</dd></div>
            <div><dt>目标维度</dt><dd>{selectedDimensions ?? config.profileDimensions ?? "--"}</dd></div>
          </dl>
          {dimensionsWillChange ? (
            <p className="vector-notice is-warning" role="status">
              <AlertTriangle aria-hidden="true" size={17} />
              目标维度 {selectedDimensions} 与当前 schema {status.schemaDimensions} 不一致，保存后需清空旧向量并重建。
            </p>
          ) : null}
          {!form.vectorEnabled ? (
            <p className="vector-notice is-warning" role="status">
              <AlertTriangle aria-hidden="true" size={17} />
              禁用后 Query 将不再使用向量召回通道。
            </p>
          ) : null}
          {saveMessage ? <p className="vector-notice is-success" role="status"><Check aria-hidden="true" size={17} />{saveMessage}</p> : null}
          {formError ? <p className="vector-notice is-error" role="alert">{formError}</p> : null}
          <div className="vector-form-actions">
            <button className="primary-button" disabled={!dirty || saveMutation.isPending || rebuildMutation.isPending} type="submit">
              <Save aria-hidden="true" size={16} />{saveMutation.isPending ? "正在保存" : "保存配置"}
            </button>
            <button className="secondary-button compact-button" disabled={!dirty || saveMutation.isPending} onClick={() => setForm(baseline)} type="button">
              <RotateCcw aria-hidden="true" size={16} />撤销改动
            </button>
          </div>
        </form>
      </section>

      <section aria-labelledby="vector-rebuild-title" className="vector-section vector-maintenance-section">
        <header className="vector-section-header">
          <div>
            <h2 id="vector-rebuild-title">索引重建</h2>
            <p>同步执行 · 当前无取消与任务轮询契约</p>
          </div>
          <DatabaseZap aria-hidden="true" size={20} />
        </header>
        <div className="vector-rebuild-form">
          <label className="form-field">
            <span>操作人</span>
            <input autoComplete="off" onChange={(event) => setRebuildOperator(event.target.value)} value={rebuildOperator} />
          </label>
          <label className="checkbox-field vector-truncate-toggle">
            <input checked={truncateFirst} onChange={(event) => setTruncateFirst(event.target.checked)} type="checkbox" />
            先清空文章与分块向量
          </label>
          <dl className="vector-rebuild-scope">
            <div><dt>目标文章</dt><dd>{status.articleCount}</dd></div>
            <div><dt>现有文章向量</dt><dd>{status.indexedArticleCount}</dd></div>
            <div><dt>执行模型</dt><dd>{status.configuredModelName ?? "--"}</dd></div>
          </dl>
          {rebuildError && !rebuildOpen ? <p className="vector-notice is-error" role="alert">{rebuildError}</p> : null}
          <div className="vector-form-actions">
            <button
              className="danger-button"
              disabled={rebuildMutation.isPending || saveMutation.isPending}
              onClick={prepareRebuild}
              type="button"
            >
              <DatabaseZap aria-hidden="true" size={16} />准备重建
            </button>
          </div>
          {rebuildResult ? <RebuildResult result={rebuildResult} /> : null}
        </div>
      </section>

      {rebuildOpen ? (
        <ArticleGovernanceDialog
          confirmLabel="确认重建索引"
          description="重建会重新计算全部文章与分块向量，并清空 Query 与提示词缓存。"
          destructive
          error={rebuildMutation.isError ? rebuildError : undefined}
          onClose={() => setRebuildOpen(false)}
          onConfirm={() => rebuildMutation.mutate()}
          pending={rebuildMutation.isPending}
          title="重建向量索引"
        >
          <dl className="governance-impact-summary">
            <div><dt>目标范围</dt><dd>{status.articleCount} 篇文章及其全部分块</dd></div>
            <div><dt>执行模型</dt><dd>{status.configuredModelName ?? "--"} / {status.profileDimensions ?? "--"} 维</dd></div>
            <div><dt>当前索引</dt><dd>{status.indexedArticleCount} 篇文章 / {status.indexedModelNames.join("、") || "无模型记录"}</dd></div>
            <div><dt>执行模式</dt><dd>{truncateFirst ? "先清空旧向量，再执行全量重建" : "保留旧向量并执行全量覆盖"}</dd></div>
            <div><dt>操作人</dt><dd>{rebuildOperator.trim()}</dd></div>
          </dl>
        </ArticleGovernanceDialog>
      ) : null}
    </div>
  );
}

function VectorStatusSection({ status }: { status: VectorIndexStatus }) {
  const coverage = status.articleCount === 0
    ? 100
    : Math.min(100, Math.round((status.indexedArticleCount / status.articleCount) * 100));
  const checks = [
    ["运行开关", status.vectorEnabled, status.vectorEnabled ? "已启用" : "已禁用"],
    ["数据库类型", status.vectorTypeAvailable, status.vectorTypeAvailable ? "vector 可用" : "vector 不可用"],
    ["索引表", status.vectorIndexTableAvailable, status.vectorIndexTableAvailable ? "表可访问" : "表不可用"],
    ["索引能力", status.indexingAvailable, status.indexingAvailable ? "可执行" : "不可执行"],
  ] as const;
  return (
    <section aria-labelledby="vector-status-title" className="vector-section vector-status-section">
      <header className="vector-section-header">
        <div><h2 id="vector-status-title">运行状态</h2><p>最近索引 {formatLlmDateTime(status.latestUpdatedAt)}</p></div>
        <span className={`vector-health-badge ${status.indexingAvailable ? "is-ready" : "is-blocked"}`}>
          {status.indexingAvailable ? "可维护" : "受阻"}
        </span>
      </header>
      <ol className="vector-availability-chain">
        {checks.map(([label, passed, detail]) => (
          <li className={passed ? "is-ready" : "is-blocked"} key={label}>
            {passed ? <Check aria-hidden="true" size={15} /> : <CircleAlert aria-hidden="true" size={15} />}
            <span><strong>{label}</strong><small>{detail}</small></span>
          </li>
        ))}
      </ol>
      <div className="vector-status-details">
        <div className="vector-coverage-block">
          <span>文章覆盖</span>
          <strong>{status.indexedArticleCount} / {status.articleCount}</strong>
          <div aria-label={`向量覆盖率 ${coverage}%`} className="vector-coverage-track" role="img"><span style={{ width: `${coverage}%` }} /></div>
        </div>
        <dl className="vector-diagnostics">
          <div><dt>配置模型</dt><dd>{status.configuredModelName ?? "--"}</dd></div>
          <div><dt>Profile / Schema</dt><dd>{status.profileDimensions ?? "--"} / {status.schemaDimensions ?? "--"}</dd></div>
          <div><dt>向量列</dt><dd><code>{status.embeddingColumnType || "--"}</code></dd></div>
          <div><dt>ANN 索引</dt><dd>{status.annIndexReady ? status.annIndexType || "就绪" : "未就绪"}</dd></div>
          <div><dt>历史模型</dt><dd>{status.indexedModelNames.join("、") || "--"}</dd></div>
          <div><dt>维度一致性</dt><dd className={status.dimensionsConsistent ? "is-success" : "is-warning"}>{status.dimensionsConsistent ? "一致" : "不一致"}</dd></div>
        </dl>
      </div>
      {!status.dimensionsConsistent ? <p className="vector-notice is-error" role="alert"><CircleAlert aria-hidden="true" size={17} />Profile 与 schema 维度不一致，已有历史向量时必须清空后重建。</p> : null}
      {!status.annIndexReady ? <p className="vector-notice is-warning" role="status"><AlertTriangle aria-hidden="true" size={17} />ANN 索引未就绪，向量相似度查询可能退化为全表扫描。</p> : null}
    </section>
  );
}

function RebuildResult({ result }: { result: VectorIndexRebuildResult }) {
  return (
    <section aria-label="最近重建结果" className="vector-rebuild-result" role="status">
      <header><Check aria-hidden="true" size={17} /><strong>重建完成</strong><time dateTime={result.rebuiltAt}>{formatLlmDateTime(result.rebuiltAt)}</time></header>
      <dl>
        <div><dt>文章向量</dt><dd>{result.previousIndexedArticleCount} {"->"} {result.indexedArticleCount}</dd></div>
        <div><dt>分块向量</dt><dd>{result.previousIndexedChunkCount} {"->"} {result.indexedChunkCount}</dd></div>
        <div><dt>执行模式</dt><dd>{result.truncateFirst ? "清空后重建" : "覆盖重建"}</dd></div>
        <div><dt>模型</dt><dd>{result.configuredModelName ?? "--"}</dd></div>
      </dl>
    </section>
  );
}

function configToForm(config: VectorConfig, operator?: string): VectorConfigForm {
  return {
    vectorEnabled: config.vectorEnabled,
    embeddingModelProfileId: config.embeddingModelProfileId === null ? "" : String(config.embeddingModelProfileId),
    operator: operator?.trim() || config.updatedBy?.trim() || "admin",
  };
}

function configIdentity(config: VectorConfig) {
  return [config.embeddingModelProfileId, config.vectorEnabled, config.updatedAt ?? "", config.updatedBy ?? ""].join(":");
}

function validateConfigForm(form: VectorConfigForm, embeddingModels: LlmModel[]) {
  const profileId = Number(form.embeddingModelProfileId);
  if (!Number.isInteger(profileId) || profileId <= 0) return "请选择有效的 Embedding 模型";
  const model = embeddingModels.find((candidate) => candidate.id === profileId);
  if (!model) return "所选模型不是可用的 Embedding 模型档案";
  if (!model.enabled) return "所选 Embedding 模型已停用";
  if (!model.expectedDimensions || model.expectedDimensions <= 0) return "所选 Embedding 模型未配置有效维度";
  if (!form.operator.trim()) return "请填写操作人";
  return "";
}

function validateRebuild(status: VectorIndexStatus, truncateFirst: boolean, operator: string) {
  if (!operator.trim()) return "请填写重建操作人";
  if (!status.vectorEnabled) return "当前未启用向量索引";
  if (!status.vectorTypeAvailable) return "当前数据库未启用 vector 类型";
  if (!status.vectorIndexTableAvailable) return "当前 schema 不存在向量索引表";
  if (!status.indexingAvailable) return "当前 embedding 模型或索引能力不可用";
  if (!status.profileDimensions || status.profileDimensions <= 0) return "当前 Embedding 模型未配置有效维度";
  if (!status.dimensionsConsistent && status.indexedArticleCount > 0 && !truncateFirst) {
    return "维度不一致且存在历史向量，请勾选“先清空文章与分块向量”";
  }
  return "";
}

function submit(event: FormEvent, save: () => void) {
  event.preventDefault();
  save();
}

async function refreshQueries(...refetchers: Array<() => Promise<unknown>>) {
  await Promise.all(refetchers.map((refetch) => refetch()));
}
