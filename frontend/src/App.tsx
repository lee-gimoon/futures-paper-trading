import { Navigate, Route, Routes } from 'react-router-dom';

import LandingPage from './landing/LandingPage';
import TradingPage from './trading/TradingPage';

// App은 URL과 페이지 컴포넌트를 연결하는 최상위 라우팅 컴포넌트다.
//
// BrowserRouter(main.tsx)는 브라우저의 현재 주소에서 pathname을 읽어 라우팅 정보로 관리하고,
// 자신 안에 렌더링된 App의 Routes가 그 정보를 사용할 수 있도록 전달한다.
// Routes는 BrowserRouter가 전달한 현재 pathname과 아래 Route들의 path를 비교해 일치하는 Route를 고르고, 그 Route의 element에 지정된 화면 컴포넌트를 렌더링한다.
// Route는 path로 적용할 URL 경로를 지정하고, element로 현재 주소가 그 경로와 일치할 때 렌더링할 화면 컴포넌트를 지정한다.
// 즉, BrowserRouter가 브라우저 주소를 현재 pathname으로 관리하면, Routes가 그 pathname을 각 Route의 path와 비교해 렌더링할 element를 결정한다.
//
// /       → 서비스 소개와 거래 진입 버튼이 있는 랜딩페이지
// /trade  → 기존 BTCUSDT 차트·호가창·모의 거래 화면
export default function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/trade" element={<TradingPage />} />
      {/* 정의되지 않은 프런트엔드 주소는 랜딩페이지로 돌려보낸다. */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
