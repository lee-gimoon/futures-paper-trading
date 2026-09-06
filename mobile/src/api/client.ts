/**
 * Spring API 요청에 서버 주소, SESSION 쿠키, CSRF 헤더와 공통 오류 처리를 적용한다.
 * 각 기능 API 파일은 경로와 요청 본문만 정하고 반복되는 통신 규칙은 이 파일에 맡긴다.
 */

import { fetch } from 'expo/fetch';

import { apiUrl } from '@/api/config';
import { ensureCsrfToken } from '@/api/csrf';

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE']);

type ApiRequestInit = RequestInit & {
  // 로그인 실패나 로그인 상태 확인의 401은 전역 세션 만료 처리에서 제외한다.
  skipUnauthorizedHandler?: boolean;
};

let unauthorizedHandler: (() => void) | null = null;

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler;
}

export class HttpError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = 'HttpError';
  }
}

export class NetworkError extends Error {
  constructor(
    message = '서버에 연결하지 못했습니다. 인터넷 연결을 확인해 주세요.',
  ) {
    super(message);
    this.name = 'NetworkError';
  }
}

export async function toHttpError(
  response: Response,
  fallback: string,
): Promise<HttpError> {
  try {
    const body: unknown = await response.json();
    if (
      typeof body === 'object' &&
      body !== null &&
      'message' in body &&
      typeof body.message === 'string'
    ) {
      return new HttpError(response.status, body.message);
    }
  } catch {
    // JSON 오류 본문이 없으면 호출한 API 함수가 전달한 안내 문구를 사용한다.
  }

  return new HttpError(response.status, fallback);
}

export async function apiFetch(
  path: string,
  init: ApiRequestInit = {},
): Promise<Response> {
  const { skipUnauthorizedHandler = false, ...requestInit } = init;
  const method = (requestInit.method ?? 'GET').toUpperCase();
  const headers = new Headers(requestInit.headers);

  if (!SAFE_METHODS.has(method)) {
    const csrf = await ensureCsrfToken();
    headers.set(csrf.headerName, csrf.token);
  }

  try {
    const response = await fetch(apiUrl(path), {
      ...requestInit,
      headers,
      credentials: 'include',
    });

    if (response.status === 401 && !skipUnauthorizedHandler) {
      unauthorizedHandler?.();
    }

    return response;
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') throw error;
    throw new NetworkError();
  }
}

export async function readJson<T>(
  response: Response,
  fallback: string,
): Promise<T> {
  if (!response.ok) throw await toHttpError(response, fallback);
  return response.json() as Promise<T>;
}
