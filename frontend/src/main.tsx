import ReactDOM from 'react-dom/client';
import App from './App';
import './styles.css';

// React 진입점. index.html의 <div id="root">를 찾아 그 안에 App을 그린다.
//
// ⚠️ <React.StrictMode>를 의도적으로 제거했다.
// 이유: dev 모드에서 StrictMode는 useEffect를 mount → cleanup → mount로 두 번 강제 실행한다.
//      그 사이클이 useOrderBookStream의 EventSource(SSE) 연결과 잘 안 맞아서,
//      부팅 직후 첫 접속 시 ES1이 첫 데이터를 받자마자 close되고 ES2가 race에 빠져
//      화면이 첫 데이터 1장만 그려진 채로 멈추는 현상이 있었다.
//      production 빌드에서는 StrictMode 더블 마운트가 일어나지 않으므로 영향 0.
//      나중에 다른 컴포넌트가 늘어나면 다시 켜서 cleanup 견고성을 검증할 예정.
ReactDOM.createRoot(document.getElementById('root')!).render(
  <App />
);
