import type { ReactNode } from "react";

import { PageState } from "../../components/page-state";
import { resolveQualityError } from "./quality-utils";

interface MetricItem {
  label: string;
  value: ReactNode;
  tone?: "default" | "warning" | "danger";
}

export function MetricGrid({ items, label }: { items: MetricItem[]; label: string }) {
  return (
    <dl aria-label={label} className="quality-metric-grid">
      {items.map((item) => (
        <div className={item.tone ? `is-${item.tone}` : undefined} key={item.label}>
          <dt>{item.label}</dt>
          <dd>{item.value}</dd>
        </div>
      ))}
    </dl>
  );
}

export function QualitySection({
  title,
  context,
  children,
  actions,
}: {
  title: string;
  context?: string;
  children: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <section className="quality-section">
      <header>
        <div>
          <h2>{title}</h2>
          {context ? <p>{context}</p> : null}
        </div>
        {actions ? <div className="quality-section-actions">{actions}</div> : null}
      </header>
      {children}
    </section>
  );
}

export function QueryFailure({
  title,
  error,
  onRetry,
}: {
  title: string;
  error: unknown;
  onRetry: () => void;
}) {
  return (
    <PageState
      actionLabel="重试"
      description={resolveQualityError(error)}
      onAction={onRetry}
      status="error"
      title={title}
    />
  );
}
