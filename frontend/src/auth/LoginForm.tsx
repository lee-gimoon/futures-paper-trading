import { useState, type FormEvent } from 'react';

type Props = {
  onLogin: (email: string, password: string) => Promise<void>;
  onClose: () => void;
};

export function LoginForm({ onLogin, onClose }: Props) {
  const [email, setEmail] = useState('');
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
