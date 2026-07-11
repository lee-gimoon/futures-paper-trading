import { useState, type FormEvent } from 'react';

type Props = {
  onSignup: (email: string, password: string, displayName: string) => Promise<void>;
  onClose: () => void;
};

export function SignupForm({ onSignup, onClose }: Props) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      await onSignup(email, password, displayName);
      onClose(); // 성공 → 가입+로그인까지 끝났으므로 폼 닫기
    } catch (err) {
      // 중복 이메일 409 등 백엔드 메시지를 그대로 보여준다.
      setError(err instanceof Error ? err.message : '회원가입에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="auth-form" onSubmit={handleSubmit}>
      <h2>회원가입</h2>
      <input
        type="email"
        placeholder="이메일"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        required
      />
      <input
        type="password"
        placeholder="비밀번호 (8자 이상)"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        minLength={8}
        required
      />
      <input
        type="text"
        placeholder="표시 이름"
        value={displayName}
        onChange={(e) => setDisplayName(e.target.value)}
      />
      {error && <p className="auth-error">{error}</p>}
      <div className="auth-actions">
        <button type="submit" disabled={submitting}>
          {submitting ? '...' : '회원가입'}
        </button>
        <button type="button" className="ghost" onClick={onClose}>
          취소
        </button>
      </div>
    </form>
  );
}
