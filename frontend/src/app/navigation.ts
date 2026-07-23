import {
  Activity,
  Bot,
  Braces,
  ClipboardCheck,
  Database,
  FileScan,
  LibraryBig,
  MessageSquareText,
  MessagesSquare,
  ScanSearch,
  ShieldCheck,
  SlidersHorizontal,
  Wrench,
  type LucideIcon,
} from "lucide-react";
import { matchPath } from "react-router-dom";

export interface NavigationItem {
  label: string;
  path: string;
  icon: LucideIcon;
  end?: boolean;
}

export interface NavigationGroup {
  label: string;
  items: NavigationItem[];
}

export interface RouteMeta {
  pattern: string;
  title: string;
  section: string;
}

export const NAVIGATION_GROUPS: NavigationGroup[] = [
  {
    label: "工作台",
    items: [
      {
        label: "问答与研究",
        path: "/ask",
        icon: MessageSquareText,
        end: true,
      },
    ],
  },
  {
    label: "知识库",
    items: [
      { label: "资料源", path: "/library/sources", icon: Database },
      { label: "知识文章", path: "/library/articles", icon: LibraryBig },
      { label: "知识质量", path: "/library/quality", icon: ShieldCheck },
    ],
  },
  {
    label: "处理中心",
    items: [
      { label: "运行任务", path: "/activity", icon: Activity },
      { label: "人工审核", path: "/reviews", icon: ClipboardCheck },
      { label: "结果反馈", path: "/feedback", icon: MessagesSquare },
    ],
  },
  {
    label: "系统设置",
    items: [
      { label: "模型与绑定", path: "/settings/models", icon: Bot },
      { label: "向量索引", path: "/settings/vector", icon: ScanSearch },
      { label: "文档解析", path: "/settings/parsing", icon: FileScan },
      {
        label: "检索参数",
        path: "/settings/retrieval",
        icon: SlidersHorizontal,
      },
      { label: "系统维护", path: "/settings/maintenance", icon: Wrench },
    ],
  },
  {
    label: "接入",
    items: [{ label: "开发者接入", path: "/developer", icon: Braces }],
  },
];

export const NAVIGATION_ITEMS = NAVIGATION_GROUPS.flatMap(
  (group) => group.items,
);

const ROUTE_META: RouteMeta[] = [
  { pattern: "/ask", title: "问答与研究", section: "工作台" },
  {
    pattern: "/library/sources/:sourceId",
    title: "资料源详情",
    section: "知识库",
  },
  { pattern: "/library/sources", title: "资料源", section: "知识库" },
  {
    pattern: "/library/articles/:articleKey",
    title: "文章详情",
    section: "知识库",
  },
  { pattern: "/library/articles", title: "知识文章", section: "知识库" },
  { pattern: "/library/quality", title: "知识质量", section: "知识库" },
  { pattern: "/activity", title: "运行任务", section: "处理中心" },
  { pattern: "/reviews", title: "人工审核", section: "处理中心" },
  { pattern: "/feedback", title: "结果反馈", section: "处理中心" },
  { pattern: "/settings/models", title: "模型与绑定", section: "系统设置" },
  { pattern: "/settings/vector", title: "向量索引", section: "系统设置" },
  { pattern: "/settings/parsing", title: "文档解析", section: "系统设置" },
  {
    pattern: "/settings/retrieval",
    title: "检索参数",
    section: "系统设置",
  },
  {
    pattern: "/settings/maintenance",
    title: "系统维护",
    section: "系统设置",
  },
  { pattern: "/developer", title: "开发者接入", section: "接入" },
];

export function resolveRouteMeta(pathname: string): RouteMeta {
  const routeMeta = ROUTE_META.find((candidate) =>
    matchPath(candidate.pattern, pathname),
  );
  return routeMeta ?? ROUTE_META[0];
}
