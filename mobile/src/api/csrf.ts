/**
 * Spring WebSession에 저장된 CSRF 토큰을 앱 메모리에서 관리한다.
 * SESSION 쿠키는 네트워크 계층이 보관하고, 이 파일은 변경 요청 헤더에 넣을 토큰만 기억한다.
 */

import { fetch } from 'expo/fetch';

import { apiUrl } from '@/api/config';

export type CsrfState = {
  headerName: string;
  token: string;
};

let csrfState: CsrfState | null = null;
let csrfLoadPromise: Promise<CsrfState> | null = null;

function isCsrfState(value: unknown): value is CsrfState {
  return (
    typeof value === 'object' &&
    value !== null &&
    'headerName' in value &&
    typeof value.headerName === 'string' &&
    'token' in value &&
    typeof value.token === 'string'
  );
}

async function requestCsrfToken(): Promise<CsrfState> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10000);
  const response = await fetch(apiUrl('/api/auth/csrf'), {
    credentials: 'include',
    signal: controller.signal,
  })
    .catch(() => {
      throw new Error(
        '계좌 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.',
      );
    })
    .finally(() => clearTimeout(timeout));

  if (!response.ok) {
    throw new Error('보안 토큰을 받지 못했습니다. 잠시 후 다시 시도해 주세요.');
  }

  const body: unknown = await response.json();
  if (!isCsrfState(body)) {
    throw new Error('서버의 보안 토큰 응답 형식이 올바르지 않습니다.');
  }

  csrfState = body;
  return body;
}

// 동시에 여러 변경 요청이 시작되어도 진행 중인 토큰 요청 Promise 하나를 함께 사용한다.
export async function ensureCsrfToken(): Promise<CsrfState> {
  if (csrfState) return csrfState;

  if (!csrfLoadPromise) {
    csrfLoadPromise = requestCsrfToken().finally(() => {
      csrfLoadPromise = null;
    });
  }

  return csrfLoadPromise;
}

// 로그인으로 세션 ID가 바뀐 뒤 현재 세션에 속한 토큰을 다시 읽는다.
export async function refreshCsrfToken(): Promise<CsrfState> {
  csrfState = null;
  return ensureCsrfToken();
}

export function clearCsrfToken(): void {
  csrfState = null;
  csrfLoadPromise = null;
}
