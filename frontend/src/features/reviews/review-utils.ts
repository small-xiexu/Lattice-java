export interface ReviewIssueView {
  severity: string;
  category: string;
  description: string;
}

export function parseReviewIssues(value: string): ReviewIssueView[] {
  try {
    const parsed: unknown = JSON.parse(value);
    const candidates = Array.isArray(parsed)
      ? parsed
      : isRecord(parsed) && Array.isArray(parsed.issues)
        ? parsed.issues
        : [];
    return candidates.filter(isRecord).map((issue) => ({
      severity: stringValue(issue.severity, "UNKNOWN"),
      category: stringValue(issue.category, "未分类"),
      description: stringValue(issue.description, "未提供问题说明"),
    }));
  } catch {
    return [];
  }
}

export function reviewStatusLabel(value: string) {
  const labels: Record<string, string> = {
    needs_human_review: "待人工确认",
    accepted: "已接受",
    published: "已发布",
    rejected: "已驳回",
  };
  return labels[value.toLowerCase()] ?? value;
}

export function reviewStatusTone(value: string) {
  if (value.toLowerCase() === "published" || value.toLowerCase() === "accepted") return "success";
  if (value.toLowerCase() === "rejected") return "danger";
  if (value.toLowerCase() === "needs_human_review") return "warning";
  return "neutral";
}

export function pendingStatusLabel(value: string) {
  const normalized = value.toUpperCase();
  if (normalized === "PASSED") return "模型审查通过";
  if (normalized === "NEEDS_HUMAN_REVIEW") return "需人工核验";
  if (normalized === "PENDING_REVIEW") return "等待审查";
  return value;
}

export function issueTone(value: string) {
  if (value.toUpperCase() === "HIGH") return "danger";
  if (value.toUpperCase() === "MEDIUM") return "warning";
  return "neutral";
}

export function formatReviewTime(value?: string | null) {
  if (!value) return "--";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat("zh-CN", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        hour12: false,
      }).format(date);
}

export function resolveReviewError(error: unknown) {
  if (typeof error === "object" && error !== null && "message" in error) {
    return String(error.message);
  }
  return "审核请求失败";
}

export function stripFrontmatter(content: string) {
  return content.replace(/^---\s*\n[\s\S]*?\n---\s*\n?/, "");
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function stringValue(value: unknown, fallback: string) {
  return typeof value === "string" && value.trim() ? value : fallback;
}
