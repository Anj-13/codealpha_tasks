const TIMEOUT_MS = 10000;

/** The service answered with an error it chose to send. */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly fieldErrors: Record<string, string> = {},
  ) {
    super(message);
  }
}

/** The service could not be reached, or the proxy had nothing to forward to. */
export class ServiceOfflineError extends Error {
  constructor() {
    super('This service is offline');
  }
}

export interface RawResponse {
  status: number;
  body: unknown;
}

function isErrorBody(body: unknown): body is { message: string; fieldErrors?: Record<string, string> } {
  return typeof body === 'object' && body !== null && typeof (body as { message?: unknown }).message === 'string';
}

export async function send(url: string, init: RequestInit = {}): Promise<RawResponse> {
  let response: Response;
  try {
    response = await fetch(url, { ...init, signal: AbortSignal.timeout(TIMEOUT_MS) });
  } catch {
    throw new ServiceOfflineError();
  }
  const body: unknown = await response.json().catch(() => null);
  return { status: response.status, body };
}

export function toError({ status, body }: RawResponse): Error {
  if (isErrorBody(body)) {
    return new ApiError(body.message, status, body.fieldErrors);
  }
  // Every service error is JSON, so anything else came from the proxy with nothing behind it.
  return status >= 500 ? new ServiceOfflineError() : new ApiError('The request could not be completed', status);
}

export async function getJson<T>(url: string, headers: HeadersInit = {}): Promise<T> {
  const response = await send(url, { headers });
  if (response.status >= 200 && response.status < 300 && response.body !== null) {
    return response.body as T;
  }
  throw toError(response);
}

export function postRaw(url: string, payload: unknown): Promise<RawResponse> {
  return send(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}

export async function postJson<T>(url: string, payload: unknown): Promise<T> {
  const response = await postRaw(url, payload);
  if (response.status >= 200 && response.status < 300 && response.body !== null) {
    return response.body as T;
  }
  throw toError(response);
}
