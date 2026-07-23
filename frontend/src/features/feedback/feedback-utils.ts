export function feedbackStatusLabel(value: string) {
  const labels: Record<string, string> = {
    PENDING: "待处理",
    RESOLVED: "已解决",
    DISMISSED: "已忽略",
  };
  return labels[value.toUpperCase()] ?? value;
}

export function feedbackStatusTone(value: string) {
  const status = value.toUpperCase();
  if (status === "RESOLVED") return "success";
  if (status === "DISMISSED") return "neutral";
  return "warning";
}

export function feedbackTypeLabel(value: string) {
  const labels: Record<string, string> = {
    reliable: "结果可靠",
    answer_problem: "回答问题",
    source_conflict: "来源冲突",
    needs_manual_confirmation: "需要人工确认",
  };
  return labels[value.toLowerCase()] ?? value;
}

export function feedbackActionLabel(value: string) {
  const labels: Record<string, string> = {
    CREATE: "创建反馈",
    RESOLVE: "标记已解决",
    DISMISS: "标记已忽略",
  };
  return labels[value.toUpperCase()] ?? value;
}

export function formatFeedbackTime(value?: string | null) {
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

export function resolveFeedbackError(error: unknown) {
  if (typeof error === "object" && error !== null && "message" in error) {
    return String(error.message);
  }
  return "反馈治理请求失败";
}
