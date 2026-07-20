import { useState, type FormEvent } from 'react';

type Props = {
  // 부모가 실제 로그인 처리와 폼 닫기 동작을 전달한다.
  onLogin: (email: string, password: string) => Promise<void>;
  onClose: () => void;
};

// 이 컴포넌트의 state로 로그인 폼 화면을 계산한다.
export function LoginForm({ onLogin, onClose }: Props) {
  // 폼이 기억해야 하는 값: 입력값, 오류, 요청 진행 여부
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault(); // 페이지 이동 대신 React가 로그인 처리를 계속한다.
    setError('');
    setSubmitting(true); // 버튼을 잠그고 로딩 문구를 보여 준다.
    try {
      await onLogin(email, password); // 부모가 전달한 로그인 작업이 끝날 때까지 기다린다.
      onClose(); // 성공했을 때만 폼을 닫는다.
    } catch (err) {
      // 실패 이유를 state에 저장하면 다음 렌더링에서 화면에 보인다.
      setError(err instanceof Error ? err.message : '로그인에 실패했습니다.');
    } finally {
      setSubmitting(false); // 성공·실패와 관계없이 버튼을 다시 사용할 수 있게 한다.
    }
  }

  return (
    <form className="auth-form" onSubmit={handleSubmit}>
      <h2>로그인</h2>
      {/* state가 input 값을 정하고, 사용자의 입력은 다시 state로 돌아온다. */}
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
