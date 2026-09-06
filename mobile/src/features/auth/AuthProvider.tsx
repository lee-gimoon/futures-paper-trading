/**
 * 앱 전체가 공유할 로그인 사용자를 한곳에서 관리한다.
 * 앱 시작 시 서버 세션을 확인하고, 로그인·회원가입·로그아웃 결과를 Context로 제공한다.
 */

import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren,
} from 'react';

import * as authApi from '@/api/authApi';
import { setUnauthorizedHandler } from '@/api/client';
import { clearCsrfToken, ensureCsrfToken, refreshCsrfToken } from '@/api/csrf';
import {
  AuthContext,
  type AuthContextValue,
} from '@/features/auth/AuthContext';
import type { AuthStatus, User } from '@/types/auth';

export function AuthProvider({ children }: PropsWithChildren) {
  const [user, setUser] = useState<User | null>(null);
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [error, setError] = useState<string | null>(null);

  const clearError = useCallback(() => setError(null), []);

  const expireSession = useCallback(() => {
    clearCsrfToken();
    setUser(null);
    setStatus('anonymous');
    setError('로그인 세션이 만료되었습니다. 다시 로그인해 주세요.');
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(expireSession);
    return () => setUnauthorizedHandler(null);
  }, [expireSession]);

  useEffect(() => {
    let active = true;

    async function initializeAuth() {
      try {
        // CSRF 요청에서 받은 SESSION 쿠키를 같은 세션 확인 요청에 이어서 사용한다.
        await ensureCsrfToken();
        const currentUser = await authApi.fetchMe();
        if (!active) return;

        setUser(currentUser);
        setStatus(currentUser ? 'authenticated' : 'anonymous');
        setError(null);
      } catch (caught) {
        if (!active) return;
        setUser(null);
        setStatus('anonymous');
        setError(
          caught instanceof Error
            ? caught.message
            : '로그인 상태를 확인하지 못했습니다.',
        );
      }
    }

    void initializeAuth();
    return () => {
      active = false;
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    setError(null);
    await authApi.login(email, password);
    await refreshCsrfToken();
    const authenticatedUser = await authApi.fetchMe();

    if (!authenticatedUser) {
      throw new Error('로그인 세션을 확인하지 못했습니다. 다시 시도해 주세요.');
    }

    setUser(authenticatedUser);
    setStatus('authenticated');
  }, []);

  const signup = useCallback(
    async (email: string, password: string, displayName: string) => {
      setError(null);
      await authApi.signup(email, password, displayName);
      await authApi.login(email, password);
      await refreshCsrfToken();
      const authenticatedUser = await authApi.fetchMe();

      if (!authenticatedUser) {
        throw new Error('가입 후 로그인 세션을 확인하지 못했습니다.');
      }

      setUser(authenticatedUser);
      setStatus('authenticated');
    },
    [],
  );

  const logout = useCallback(async () => {
    setError(null);
    await authApi.logout();
    clearCsrfToken();
    setUser(null);
    setStatus('anonymous');
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      status,
      error,
      clearError,
      login,
      signup,
      logout,
      expireSession,
    }),
    [clearError, error, expireSession, login, logout, signup, status, user],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
