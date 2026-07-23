import { AlertTriangle, Inbox, LoaderCircle, type LucideIcon } from "lucide-react";

interface PageStateProps {
  status: "loading" | "empty" | "error";
  title: string;
  description?: string;
  actionLabel?: string;
  onAction?: () => void;
}

const STATE_ICONS: Record<PageStateProps["status"], LucideIcon> = {
  loading: LoaderCircle,
  empty: Inbox,
  error: AlertTriangle,
};

export function PageState({
  status,
  title,
  description,
  actionLabel,
  onAction,
}: PageStateProps) {
  const Icon = STATE_ICONS[status];
  return (
    <section
      aria-live={status === "loading" ? "polite" : undefined}
      className={`page-state is-${status}`}
      role={status === "error" ? "alert" : status === "loading" ? "status" : undefined}
    >
      <Icon
        aria-hidden="true"
        className={status === "loading" ? "state-loading-icon" : undefined}
        size={22}
        strokeWidth={1.75}
      />
      <h2>{title}</h2>
      {description ? <p>{description}</p> : null}
      {actionLabel && onAction ? (
        <button className="secondary-button" onClick={onAction} type="button">
          {actionLabel}
        </button>
      ) : null}
    </section>
  );
}
