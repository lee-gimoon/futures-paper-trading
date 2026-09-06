/** 인증 API가 비밀번호를 제외하고 반환하는 현재 사용자 정보다. */
export type User = {
  id: number;
  email: string;
  displayName: string | null;
};

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous';
