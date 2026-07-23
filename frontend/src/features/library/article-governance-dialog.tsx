import { X } from "lucide-react";
import { useEffect, useRef, type FormEvent, type ReactNode } from "react";

interface ArticleGovernanceDialogProps {
  title: string;
  description: string;
  confirmLabel: string;
  pending: boolean;
  error?: string;
  children: ReactNode;
  destructive?: boolean;
  onClose: () => void;
  onConfirm: () => void;
}

export function ArticleGovernanceDialog({
  title,
  description,
  confirmLabel,
  pending,
  error,
  children,
  destructive = false,
  onClose,
  onConfirm,
}: ArticleGovernanceDialogProps) {
  const dialogRef = useRef<HTMLElement>(null);
  const returnFocusRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    returnFocusRef.current = document.activeElement instanceof HTMLElement
      ? document.activeElement
      : null;
    const firstField = dialogRef.current?.querySelector<HTMLElement>(
      "input:not([disabled]), textarea:not([disabled]), select:not([disabled]), button:not([disabled])",
    );
    firstField?.focus();
    return () => {
      returnFocusRef.current?.focus();
    };
  }, []);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !pending) {
        event.preventDefault();
        onClose();
      } else if (event.key === "Tab") {
        keepFocusInDialog(event, dialogRef.current);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose, pending]);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!pending) onConfirm();
  };

  return (
    <div
      className="governance-dialog-overlay"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !pending) onClose();
      }}
      role="presentation"
    >
      <section
        aria-labelledby="governance-dialog-title"
        aria-modal="true"
        className="governance-dialog"
        ref={dialogRef}
        role="dialog"
      >
        <header>
          <div>
            <h2 id="governance-dialog-title">{title}</h2>
            <p>{description}</p>
          </div>
          <button aria-label="关闭" className="icon-button" disabled={pending} onClick={onClose} type="button">
            <X aria-hidden="true" size={18} />
          </button>
        </header>
        <form onSubmit={submit}>
          <div className="governance-dialog-body">{children}</div>
          {error ? <p className="governance-dialog-error" role="alert">{error}</p> : null}
          <footer>
            <button className="secondary-button governance-dialog-button" disabled={pending} onClick={onClose} type="button">
              取消
            </button>
            <button className={destructive ? "danger-button" : "primary-button"} disabled={pending} type="submit">
              {pending ? "正在执行" : confirmLabel}
            </button>
          </footer>
        </form>
      </section>
    </div>
  );
}

function keepFocusInDialog(event: KeyboardEvent, dialog: HTMLElement | null) {
  if (!dialog) return;
  const focusable = Array.from(
    dialog.querySelectorAll<HTMLElement>(
      'button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [href], [tabindex]:not([tabindex="-1"])',
    ),
  ).filter((element) => !element.hasAttribute("hidden"));
  const first = focusable[0];
  const last = focusable.at(-1);
  if (!first || !last) {
    event.preventDefault();
  } else if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}
