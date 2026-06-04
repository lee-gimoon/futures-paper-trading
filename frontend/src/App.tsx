import { useState } from 'react';
import { useOrderBookStream } from './useOrderBookStream';
import { OrderBook } from './OrderBook';
import { CandleChart } from './CandleChart';
import { useAuth } from './auth/useAuth';
import { LoginForm } from './auth/LoginForm';
import { SignupForm } from './auth/SignupForm';

// 화면 셸: 상단 인증 바 + 캔들 차트 + 호가창.
// - 차트/호가창은 공개 데이터라 로그인과 무관하게 항상 보인다.
// - 인증은 상단 바에만 얹는다: 비로그인 → 로그인/회원가입 버튼, 로그인 → 이름 + 로그아웃.
type FormMode = 'login' | 'signup' | null;

export default function App() {
  const snapshot = useOrderBookStream();
  const { user, loading, login, signup, logout } = useAuth();
  const [form, setForm] = useState<FormMode>(null);

  return (
    <div className="app">
      <header className="topbar">
        <h1>BTCUSDT 차트 / 호가창</h1>
        <div className="auth-box">
          {loading ? null : user ? (
            <>
              <span className="auth-user">{user.displayName || user.email}님</span>
              <button className="ghost" onClick={logout}>
                로그아웃
              </button>
            </>
          ) : (
            <>
              <button onClick={() => setForm('login')}>로그인</button>
              <button onClick={() => setForm('signup')}>회원가입</button>
            </>
          )}
        </div>
      </header>

      {form === 'login' && <LoginForm onLogin={login} onClose={() => setForm(null)} />}
      {form === 'signup' && <SignupForm onSignup={signup} onClose={() => setForm(null)} />}

      <div className="layout">
        <div className="chart-col">
          <CandleChart snapshot={snapshot} />
        </div>
        <div className="book-col">
          {snapshot ? (
            <OrderBook snapshot={snapshot} />
          ) : (
            <p className="empty">호가 데이터 수신 대기 중...</p>
          )}
        </div>
      </div>
    </div>
  );
}
