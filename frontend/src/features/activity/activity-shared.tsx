import { AlertCircle, CheckCircle2, Clock3, LoaderCircle } from "lucide-react";
import { type ReactNode } from "react";

import { formatDateTime } from "./activity-utils";

export interface ActivityListEntry {
  id: string;
  title: string;
  meta: string;
  status: string;
  tone: string;
  progress?: string | null;
  time?: string | null;
}

interface ActivityListProps {
  entries: ActivityListEntry[];
  label: string;
  selectedId: string | null;
  onSelect: (id: string) => void;
}

export function ActivityList({
  entries,
  label,
  selectedId,
  onSelect,
}: ActivityListProps) {
  return (
    <ul aria-label={label} className="activity-list">
      {entries.map((entry) => (
        <li key={entry.id}>
          <button
            aria-current={selectedId === entry.id ? "true" : undefined}
            className={selectedId === entry.id ? "is-selected" : undefined}
            onClick={() => onSelect(entry.id)}
            type="button"
          >
            <span className="activity-list-main">
              <strong>{entry.title}</strong>
              <span>{entry.meta}</span>
            </span>
            <span className={`activity-status is-${normalizeTone(entry.tone)}`}>
              {entry.status}
            </span>
            <span className="activity-list-foot">
              <span>{entry.progress || "--"}</span>
              <time dateTime={entry.time ?? undefined}>
                {formatDateTime(entry.time)}
              </time>
            </span>
          </button>
        </li>
      ))}
    </ul>
  );
}

export function ActivityLayout({
  list,
  detail,
}: {
  list: ReactNode;
  detail: ReactNode;
}) {
  return (
    <div className="activity-layout">
      <section aria-label="任务列表" className="activity-list-column">
        {list}
      </section>
      <section aria-label="任务详情" className="activity-detail-column">
        {detail}
      </section>
    </div>
  );
}

export function ActivityToolbar({
  children,
  count,
}: {
  children?: ReactNode;
  count?: number;
}) {
  return (
    <div className="activity-toolbar">
      {children}
      <span aria-live="polite" className="result-count">
        {count === undefined ? "-- 项" : `${count} 项`}
      </span>
    </div>
  );
}

export function LimitField({
  value,
  onChange,
}: {
  value: number;
  onChange: (value: number) => void;
}) {
  return (
    <label className="filter-field">
      <span>最近</span>
      <select
        onChange={(event) => onChange(Number(event.target.value))}
        value={value}
      >
        {[10, 20, 50].map((option) => (
          <option key={option} value={option}>{option} 条</option>
        ))}
      </select>
    </label>
  );
}

export interface ActivityStep {
  key: string;
  label: string;
  status: string;
  detail: string | null;
}

export function ActivitySteps({ steps }: { steps: ActivityStep[] }) {
  if (!steps.length) return <p className="activity-muted">暂无步骤记录</p>;
  return (
    <ol aria-label="任务执行步骤" className="activity-steps">
      {steps.map((step) => (
        <li className={`is-${step.status.toLowerCase()}`} key={step.key}>
          <StepIcon status={step.status} />
          <div>
            <strong>{step.label}</strong>
            {step.detail ? <p>{step.detail}</p> : null}
          </div>
        </li>
      ))}
    </ol>
  );
}

export function ActivityFacts({
  children,
}: {
  children: ReactNode;
}) {
  return <dl className="activity-facts">{children}</dl>;
}

export function ActivityFact({
  label,
  children,
}: {
  label: string;
  children: ReactNode;
}) {
  return <div><dt>{label}</dt><dd>{children}</dd></div>;
}

export function DetailPlaceholder({ title }: { title: string }) {
  return (
    <div className="activity-detail-placeholder">
      <Clock3 aria-hidden="true" size={22} />
      <p>{title}</p>
    </div>
  );
}

function StepIcon({ status }: { status: string }) {
  if (status === "COMPLETED") return <CheckCircle2 aria-hidden="true" size={18} />;
  if (status === "ACTIVE") return <LoaderCircle aria-hidden="true" className="activity-step-spinner" size={18} />;
  if (status === "FAILED") return <AlertCircle aria-hidden="true" size={18} />;
  return <Clock3 aria-hidden="true" size={18} />;
}

function normalizeTone(tone: string) {
  if (["success", "warning", "danger", "error", "info"].includes(tone)) {
    return tone === "error" ? "danger" : tone;
  }
  return "neutral";
}
