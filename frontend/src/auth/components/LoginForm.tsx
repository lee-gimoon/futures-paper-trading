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
    /* 1. React가 시작될 때 root에 submit 이벤트를 받을 내부 리스너를 미리 등록한다.
       2. 사용자가 type="submit" 버튼을 클릭하면 브라우저가 form의 submit 이벤트를 발생시킨다.
       3. 이벤트가 DOM 트리를 따라 전파되어 root의 React 내부 리스너에 도착한다.
       4. React 내부 콜백이 이 form의 onSubmit={handleSubmit}을 찾아 handleSubmit(e)를 호출한다. */
    <form className="auth-form" onSubmit={handleSubmit}>
      <h2>로그인</h2>
      {/* 이메일을 입력받는 input이다. placeholder는 비어 있을 때의 안내 문구다.
          type="email"과 required는 폼 제출 시 브라우저가 빈 값과 이메일 형식을 검사하게 한다.
          main.tsx의 ReactDOM.createRoot(div#root) 호출 시, ReactDOM 구현 코드가 내부적으로 root에 이벤트 리스너를 등록한다.
          사용자 입력 → 브라우저가 실제 DOM input.value를 변경
          → input 이벤트가 상위 브라우저 DOM div#root까지 버블링 → 브라우저가 그곳에 ReactDOM이 등록한 리스너를 호출
          → ReactDOM의 이벤트 처리 코드가 브라우저 이벤트 객체를 감싼 React 이벤트 객체 e를 만든다.
          → ReactDOM이 e를 인자로 넣어 onChange prop에 등록된 함수를 호출한다.
          → setEmail(e.target.value)로 state 저장 → 다시 렌더링한 value={email}이 input 표시값을 갱신한다. */}
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
        {/* 사용자가 클릭하면 브라우저가 click 이벤트를 발생시킨다.
            이 버튼에는 onClick이 없으므로 click을 처리할 함수는 없다.
            이후 type="submit"의 기본 동작으로 form의 submit 이벤트가 발생한다.
            disabled={submitting}: 요청 중(true)에는 중복 로그인을 막기 위해 클릭할 수 없다.
            아래 삼항 연산자: 요청 중에는 '로그인 중...', 평소에는 '로그인'을 표시한다. */}
        <button type="submit" disabled={submitting}>
          {submitting ? '로그인 중...' : '로그인'}
        </button>
        {/* type="button"은 form 안에 있어도 submit 이벤트를 만들지 않는 일반 버튼이다.
            사용자가 클릭 → 브라우저가 click 이벤트 발생 → React 내부 리스너가 받음
            → React가 이 버튼의 onClick={onClose}를 찾아 onClose()를 호출한다. */}
        <button type="button" className="ghost" onClick={onClose}>
          취소
        </button>
      </div>
    </form>
  );
}
