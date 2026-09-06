/** 회원가입, 로그인, 현재 사용자 조회와 로그아웃 요청을 한곳에 모은다. */

import { apiFetch, readJson, toHttpError } from '@/api/client';
import type { User } from '@/types/auth';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

export async function signup(
  email: string,
  password: string,
  displayName: string,
): Promise<User> {
  const response = await apiFetch('/api/auth/signup', {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify({
      email,
      password,
      displayName: displayName.trim() || null,
    }),
    skipUnauthorizedHandler: true,
  });
  return readJson(response, '회원가입에 실패했습니다.');
}

export async function login(email: string, password: string): Promise<void> {
  const response = await apiFetch('/api/auth/login', {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify({ email, password }),
    skipUnauthorizedHandler: true,
  });

  if (!response.ok) throw await toHttpError(response, '로그인에 실패했습니다.');
}

export async function fetchMe(): Promise<User | null> {
  const response = await apiFetch('/api/auth/me', {
    skipUnauthorizedHandler: true,
  });
  if (response.status === 401) return null;
  return readJson(response, '로그인 상태를 확인하지 못했습니다.');
}

export async function logout(): Promise<void> {
  const response = await apiFetch('/api/auth/logout', { method: 'POST' });
  if (!response.ok)
    throw await toHttpError(response, '로그아웃에 실패했습니다.');
}
