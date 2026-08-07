import { useCallback, useEffect, useState } from 'react';
import type { User } from '../../shared/types';
import * as authApi from '../api/authApi';

// 로그인 상태를 들고 있는 단 하나의 출처.
// 새로고침하면 user state는 null로 초기화되지만 (기존 JavaScript 실행 환경이 종료되고 React 앱이 처음부터 다시 실행되기 때문),
// 브라우저의 SESSION 쿠키와 서버 세션은 남아 있을 수 있다.
// useAuth는 마운트 시 fetchMe()로 현재 사용자를 다시 조회해 로그인 상태를 복원한다.
// - user: 로그인한 사용자 (없으면 null)
// - loading: 첫 사용자 조회가 끝나기 전 true (버튼이 잠깐 깜빡이는 것 방지)
export function useAuth() {
  // <User | null>은 useState에 전달하는 타입 인자다.
  // user state에는 User 객체 또는 null이 들어갈 수 있고, (null)은 최초 초기값이다.
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // useEffect는 렌더링 후 부수 효과를 실행하는 Hook으로, useAuth를 호출한 컴포넌트가 처음 렌더링된 뒤 현재 사용자 정보를 조회하는 fetchMe() 요청을 한 번 실행한다.
  // fetchMe()는 SESSION 쿠키를 포함해 GET /api/auth/me를 요청한다.
  // 렌더링 중에 하면 안 되는 비동기 요청이므로 useEffect에서 처리한다 (렌더링 중 state를 변경하면 다시 렌더링되어 API 요청이 반복될 수 있기 때문).
  // 빈 의존성 배열([]) 때문에 마운트 시 한 번만 실행한다.
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

  // useCallback: 렌더링 사이에 같은 함수 자체를 기억(재사용)하는 React 훅이다.
  // 함수 참조가 불필요하게 바뀌는 것을 막아, 자식 컴포넌트 재렌더링이나 Effect 재실행을 줄일 수 있다.
  // 빈 의존성 배열([]): 이 콜백은 변경되는 state나 props 값에 의존하지 않아 기존 함수 참조를 재사용한다.
  // 반대로 [user]처럼 의존성이 있으면 user가 바뀔 때 최신 값을 반영한 새 함수를 만든다.
  // 즉, useCallback의 두 번째 매개변수인 의존성 배열은 첫 번째 매개변수 함수의 재생성 여부를 결정한다.
  const logout = useCallback(async () => {
    setError(null); // 이전에 표시된 인증 오류 상태를 초기화한다.
    try { // 로그아웃 요청이 성공하는 경우의 작업을 실행한다.
      await authApi.logout(); // Promise가 완료될 때까지 기다린다.
      setUser(null); // 화면의 현재 사용자 상태도 로그아웃 상태로 변경한다.
    } catch (err) { // 요청 중 오류가 발생하면 err 오류 값을 받아 처리한다.
      setError(err instanceof Error ? err.message : '로그아웃에 실패했습니다.'); // Error면 메시지를, 아니면 기본 문구를 저장한다.
    }
  }, []); // 빈 의존성 배열: 이전 logout 함수 참조를 유지한다.

  // 보호 API가 401을 반환하면 만료된 서버 세션을 화면 상태에도 반영한다.
  const expireSession = useCallback(() => {
    setUser(null);
    setError('로그인 세션이 만료되었습니다. 다시 로그인해주세요.');
  }, []);

  // user, loading, error, login 등은 useAuth 함수 내부의 변수다.
  // return은 이 변수들을 속성으로 담은 객체를 호출한 컴포넌트에 반환한다.
  return { user, loading, error, login, signup, logout, expireSession };
}
