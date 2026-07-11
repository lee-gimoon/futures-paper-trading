import { useMemo, useState } from 'react';
import { useOrderBookStream } from './market/hooks/useOrderBookStream';
import { OrderBook } from './market/components/OrderBook';
import { CandleChart } from './market/components/CandleChart';
import { deriveQuote } from './market/engine/quote';
import { useAuth } from './auth/hooks/useAuth';
import { LoginForm } from './auth/components/LoginForm';
import { SignupForm } from './auth/components/SignupForm';
import { TradingPanel } from './paper/components/TradingPanel';
import { FillHistory } from './paper/components/FillHistory';
import { OrderList } from './paper/components/OrderList';
import { useTrading } from './paper/hooks/useTrading';
import type { OrderBookSnapshot } from './shared/types';

// 화면 셸: 상단 인증 바 + 캔들 차트 + (로그인 시) 거래 패널 + 호가창.
// - 차트/호가창은 공개 데이터라 로그인과 무관하게 항상 보인다.
// - 거래 패널(주문·계좌·PnL)은 로그인했을 때만 가운데 컬럼에 뜬다.
// - 인증은 상단 바에만 얹는다: 비로그인 → 로그인/회원가입 버튼, 로그인 → 이름 + 로그아웃.
type FormMode = 'login' | 'signup' | null;

type TradingLayoutProps = {
  snapshot: OrderBookSnapshot | null;
  midPrice: number | null;
  limitFill: { price: number; n: number } | null;
  onPriceClick: (price: number) => void;
};

function AuthenticatedTradingLayout({ snapshot, midPrice, limitFill, onPriceClick }: TradingLayoutProps) {
  const { portfolio, orders, fills, refresh } = useTrading();
  const openOrders = useMemo(() => orders.filter((order) => order.status === 'OPEN'), [orders]);

  return (
    <>
      <div className="chart-col">
        <CandleChart snapshot={snapshot} position={portfolio?.position ?? null} openOrders={openOrders} />
        <div className="chart-activity">
          <section className="activity-panel pending-orders">
            <h3>대기 주문</h3>
            <OrderList
              orders={openOrders}
              position={portfolio?.position ?? null}
              onChanged={refresh}
              emptyLabel="대기 주문 없음"
            />
          </section>
          <section className="activity-panel fills-panel">
            <h3>나의 체결 내역</h3>
            <FillHistory fills={fills} />
          </section>
        </div>
      </div>
      <div className="trade-col">
        <TradingPanel portfolio={portfolio} midPrice={midPrice} limitFill={limitFill} onChanged={refresh} />
      </div>
      <div className="book-col">
        {snapshot ? (
          <OrderBook snapshot={snapshot} onPriceClick={onPriceClick} />
        ) : (
          <p className="empty">호가 데이터 수신 대기 중...</p>
        )}
      </div>
    </>
  );
}

export default function App() {
  const snapshot = useOrderBookStream();
  const { user, loading, login, signup, logout } = useAuth();
  const [form, setForm] = useState<FormMode>(null);

  // 거래 패널의 미실현 PnL을 실시간으로 갱신하려고 SSE snapshot에서 mid를 뽑아 넘긴다(호가창·차트와 같은 값).
  const quote = snapshot ? deriveQuote(snapshot) : null;
  const midPrice = quote ? quote.midPrice : null;

  // 호가창에서 클릭한 가격을 주문폼 지정가로 흘려보낸다. n(증가 카운터)으로 같은 가격을 다시 눌러도 반영되게 한다.
  const [limitFill, setLimitFill] = useState<{ price: number; n: number } | null>(null);
  const handlePriceClick = (price: number) => setLimitFill((prev) => ({ price, n: (prev?.n ?? 0) + 1 }));

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
        {!user && (
          <div className="chart-col">
            <CandleChart snapshot={snapshot} />
          </div>
        )}
        {user && (
          <AuthenticatedTradingLayout
            snapshot={snapshot}
            midPrice={midPrice}
            limitFill={limitFill}
            onPriceClick={handlePriceClick}
          />
        )}
        {!user && (
          <div className="book-col">
          {snapshot ? (
            <OrderBook snapshot={snapshot} onPriceClick={handlePriceClick} />
          ) : (
            <p className="empty">호가 데이터 수신 대기 중...</p>
          )}
          </div>
        )}
      </div>
    </div>
  );
}
