import { useCallback, useEffect, useState } from 'react';
import type { User } from '../types';
import * as authApi from './authApi';

// 로그인 상태를 들고 있는 단 하나의 출처.
// - user: 로그인한 사용자 (없으면 null)
// - loading: 첫 me() 확인이 끝나기 전 true (버튼이 잠깐 깜빡이는 것 방지)
export function useAuth() {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  // 마운트 시 1회: 쿠키가 살아 있으면 누구인지 가져온다 → 새로고침해도 로그인 유지.
  useEffect(() => {
    authApi
      .fetchMe()
      .then(setUser)
      .catch(() => setUser(null))
      .finally(() => setLoading(false));
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    await authApi.login(email, password);
    setUser(await authApi.fetchMe()); // 로그인 직후 내 정보 채우기
  }, []);

  // 가입 → 곧바로 로그인까지 이어줘서 한 번에 로그인 상태가 되게 한다.
  const signup = useCallback(async (email: string, password: string, displayName: string) => {
    await authApi.signup(email, password, displayName);
    await authApi.login(email, password);
    setUser(await authApi.fetchMe());
  }, []);

  const logout = useCallback(async () => {
    await authApi.logout();
    setUser(null);
  }, []);

  return { user, loading, login, signup, logout };
}
