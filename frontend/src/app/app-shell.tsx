import {
  ChevronsLeft,
  ChevronsRight,
  Menu,
  Search,
  X,
} from "lucide-react";
import {
  Suspense,
  useCallback,
  useEffect,
  useRef,
  useState,
} from "react";
import { NavLink, Outlet, useLocation } from "react-router-dom";

import { PageState } from "../components/page-state";
import { RouteErrorBoundary } from "../components/route-error-boundary";
import { CommandPalette } from "./command-palette";
import { NAVIGATION_GROUPS, resolveRouteMeta } from "./navigation";
import { useMediaQuery } from "./use-media-query";
import { useAdminOverview } from "../api/use-admin-overview";

function getInitialCollapsedState() {
  if (typeof window.matchMedia !== "function") {
    return false;
  }
  return window.matchMedia("(max-width: 1535px)").matches;
}

export function AppShell() {
  const location = useLocation();
  const overviewQuery = useAdminOverview();
  const routeMeta = resolveRouteMeta(location.pathname);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(
    getInitialCollapsedState,
  );
  const [mobileNavigationPath, setMobileNavigationPath] = useState<string | null>(
    null,
  );
  const [commandPaletteOpen, setCommandPaletteOpen] = useState(false);
  const mobileCloseButtonRef = useRef<HTMLButtonElement>(null);
  const mobileMenuButtonRef = useRef<HTMLButtonElement>(null);
  const drawerNavigation = useMediaQuery("(max-width: 1279px)");
  const mobileNavigationOpen = mobileNavigationPath === location.pathname;
  const closeCommandPalette = useCallback(
    () => setCommandPaletteOpen(false),
    [],
  );
  const closeMobileNavigation = useCallback((restoreFocus = false) => {
    setMobileNavigationPath(null);
    if (restoreFocus) {
      window.requestAnimationFrame(() => mobileMenuButtonRef.current?.focus());
    }
  }, []);
  const openMobileNavigation = useCallback(() => {
    setMobileNavigationPath(location.pathname);
    window.requestAnimationFrame(() => mobileCloseButtonRef.current?.focus());
  }, [location.pathname]);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        setCommandPaletteOpen(true);
      } else if (event.key === "Escape" && mobileNavigationOpen) {
        event.preventDefault();
        closeMobileNavigation(true);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [closeMobileNavigation, mobileNavigationOpen]);

  const sidebarClassName = [
    "app-sidebar",
    sidebarCollapsed ? "is-collapsed" : "",
    mobileNavigationOpen ? "is-mobile-open" : "",
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <div
      className={sidebarCollapsed ? "app-layout is-collapsed" : "app-layout"}
    >
      <a className="skip-link" href="#main-content">
        跳到主内容
      </a>
      <aside
        aria-hidden={drawerNavigation && !mobileNavigationOpen ? true : undefined}
        aria-label="应用导航"
        className={sidebarClassName}
        inert={drawerNavigation && !mobileNavigationOpen}
      >
        <div className="sidebar-brand">
          <span aria-hidden="true" className="brand-mark">
            L
          </span>
          <strong className="sidebar-label">Lattice</strong>
          <button
            aria-label="关闭导航"
            className="icon-button mobile-close"
            onClick={() => closeMobileNavigation(true)}
            ref={mobileCloseButtonRef}
            type="button"
          >
            <X aria-hidden="true" size={19} />
          </button>
        </div>
        <nav aria-label="主导航" className="sidebar-navigation">
          {NAVIGATION_GROUPS.map((group) => (
            <div className="navigation-group" key={group.label}>
              <p className="navigation-group-label sidebar-label">{group.label}</p>
              {group.items.map((item) => {
                const Icon = item.icon;
                return (
                  <NavLink
                    className={({ isActive }) =>
                      isActive ? "navigation-item is-active" : "navigation-item"
                    }
                    end={item.end}
                    key={item.path}
                    onClick={() => closeMobileNavigation()}
                    title={sidebarCollapsed ? item.label : undefined}
                    to={item.path}
                  >
                    <Icon aria-hidden="true" size={18} strokeWidth={1.75} />
                    <span className="sidebar-label">{item.label}</span>
                  </NavLink>
                );
              })}
            </div>
          ))}
        </nav>
        <div
          aria-label={resolveOverviewLabel(overviewQuery)}
          className="sidebar-footer"
          role="status"
        >
          <span
            aria-hidden="true"
            className={`service-indicator ${resolveOverviewTone(overviewQuery)}`}
          />
          <span className="sidebar-label">{resolveOverviewLabel(overviewQuery)}</span>
        </div>
        <button
          aria-label={sidebarCollapsed ? "展开侧栏" : "收起侧栏"}
          className="sidebar-collapse-button"
          onClick={() => setSidebarCollapsed((collapsed) => !collapsed)}
          title={sidebarCollapsed ? "展开侧栏" : "收起侧栏"}
          type="button"
        >
          {sidebarCollapsed ? (
            <ChevronsRight aria-hidden="true" size={18} />
          ) : (
            <ChevronsLeft aria-hidden="true" size={18} />
          )}
          <span className="sidebar-label">收起侧栏</span>
        </button>
      </aside>
      {mobileNavigationOpen ? (
        <button
          aria-label="关闭导航"
          aria-hidden="true"
          className="navigation-scrim"
          onClick={() => closeMobileNavigation(true)}
          tabIndex={-1}
          type="button"
        />
      ) : null}
      <div className="app-workspace">
        <header className="app-topbar">
          <button
            aria-label="打开导航"
            className="icon-button mobile-menu"
            onClick={openMobileNavigation}
            ref={mobileMenuButtonRef}
            type="button"
          >
            <Menu aria-hidden="true" size={20} />
          </button>
          <div className="topbar-breadcrumb" aria-label="当前位置">
            <span>{routeMeta.section}</span>
            <span aria-hidden="true">/</span>
            <strong>{routeMeta.title}</strong>
          </div>
          <button
            className="search-trigger"
            onClick={() => setCommandPaletteOpen(true)}
            type="button"
          >
            <Search aria-hidden="true" size={17} />
            <span>搜索</span>
          </button>
        </header>
        <main id="main-content" tabIndex={-1}>
          <RouteErrorBoundary key={location.pathname}>
            <Suspense fallback={<RouteLoadingState />}>
              <Outlet />
            </Suspense>
          </RouteErrorBoundary>
        </main>
      </div>
      <CommandPalette
        onClose={closeCommandPalette}
        open={commandPaletteOpen}
      />
    </div>
  );
}

type OverviewQuery = ReturnType<typeof useAdminOverview>;

function resolveOverviewLabel(query: OverviewQuery) {
  if (query.isPending) return "正在获取知识状态";
  if (query.isError || !query.data) return "知识状态不可用";
  return `文章 ${query.data.status.articleCount} · 源文件 ${query.data.status.sourceFileCount}`;
}

function resolveOverviewTone(query: OverviewQuery) {
  if (query.isError) return "is-unavailable";
  if (!query.data) return "";
  return query.data.status.articleCount > 0 && query.data.status.sourceFileCount > 0
    ? "is-ready"
    : "is-attention";
}

function RouteLoadingState() {
  return (
    <div className="page-frame route-loading">
      <PageState status="loading" title="页面加载中" />
    </div>
  );
}
