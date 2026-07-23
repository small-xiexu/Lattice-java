import { isApiError } from "../../api/api-error";

export function resolveQualityError(error: unknown) {
  return isApiError(error) ? error.message : "请求未完成，请稍后重试";
}

export function formatTime(value: string | null) {
  if (!value) return "尚无采样";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("zh-CN", { hour12: false });
}

export function formatRatio(value: number) {
  return `${(value * 100).toFixed(1)}%`;
}

export function formatDelta(value: number, percent = true) {
  const number = percent ? value * 100 : value;
  const suffix = percent ? "%" : "";
  return `${number > 0 ? "+" : ""}${number.toFixed(percent ? 1 : 0)}${suffix}`;
}
