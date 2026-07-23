import {
  AlertCircle,
  AlertTriangle,
  CheckCircle2,
  Info,
  type LucideIcon,
} from "lucide-react";

type AlertTone = "info" | "success" | "warning" | "error";

interface InlineAlertProps {
  tone: AlertTone;
  title: string;
  description?: string;
  actionLabel?: string;
  onAction?: () => void;
}

const ALERT_ICONS: Record<AlertTone, LucideIcon> = {
  info: Info,
  success: CheckCircle2,
  warning: AlertTriangle,
  error: AlertCircle,
};

export function InlineAlert({
  tone,
  title,
  description,
  actionLabel,
  onAction,
}: InlineAlertProps) {
  const Icon = ALERT_ICONS[tone];
  return (
    <div
      className={`inline-alert is-${tone}`}
      role={tone === "error" ? "alert" : "status"}
    >
      <Icon aria-hidden="true" size={19} strokeWidth={1.75} />
      <div className="inline-alert-content">
        <strong>{title}</strong>
        {description ? <p>{description}</p> : null}
      </div>
      {actionLabel && onAction ? (
        <button className="inline-alert-action" onClick={onAction} type="button">
          {actionLabel}
        </button>
      ) : null}
    </div>
  );
}
