import { X } from "lucide-react";
import { useEffect, useRef, type ReactNode } from "react";

import { useMediaQuery } from "../app/use-media-query";

interface SplitViewProps {
  primary: ReactNode;
  secondary: ReactNode;
  secondaryLabel: string;
  mobileSecondaryOpen?: boolean;
  onMobileSecondaryClose?: () => void;
}

export function SplitView({
  primary,
  secondary,
  secondaryLabel,
  mobileSecondaryOpen = false,
  onMobileSecondaryClose,
}: SplitViewProps) {
  const mobileLayout = useMediaQuery("(max-width: 1023px)");
  const secondaryHidden = mobileLayout && !mobileSecondaryOpen;
  const secondaryRef = useRef<HTMLElement>(null);

  useEffect(() => {
    if (!mobileLayout || !mobileSecondaryOpen) {
      return;
    }
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onMobileSecondaryClose?.();
      } else if (event.key === "Tab") {
        keepFocusInSheet(event, secondaryRef.current);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [mobileLayout, mobileSecondaryOpen, onMobileSecondaryClose]);

  return (
    <div className="split-view">
      <section className="split-view-primary">{primary}</section>
      <aside
        aria-hidden={secondaryHidden ? true : undefined}
        aria-label={secondaryLabel}
        aria-modal={mobileLayout && mobileSecondaryOpen ? true : undefined}
        className={`split-view-secondary${mobileSecondaryOpen ? " is-mobile-open" : ""}`}
        inert={secondaryHidden}
        ref={secondaryRef}
        role={mobileLayout ? "dialog" : undefined}
      >
        <div className="split-view-secondary-header">
          <strong>{secondaryLabel}</strong>
          <button
            aria-label={`关闭${secondaryLabel}`}
            className="icon-button split-view-close"
            onClick={onMobileSecondaryClose}
            type="button"
          >
            <X aria-hidden="true" size={19} />
          </button>
        </div>
        <div className="split-view-secondary-body">{secondary}</div>
      </aside>
      {mobileSecondaryOpen ? (
        <button
          aria-hidden="true"
          className="split-view-scrim"
          onClick={onMobileSecondaryClose}
          tabIndex={-1}
          type="button"
        />
      ) : null}
    </div>
  );
}

function keepFocusInSheet(event: KeyboardEvent, sheet: HTMLElement | null) {
  if (!sheet) {
    return;
  }
  const focusable = Array.from(
    sheet.querySelectorAll<HTMLElement>(
      'button:not([disabled]), input:not([disabled]), [href], [tabindex]:not([tabindex="-1"])',
    ),
  );
  const first = focusable[0];
  const last = focusable.at(-1);
  if (!first || !last) {
    event.preventDefault();
    return;
  }
  const activeIndex = focusable.indexOf(document.activeElement as HTMLElement);
  if (activeIndex === -1) {
    event.preventDefault();
    (event.shiftKey ? last : first).focus();
  } else if (event.shiftKey && activeIndex === 0) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && activeIndex === focusable.length - 1) {
    event.preventDefault();
    first.focus();
  }
}
