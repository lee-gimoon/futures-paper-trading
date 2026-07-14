// 훅(Hook)은 갈고리처럼 컴포넌트에서 React의 추가 기능을 사용하게 해 주는 함수다.
// 훅으로 UI에 필요한 상태를 기억하거나, 외부 데이터를 구독하고, 타이머 같은 동작을 관리할 수 있다.
// 상태가 바뀌면 React는 컴포넌트를 다시 실행해 화면을 갱신한다.
// React가 제공하는 훅: useState는 값을 기억하고, useMemo는 계산 결과를 재사용한다.
import { useMemo, useState } from 'react';

// market 도메인: 서버/SSE에서 공개 시세 데이터를 가져오는 커스텀 훅.
import { useOrderBookStream } from './market/hooks/useOrderBookStream';

// market 도메인: 화면을 구성하는 독립적인 UI 단위인 React 함수(컴포넌트). JSX에서 <OrderBook />처럼 사용한다.
import { OrderBook } from './market/components/OrderBook';
import { CandleChart } from './market/components/CandleChart';

// market 도메인: 시세 값만 계산하는 일반 TypeScript 함수.
import { deriveQuote } from './market/engine/quote';

// auth 도메인: 로그인 상태와 인증 동작을 관리하는 커스텀 훅.
import { useAuth } from './auth/hooks/useAuth';

// auth 도메인: 로그인과 회원가입 화면을 만드는 React 컴포넌트.
import { LoginForm } from './auth/components/LoginForm';
import { SignupForm } from './auth/components/SignupForm';

// paper 도메인: 로그인 사용자의 거래 데이터와 동작을 관리하는 커스텀 훅.
import { useTrading } from './paper/hooks/useTrading';

// paper 도메인: 모의 거래 화면을 만드는 React 컴포넌트.
import { TradingPanel } from './paper/components/TradingPanel';
import { FillHistory } from './paper/components/FillHistory';
import { OrderList } from './paper/components/OrderList';

// `type` 키워드가 붙은 import는 실행 코드가 아니라 TypeScript 타입 정보만 가져온다.
import type { OrderBookSnapshot } from './shared/types';

// App의 화면 역할과 컴포넌트 계층
// - App은 상단 인증 바, 공개 시세 화면, 로그인 후 거래 화면을 조립하는 최상위 컴포넌트다.
// - 캔들 차트는 로그인 여부와 관계없이 표시한다.
// - 호가 데이터(snapshot)가 아직 없으면 OrderBook 대신 "호가 데이터 수신 대기 중..." 메시지를 표시한다.
// - 거래 패널(주문·계좌·PnL)과 주문/체결 내역은 로그인한 사용자에게만 표시한다.
//
// main.tsx
//   └─ root.render(<App />)
//        └─ App
//             ├─ 인증 영역: 로딩 상태 또는 사용자/로그아웃 또는 로그인·회원가입 버튼
//             ├─ 인증 오류 메시지                         (authError가 있을 때)
//             ├─ LoginForm                               (form === 'login')
//             ├─ SignupForm                              (form === 'signup')
//             ├─ 공개 시세 화면                          (user가 없을 때)
//             │  ├─ CandleChart
//             │  └─ OrderBook 또는 수신 대기 메시지
//             └─ AuthenticatedTradingLayout              (user가 있을 때)
//                ├─ CandleChart
//                ├─ OrderList
//                ├─ FillHistory
//                ├─ TradingPanel
//                └─ OrderBook 또는 수신 대기 메시지

// 현재 열린 인증 폼의 종류. null이면 로그인/회원가입 폼을 열지 않은 상태다.
// FormMode는 변수명이 아닌 타입 이름이며, FormMode 타입 변수에는 'login', 'signup', null만 저장할 수 있다.
type FormMode = 'login' | 'signup' | null;

// 로그인했을 때만 표시할 거래 레이아웃이 부모(App)로부터 받는 값들.
// `type TradingLayoutProps = { ... }`는 TradingLayoutProps 타입의 모양을 정의하며,
// 이 타입으로 선언된 변수에는 이 모양을 만족하는 객체만 저장할 수 있다.
type TradingLayoutProps = {
  snapshot: OrderBookSnapshot | null;
  midPrice: number | null;
  limitFill: { price: number; n: number } | null;
  onPriceClick: (price: number) => void;
  onUnauthorized: () => void;
};

// 인증된 사용자의 3열 화면(차트·거래 패널·호가창)을 담당하는 보조 컴포넌트.
// App은 인증과 공개 시세를 관리하고, 이 컴포넌트는 사용자별 거래 데이터를 조회해 표시한다.
function AuthenticatedTradingLayout({
  snapshot,
  midPrice,
  limitFill,
  onPriceClick,
  onUnauthorized,
}: TradingLayoutProps) {
  // 포트폴리오, 주문, 체결 내역과 갱신 함수. 인증이 만료되면 부모가 전달한 onUnauthorized를 호출한다.
  const { portfolio, orders, fills, loading, error, refresh } = useTrading(onUnauthorized);

  // 전체 주문 중 아직 체결/취소되지 않은 OPEN 상태만 골라낸다.
  // orders가 바뀔 때만 다시 계산하도록 useMemo를 사용한다.
  const openOrders = useMemo(() => orders.filter((order) => order.status === 'OPEN'), [orders]);

  return (
    <>
      {/* 왼쪽 열: 캔들 차트와 주문·체결 활동 내역 */}
      <div className="chart-col">
        <CandleChart snapshot={snapshot} position={portfolio?.position ?? null} openOrders={openOrders} />
        <div className="chart-activity">
          <section className="activity-panel pending-orders">
            <h3>대기 주문</h3>
            {/* 주문 목록에서 변경되면 refresh를 호출해 거래 데이터를 다시 가져온다. */}
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

      {/* 가운데 열: 실제 주문 입력과 계좌/PnL 정보를 보여 주는 거래 패널 */}
      <div className="trade-col">
        <TradingPanel
          portfolio={portfolio}
          midPrice={midPrice}
          limitFill={limitFill}
          loading={loading}
          error={error}
          onChanged={refresh}
        />
      </div>

      {/* 오른쪽 열: snapshot이 도착한 경우에만 호가창을 표시한다. */}
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

// 앱의 최상위 React 함수형 컴포넌트.
// 반환 타입을 쓰지 않아도 TypeScript가 아래 JSX 반환값을 보고 반환 타입을 추론한다.
// JSX 요소는 실제 브라우저 DOM의 <div>가 아니라, React가 나중에 DOM에 그릴 화면 구조를 나타내는 JavaScript 객체다.
// JSX
//   ↓
// React 요소 객체(“이 위치에 div와 h1을 보여 줘”라는 설명)
//   ↓
// ReactDOM이 설명을 해석
//   ↓
// 실제 DOM 요소 생성·수정
export default function App() {
  // SSE로 수신되는 최신 호가/시세 snapshot. 이 값이 바뀌면 App과 하위 컴포넌트가 다시 렌더링된다.
  const snapshot = useOrderBookStream();

  // 현재 로그인 사용자, 인증 처리 상태, 인증 동작을 useAuth 훅에서 가져온다.
  const { user, loading, error: authError, login, signup, logout, expireSession } = useAuth();

  // 어떤 인증 폼을 열지 저장한다. setForm으로 값을 바꾸면 해당 폼이 화면에 표시된다.
  const [form, setForm] = useState<FormMode>(null);

  // 거래 패널의 미실현 PnL을 실시간으로 갱신하려고 SSE snapshot에서 mid를 뽑아 넘긴다.
  const quote = snapshot ? deriveQuote(snapshot) : null;
  const midPrice = quote ? quote.midPrice : null;

  // 호가창에서 클릭한 가격을 주문폼 지정가로 흘려보낸다. n(증가 카운터)으로 같은 가격을 다시 눌러도 반영되게 한다.
  const [limitFill, setLimitFill] = useState<{ price: number; n: number } | null>(null);
  const handlePriceClick = (price: number) => setLimitFill((prev) => ({ price, n: (prev?.n ?? 0) + 1 }));

  return (
    // 앱 전체를 감싸는 최상위 컨테이너. className은 styles.css의 스타일 선택자와 연결된다.
    <div className="app">
      {/* 상단 바: 로그인 상태에 따라 사용자/로그아웃 또는 로그인/회원가입 버튼을 조건부로 표시한다. */}
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

      {/* authError가 있을 때만 오류 문단을 렌더링한다. role="alert"는 보조 기술에 오류 발생을 알린다. */}
      {authError && <p className="auth-error" role="alert">{authError}</p>}

      {/* form 상태가 일치할 때만 해당 폼 컴포넌트를 렌더링한다. 폼을 닫으면 form을 null로 되돌린다. */}
      {form === 'login' && <LoginForm onLogin={login} onClose={() => setForm(null)} />}
      {form === 'signup' && <SignupForm onSignup={signup} onClose={() => setForm(null)} />}

      <div className="layout">
        {/* 비로그인 상태: 공개 시세인 차트와 호가창만 양쪽 열에 표시한다. */}
        {!user && (
          <div className="chart-col">
            <CandleChart snapshot={snapshot} />
          </div>
        )}

        {/* 로그인 상태: 사용자 거래 정보까지 포함한 3열 레이아웃을 표시한다. */}
        {user && (
          <AuthenticatedTradingLayout
            snapshot={snapshot}
            midPrice={midPrice}
            limitFill={limitFill}
            onPriceClick={handlePriceClick}
            onUnauthorized={expireSession}
          />
        )}

        {/* snapshot이 아직 없으면 호가창 대신 수신 대기 메시지를 표시한다. */}
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
