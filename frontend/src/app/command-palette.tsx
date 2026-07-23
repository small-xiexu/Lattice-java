import { Search, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";

import { NAVIGATION_ITEMS } from "./navigation";

interface CommandPaletteProps {
  open: boolean;
  onClose: () => void;
}

export function CommandPalette({ open, onClose }: CommandPaletteProps) {
  const navigate = useNavigate();
  const dialogRef = useRef<HTMLElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const returnFocusRef = useRef<HTMLElement | null>(null);
  const [query, setQuery] = useState("");

  const closePalette = () => {
    setQuery("");
    onClose();
  };

  useEffect(() => {
    if (!open) {
      return;
    }
    returnFocusRef.current =
      document.activeElement instanceof HTMLElement
        ? document.activeElement
        : null;
    inputRef.current?.focus();
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onClose();
        return;
      }
      if (event.key === "Tab") {
        keepFocusInDialog(event, dialogRef.current);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => {
      window.removeEventListener("keydown", handleKeyDown);
      returnFocusRef.current?.focus();
    };
  }, [onClose, open]);

  if (!open) {
    return null;
  }

  const normalizedQuery = query.trim().toLocaleLowerCase();
  const matchingItems = NAVIGATION_ITEMS.filter((item) =>
    item.label.toLocaleLowerCase().includes(normalizedQuery),
  );

  const selectItem = (path: string) => {
    navigate(path);
    closePalette();
  };

  return (
    <div
      className="command-overlay"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) {
          closePalette();
        }
      }}
    >
      <section
        aria-label="页面跳转"
        aria-modal="true"
        className="command-dialog"
        ref={dialogRef}
        role="dialog"
      >
        <div className="command-input-row">
          <Search aria-hidden="true" size={18} />
          <input
            ref={inputRef}
            aria-label="搜索页面"
            onChange={(event) => setQuery(event.target.value)}
            placeholder="搜索页面"
            value={query}
          />
          <button
            aria-label="关闭页面跳转"
            className="icon-button"
            onClick={closePalette}
            type="button"
          >
            <X aria-hidden="true" size={18} />
          </button>
        </div>
        <div aria-label="匹配页面" className="command-results">
          {matchingItems.map((item) => {
            const Icon = item.icon;
            return (
              <button
                className="command-result"
                key={item.path}
                onClick={() => selectItem(item.path)}
                type="button"
              >
                <Icon aria-hidden="true" size={18} />
                <span>{item.label}</span>
              </button>
            );
          })}
          {matchingItems.length === 0 ? (
            <p className="command-empty">没有匹配页面</p>
          ) : null}
        </div>
      </section>
    </div>
  );
}

function keepFocusInDialog(event: KeyboardEvent, dialog: HTMLElement | null) {
  if (!dialog) {
    return;
  }
  const focusable = Array.from(
    dialog.querySelectorAll<HTMLElement>(
      'button:not([disabled]), input:not([disabled]), [href], [tabindex]:not([tabindex="-1"])',
    ),
  ).filter((element) => !element.hasAttribute("hidden"));
  const first = focusable[0];
  const last = focusable.at(-1);
  if (!first || !last) {
    event.preventDefault();
    return;
  }
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}
