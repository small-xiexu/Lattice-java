import type { ZodType } from "zod";

import { ApiError, type ApiFieldErrors } from "./api-error";

type QueryPrimitive = string | number | boolean;
type QueryParameter = QueryPrimitive | readonly QueryPrimitive[] | null | undefined;
type QueryParameters = Record<string, QueryParameter>;

interface ApiClientConfiguration {
  baseUrl?: string;
  fetchImplementation?: typeof fetch;
}

interface ApiRequestOptions<TResponse>
  extends Omit<RequestInit, "body" | "method"> {
  schema: ZodType<TResponse>;
  query?: QueryParameters;
  body?: unknown;
}

export interface ApiClient {
  get<TResponse>(
    path: string,
    options: ApiRequestOptions<TResponse>,
  ): Promise<TResponse>;
  post<TResponse>(
    path: string,
    options: ApiRequestOptions<TResponse>,
  ): Promise<TResponse>;
  put<TResponse>(
    path: string,
    options: ApiRequestOptions<TResponse>,
  ): Promise<TResponse>;
  patch<TResponse>(
    path: string,
    options: ApiRequestOptions<TResponse>,
  ): Promise<TResponse>;
  delete<TResponse>(
    path: string,
    options: ApiRequestOptions<TResponse>,
  ): Promise<TResponse>;
}

const RETRYABLE_STATUS_CODES = new Set([408, 425, 429, 500, 502, 503, 504]);

export function createApiClient({
  baseUrl = "",
  fetchImplementation,
}: ApiClientConfiguration = {}): ApiClient {
  const request = async <TResponse>(
    method: string,
    path: string,
    options: ApiRequestOptions<TResponse>,
  ): Promise<TResponse> => {
    const { body, headers, query, schema, ...requestInit } = options;
    const requestHeaders = new Headers(headers);
    requestHeaders.set("Accept", "application/json");

    let requestBody: BodyInit | undefined;
    if (body !== undefined) {
      if (isNativeBody(body)) {
        requestBody = body;
      } else {
        requestHeaders.set("Content-Type", "application/json");
        requestBody = JSON.stringify(body);
      }
    }

    let response: Response;
    try {
      const requestFetch = fetchImplementation ?? globalThis.fetch;
      response = await requestFetch(buildUrl(baseUrl, path, query), {
        ...requestInit,
        body: requestBody,
        credentials: requestInit.credentials ?? "same-origin",
        headers: requestHeaders,
        method,
      });
    } catch (error) {
      throw normalizeTransportError(error);
    }

    const payload = await readResponsePayload(response);
    if (!response.ok) {
      throw normalizeHttpError(response.status, payload);
    }

    const result = schema.safeParse(payload);
    if (!result.success) {
      throw new ApiError({
        status: response.status,
        code: "INVALID_RESPONSE",
        message: "服务端返回了无法识别的数据",
        fieldErrors: zodIssuesToFieldErrors(result.error.issues),
      });
    }
    return result.data;
  };

  return {
    get: (path, options) => request("GET", path, options),
    post: (path, options) => request("POST", path, options),
    put: (path, options) => request("PUT", path, options),
    patch: (path, options) => request("PATCH", path, options),
    delete: (path, options) => request("DELETE", path, options),
  };
}

export const apiClient = createApiClient();

function buildUrl(
  baseUrl: string,
  path: string,
  query?: QueryParameters,
): string {
  if (!path.startsWith("/")) {
    throw new Error(`API path must start with '/': ${path}`);
  }
  const search = new URLSearchParams();
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value === null || value === undefined) {
      return;
    }
    const values = Array.isArray(value) ? value : [value];
    values.forEach((entry) => search.append(key, String(entry)));
  });
  const queryString = search.toString();
  return `${baseUrl.replace(/\/$/, "")}${path}${queryString ? `?${queryString}` : ""}`;
}

function isNativeBody(body: unknown): body is BodyInit {
  return (
    typeof body === "string" ||
    body instanceof Blob ||
    body instanceof FormData ||
    body instanceof URLSearchParams ||
    body instanceof ArrayBuffer ||
    ArrayBuffer.isView(body)
  );
}

async function readResponsePayload(response: Response): Promise<unknown> {
  if (response.status === 204 || response.status === 205) {
    return undefined;
  }
  const text = await response.text();
  if (!text) {
    return undefined;
  }
  const contentType = response.headers.get("content-type") ?? "";
  if (contentType.includes("json") || /^[\s]*[{[]/.test(text)) {
    try {
      return JSON.parse(text) as unknown;
    } catch {
      return text;
    }
  }
  return text;
}

function normalizeTransportError(error: unknown): ApiError {
  if (error instanceof DOMException && error.name === "AbortError") {
    return new ApiError({
      status: null,
      code: "REQUEST_ABORTED",
      message: "请求已停止等待",
      cause: error,
    });
  }
  return new ApiError({
    status: null,
    code: "NETWORK_ERROR",
    message: "无法连接到服务端",
    retryable: true,
    cause: error,
  });
}

function normalizeHttpError(status: number, payload: unknown): ApiError {
  const body = isRecord(payload) ? payload : {};
  const code = firstString(body.code, body.error, body.title) ?? `HTTP_${status}`;
  const message =
    firstString(body.message, body.detail, body.title) ??
    (typeof payload === "string" && payload.trim()
      ? payload.trim()
      : `请求失败（HTTP ${status}）`);

  return new ApiError({
    status,
    code,
    message,
    fieldErrors: extractFieldErrors(body),
    retryable: RETRYABLE_STATUS_CODES.has(status),
  });
}

function extractFieldErrors(body: Record<string, unknown>): ApiFieldErrors {
  const errors = body.errors ?? body.fieldErrors;
  if (isRecord(errors)) {
    return Object.fromEntries(
      Object.entries(errors).map(([field, value]) => [
        field,
        Array.isArray(value) ? value.map(String) : [String(value)],
      ]),
    );
  }
  if (!Array.isArray(errors)) {
    return {};
  }
  const result: ApiFieldErrors = {};
  errors.forEach((entry) => {
    if (!isRecord(entry)) {
      return;
    }
    const field = firstString(entry.field, entry.property, entry.name);
    const message = firstString(entry.defaultMessage, entry.message, entry.reason);
    if (field && message) {
      result[field] = [...(result[field] ?? []), message];
    }
  });
  return result;
}

function zodIssuesToFieldErrors(
  issues: readonly { path: PropertyKey[]; message: string }[],
): ApiFieldErrors {
  const result: ApiFieldErrors = {};
  issues.forEach((issue) => {
    const field = issue.path.length > 0 ? issue.path.join(".") : "response";
    result[field] = [...(result[field] ?? []), issue.message];
  });
  return result;
}

function firstString(...values: unknown[]): string | undefined {
  return values.find(
    (value): value is string => typeof value === "string" && value.trim() !== "",
  );
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
