import { useCallback, useMemo } from "react";
import { useSearchParams } from "react-router-dom";

export interface ListUrlState {
  query: string;
  status: string | null;
  sourceType: string | null;
  sort: string;
  order: "asc" | "desc";
  page: number;
  size: number;
  selected: string | null;
}

interface UpdateOptions {
  replace?: boolean;
}

const DEFAULT_STATE: ListUrlState = {
  query: "",
  status: null,
  sourceType: null,
  sort: "updatedAt",
  order: "desc",
  page: 1,
  size: 20,
  selected: null,
};

export function parseListUrlState(searchParams: URLSearchParams): ListUrlState {
  return {
    query: normalizeText(searchParams.get("q"), 200) ?? DEFAULT_STATE.query,
    status: normalizeText(searchParams.get("status"), 80),
    sourceType: normalizeText(searchParams.get("sourceType"), 80),
    sort: normalizeText(searchParams.get("sort"), 80) ?? DEFAULT_STATE.sort,
    order: searchParams.get("order") === "asc" ? "asc" : "desc",
    page: parsePositiveInteger(searchParams.get("page"), DEFAULT_STATE.page),
    size: parsePageSize(searchParams.get("size")),
    selected: normalizeText(searchParams.get("selected"), 160),
  };
}

export function writeListUrlState(
  searchParams: URLSearchParams,
  state: ListUrlState,
): URLSearchParams {
  const next = new URLSearchParams(searchParams);
  setOrDelete(next, "q", state.query, DEFAULT_STATE.query);
  setOrDelete(next, "status", state.status, null);
  setOrDelete(next, "sourceType", state.sourceType, null);
  setOrDelete(next, "sort", state.sort, DEFAULT_STATE.sort);
  setOrDelete(next, "order", state.order, DEFAULT_STATE.order);
  setOrDelete(next, "page", String(state.page), String(DEFAULT_STATE.page));
  setOrDelete(next, "size", String(state.size), String(DEFAULT_STATE.size));
  setOrDelete(next, "selected", state.selected, null);
  return next;
}

export function useListUrlState() {
  const [searchParams, setSearchParams] = useSearchParams();
  const state = useMemo(() => parseListUrlState(searchParams), [searchParams]);
  const updateState = useCallback(
    (update: Partial<ListUrlState>, options: UpdateOptions = {}) => {
      const current = parseListUrlState(searchParams);
      setSearchParams(
        writeListUrlState(searchParams, { ...current, ...update }),
        { replace: options.replace ?? false },
      );
    },
    [searchParams, setSearchParams],
  );
  return [state, updateState] as const;
}

function parsePositiveInteger(value: string | null, fallback: number): number {
  if (!value || !/^\d+$/.test(value)) {
    return fallback;
  }
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : fallback;
}

function parsePageSize(value: string | null): number {
  const parsed = parsePositiveInteger(value, DEFAULT_STATE.size);
  return [10, 20, 50, 100].includes(parsed) ? parsed : DEFAULT_STATE.size;
}

function normalizeText(value: string | null, maxLength: number): string | null {
  if (value === null) {
    return null;
  }
  const normalized = value.trim().slice(0, maxLength);
  return normalized || null;
}

function setOrDelete(
  searchParams: URLSearchParams,
  key: string,
  value: string | null,
  defaultValue: string | null,
) {
  if (value === null || value === defaultValue) {
    searchParams.delete(key);
  } else {
    searchParams.set(key, value);
  }
}
