// React에서 가져오는 두 가지 도구다.
// - useState: “React의 상태(state) 기능을 사용한다(use)”라는 뜻으로 지은 이름이다.
//   이메일·비밀번호·오류 메시지처럼 화면이 기억해야 할 값을 관리하는 Hook이다.
// - type FormEvent: 폼 제출 시 handleSubmit이 받는 이벤트(e)의 TypeScript 타입 정보다.
//   `type`을 붙였으므로 브라우저에서 실행할 JavaScript를 가져오는 것이 아니라 타입 검사에만 사용한다.
import { useState, type FormEvent } from 'react';

type Props = {
  // onLogin에는 함수가 들어와야 한다. `(매개변수들) => 반환타입`은 화살표 모양으로 쓰는 함수 타입 표기다.
  // 이 함수는 email과 password 문자열을 받고, 비동기 작업의 완료를 나타내는 Promise를 반환한다.
  // Promise<void>의 <void>는 Java의 제네릭처럼 Promise가 완료된 뒤 돌려줄 값의 타입을 적는 자리다.
  // void는 “돌려줄 값이 없다”는 뜻이다. 따라서 로그인 완료를 기다릴 수는 있지만 결과값은 받지 않는다.
  onLogin: (email: string, password: string) => Promise<void>;
  onClose: () => void;
};

// LoginForm 함수(컴포넌트)는 실행될 때마다 현재 이메일·비밀번호 상태에 맞는 화면을 계산해 JSX를 반환한다.
// 상태의 실제 저장과 렌더링 관리는 LoginForm 함수가 아니라 React가 담당한다.
export function LoginForm({ onLogin, onClose }: Props) {
  // useState는 현재 렌더링 중인 LoginForm의 사용 위치에 이메일 상태를 연결해,
  // 다음 렌더링에서도 값을 유지한다. setEmail을 호출하면 LoginForm이 다시 렌더링된다.
  const [email, setEmail] = useState(''); // useState(초깃값)는 [상태 값, 변경 함수]를 반환한다.
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      await onLogin(email, password);
      onClose(); // 성공 → 폼 닫기 (헤더가 로그인 상태로 바뀐다)
    } catch (err) {
      // 백엔드 401 메시지("이메일 또는 비밀번호가...")를 그대로 보여준다.
      setError(err instanceof Error ? err.message : '로그인에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="auth-form" onSubmit={handleSubmit}>
      <h2>로그인</h2>
      {/*
        value={email}: email state 값을 input에 표시한다.
        onChange: 사용자 입력을 setEmail으로 email state에 반영한다.
      */}
      <input
        type="email"
        placeholder="이메일"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        required
      />
      <input
        type="password"
        placeholder="비밀번호"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        required
      />
      {error && <p className="auth-error">{error}</p>}
      <div className="auth-actions">
        <button type="submit" disabled={submitting}>
          {submitting ? '...' : '로그인'}
        </button>
        <button type="button" className="ghost" onClick={onClose}>
          취소
        </button>
      </div>
    </form>
  );
}
