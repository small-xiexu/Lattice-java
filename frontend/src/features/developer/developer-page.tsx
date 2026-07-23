import { useQuery } from "@tanstack/react-query";
import {
  Braces,
  Copy,
  HeartPulse,
  Network,
  RefreshCw,
  Terminal,
  type LucideIcon,
} from "lucide-react";
import { useState } from "react";
import { useSearchParams } from "react-router-dom";

import {
  developerAccessApi,
  type Health,
} from "../../api/contracts/developer-access";
import { queryKeys } from "../../api/query-keys";
import { PageHeader } from "../../components/page-header";
import { llmErrorMessage } from "../settings/llm-settings-utils";

type DeveloperSection = "mcp" | "cli" | "http";

interface DeveloperSectionDefinition {
  id: DeveloperSection;
  label: string;
  detail: string;
  icon: LucideIcon;
}

const DEVELOPER_SECTIONS: DeveloperSectionDefinition[] = [
  { id: "mcp", label: "MCP", detail: "Streamable HTTP 与 STDIO Bridge", icon: Network },
  { id: "cli", label: "CLI", detail: "远程命令与环境变量", icon: Terminal },
  { id: "http", label: "HTTP API", detail: "同步 JSON 请求", icon: Braces },
];

export default function DeveloperPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [copyMessage, setCopyMessage] = useState("");
  const section = normalizeSection(searchParams.get("section"));
  const origin = resolveOrigin();
  const healthQuery = useQuery({
    queryKey: queryKeys.health,
    queryFn: ({ signal }) => developerAccessApi.getHealth(signal),
  });

  const chooseSection = (nextSection: DeveloperSection) => {
    const next = new URLSearchParams(searchParams);
    next.set("section", nextSection);
    setSearchParams(next);
    setCopyMessage("");
  };

  const copy = async (label: string, content: string) => {
    try {
      await writeClipboard(content);
      setCopyMessage(`${label}已复制`);
    } catch {
      setCopyMessage(`${label}复制失败，请手动选择代码块内容`);
    }
  };

  return (
    <div className="page-frame developer-page">
      <PageHeader
        actions={(
          <button
            aria-label="刷新健康状态"
            className="icon-button developer-refresh-button"
            onClick={() => void healthQuery.refetch()}
            title="刷新健康状态"
            type="button"
          >
            <RefreshCw aria-hidden="true" size={18} />
          </button>
        )}
        context="HTTP API · MCP · CLI"
        title="开发者接入"
      />

      <div className="developer-layout">
        <nav aria-label="开发者接入目录" className="developer-directory">
          <p>接入方式</p>
          {DEVELOPER_SECTIONS.map((item) => {
            const Icon = item.icon;
            return (
              <button
                aria-current={section === item.id ? "page" : undefined}
                className={section === item.id ? "is-selected" : ""}
                key={item.id}
                onClick={() => chooseSection(item.id)}
                type="button"
              >
                <Icon aria-hidden="true" size={17} />
                <span><strong>{item.label}</strong><small>{item.detail}</small></span>
              </button>
            );
          })}
        </nav>

        <section aria-label="开发者接入文档" className="developer-content">
          {section === "mcp" ? <McpSection copy={copy} origin={origin} /> : null}
          {section === "cli" ? <CliSection copy={copy} origin={origin} /> : null}
          {section === "http" ? <HttpSection copy={copy} origin={origin} /> : null}
          <p aria-live="polite" className="developer-copy-status" role="status">{copyMessage}</p>
        </section>

        <DeveloperRuntimeAside
          error={healthQuery.error}
          health={healthQuery.data}
          loading={healthQuery.isLoading || healthQuery.isFetching}
          origin={origin}
          onCopy={copy}
        />
      </div>
    </div>
  );
}

function McpSection({ origin, copy }: SectionProps) {
  const mcpUrl = `${origin}/mcp`;
  const directConfig = JSON.stringify({
    mcpServers: {
      "lattice-java": {
        type: "streamable-http",
        url: mcpUrl,
      },
    },
  }, null, 2);
  const bridgeCommand = `./bin/lattice-mcp-bridge ${mcpUrl}`;
  const bridgeConfig = JSON.stringify({
    mcpServers: {
      "lattice-java": {
        command: "bash",
        args: ["-lc", `cd /path/to/Lattice-java && ./bin/lattice-mcp-bridge ${mcpUrl}`],
      },
    },
  }, null, 2);

  return (
    <section aria-labelledby="developer-mcp-title" className="developer-document">
      <header><Network aria-hidden="true" size={20} /><div><h2 id="developer-mcp-title">MCP 接入</h2><p>服务端暴露 Streamable HTTP，也可由仓库脚本转换为 STDIO。</p></div></header>
      <TemplateBlock content={directConfig} label="MCP HTTP 配置" onCopy={copy} />
      <TemplateBlock content={bridgeCommand} label="STDIO Bridge 命令" onCopy={copy} />
      <TemplateBlock content={bridgeConfig} label="STDIO 客户端配置" onCopy={copy} />
      <ol className="developer-verification-list">
        <li>连接后执行 <code>tools/list</code>。</li>
        <li>调用 <code>lattice_status</code> 核对服务与知识库状态。</li>
        <li>调用 <code>lattice_query</code>，并处理返回的 pending 记录。</li>
      </ol>
    </section>
  );
}

function CliSection({ origin, copy }: SectionProps) {
  const statusCommand = `./bin/lattice-cli status --server ${origin}`;
  const queryCommand = `./bin/lattice-cli query --server ${origin} "如何验证 Lattice 开发者接入？"`;
  const environmentExample = [
    `export LATTICE_SERVER_URL=${origin}`,
    "./bin/lattice-cli status",
    "./bin/lattice-cli query \"如何验证 Lattice 开发者接入？\"",
    "./bin/lattice-cli source-list --page 1 --size 20",
  ].join("\n");

  return (
    <section aria-labelledby="developer-cli-title" className="developer-document">
      <header><Terminal aria-hidden="true" size={20} /><div><h2 id="developer-cli-title">CLI 接入</h2><p>仓库脚本会复用当前构建产物，并支持参数或环境变量指定远端服务。</p></div></header>
      <TemplateBlock content={statusCommand} label="状态检查命令" onCopy={copy} />
      <TemplateBlock content={queryCommand} label="首次问答命令" onCopy={copy} />
      <TemplateBlock content={environmentExample} label="环境变量示例" onCopy={copy} />
    </section>
  );
}

function HttpSection({ origin, copy }: SectionProps) {
  const queryExample = [
    `curl -X POST "${origin}/api/v1/query" \\`,
    "  -H \"Content-Type: application/json\" \\",
    "  -d '{\"question\":\"如何验证 Lattice 开发者接入？\"}'",
  ].join("\n");
  const healthExample = `curl "${origin}/actuator/health"`;

  return (
    <section aria-labelledby="developer-http-title" className="developer-document">
      <header><Braces aria-hidden="true" size={20} /><div><h2 id="developer-http-title">HTTP API 接入</h2><p>查询接口接收同步 JSON 请求；健康检查由 Actuator 提供。</p></div></header>
      <TemplateBlock content={queryExample} label="最小查询请求" onCopy={copy} />
      <TemplateBlock content={healthExample} label="健康检查请求" onCopy={copy} />
    </section>
  );
}

function DeveloperRuntimeAside({ origin, health, error, loading, onCopy }: RuntimeAsideProps) {
  const status = health?.status.toUpperCase();
  const unhealthyComponents = Object.entries(health?.components ?? {})
    .filter(([, component]) => component.status?.toUpperCase() !== "UP")
    .map(([name]) => name);
  const stateClass = error ? "is-error" : status === "UP" ? "is-success" : "is-warning";
  const stateLabel = loading ? "检查中" : error ? "不可达" : status === "UP" ? "正常" : status ?? "未知";

  return (
    <aside aria-label="当前接入信息" className="developer-runtime">
      <header><HeartPulse aria-hidden="true" size={18} /><h2>当前接入信息</h2></header>
      <RuntimeAddress label="服务地址" onCopy={onCopy} value={origin} />
      <RuntimeAddress label="MCP 地址" onCopy={onCopy} value={`${origin}/mcp`} />
      <section className="developer-health-block">
        <span>Actuator 健康状态</span>
        <strong className={stateClass}>{stateLabel}</strong>
        {error ? <p>{llmErrorMessage(error)}</p> : null}
        {!error && unhealthyComponents.length > 0 ? <p>异常组件：{unhealthyComponents.join("、")}</p> : null}
        {!error && !loading && unhealthyComponents.length === 0 ? <p><code>/actuator/health</code> · {status ?? "UNKNOWN"}</p> : null}
      </section>
    </aside>
  );
}

function RuntimeAddress({ label, value, onCopy }: { label: string; value: string; onCopy: CopyHandler }) {
  return (
    <section className="developer-runtime-address">
      <span>{label}</span>
      <code>{value}</code>
      <button aria-label={`复制${label}`} className="icon-button" onClick={() => void onCopy(label, value)} title={`复制${label}`} type="button">
        <Copy aria-hidden="true" size={15} />
      </button>
    </section>
  );
}

function TemplateBlock({ label, content, onCopy }: { label: string; content: string; onCopy: CopyHandler }) {
  return (
    <section aria-label={label} className="developer-template">
      <header><h3>{label}</h3><button className="secondary-button" onClick={() => void onCopy(label, content)} type="button"><Copy aria-hidden="true" size={15} />复制</button></header>
      <pre tabIndex={0}><code>{content}</code></pre>
    </section>
  );
}

interface SectionProps {
  origin: string;
  copy: CopyHandler;
}

interface RuntimeAsideProps {
  origin: string;
  health?: Health;
  error: Error | null;
  loading: boolean;
  onCopy: CopyHandler;
}

type CopyHandler = (label: string, content: string) => Promise<void>;

function normalizeSection(value: string | null): DeveloperSection {
  return value === "cli" || value === "http" ? value : "mcp";
}

function resolveOrigin() {
  return typeof window === "undefined" ? "" : window.location.origin;
}

async function writeClipboard(content: string) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(content);
    return;
  }
  const textarea = document.createElement("textarea");
  textarea.value = content;
  textarea.readOnly = true;
  textarea.style.position = "fixed";
  textarea.style.top = "-9999px";
  document.body.appendChild(textarea);
  textarea.select();
  const copied = document.execCommand("copy");
  textarea.remove();
  if (!copied) throw new Error("copy failed");
}
