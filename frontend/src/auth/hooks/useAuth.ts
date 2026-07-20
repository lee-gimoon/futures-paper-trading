import { useCallback, useEffect, useState } from 'react';
import type { User } from '../../shared/types';
import * as authApi from '../api/authApi';

// 로그인 상태를 들고 있는 단 하나의 출처.
// - user: 로그인한 사용자 (없으면 null)
// - loading: 첫 me() 확인이 끝나기 전 true (버튼이 잠깐 깜빡이는 것 방지)
export function useAuth() {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // 마운트 시 1회: 쿠키가 살아 있으면 누구인지 가져온다 → 새로고침해도 로그인 유지.
  useEffect(() => {
    authApi
      .fetchMe()
      .then(setUser)
      .catch((err) => {
        setUser(null);
        setError(err instanceof Error ? err.message : '로그인 상태를 확인하지 못했습니다.');
      })
      .finally(() => setLoading(false));
  }, []);

  // 폼이 호출하면 서버 로그인 → 현재 사용자 조회 → React 로그인 상태 갱신 순으로 처리한다.
  const login = useCallback(async (email: string, password: string) => {
    await authApi.login(email, password); // 성공이면 undefined로 계속, 실패면 Error가 호출한 폼까지 전달된다.
    const authenticatedUser = await authApi.fetchMe(); // 로그인 응답의 SESSION 쿠키로 현재 사용자를 요청한다.
    if (!authenticatedUser) throw new Error('로그인 세션을 확인하지 못했습니다. 다시 시도해주세요.'); // 성공했는데 사용자가 없으면 이후 state 갱신을 막는다.
    setError(null); // 이전 인증 오류를 지운다.
    setUser(authenticatedUser); // user state가 바뀌어 App이 로그인된 화면을 렌더링한다.
  }, []);

  // 가입 → 곧바로 로그인까지 이어줘서 한 번에 로그인 상태가 되게 한다.
  const signup = useCallback(async (email: string, password: string, displayName: string) => {
    await authApi.signup(email, password, displayName);
    await authApi.login(email, password);
    const authenticatedUser = await authApi.fetchMe();
    if (!authenticatedUser) throw new Error('로그인 세션을 확인하지 못했습니다. 다시 시도해주세요.');
    setError(null);
    setUser(authenticatedUser);
  }, []);

  const logout = useCallback(async () => {
    setError(null);
    try {
      await authApi.logout();
      setUser(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : '로그아웃에 실패했습니다.');
    }
  }, []);

  // 보호 API가 401을 반환하면 만료된 서버 세션을 화면 상태에도 반영한다.
  const expireSession = useCallback(() => {
    setUser(null);
    setError('로그인 세션이 만료되었습니다. 다시 로그인해주세요.');
  }, []);

  return { user, loading, error, login, signup, logout, expireSession };
}
