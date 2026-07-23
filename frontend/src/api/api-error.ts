export type ApiFieldErrors = Record<string, string[]>;

interface ApiErrorOptions {
  status: number | null;
  code: string;
  message: string;
  fieldErrors?: ApiFieldErrors;
  retryable?: boolean;
  cause?: unknown;
}

export class ApiError extends Error {
  readonly status: number | null;
  readonly code: string;
  readonly fieldErrors: ApiFieldErrors;
  readonly retryable: boolean;

  constructor({
    status,
    code,
    message,
    fieldErrors = {},
    retryable = false,
    cause,
  }: ApiErrorOptions) {
    super(message, { cause });
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.retryable = retryable;
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}
