import { useEffect, useState } from 'react';
import type { OrderBookSnapshot } from '../../shared/types';

// React Hook 한 줄 요약: 컴포넌트가 마운트되면 백엔드 SSE에 연결하고,
// 받은 snapshot을 state로 들고 있는다. 컴포넌트가 사라지면 연결을 닫는다.
export function useOrderBookStream(): OrderBookSnapshot | null {
  // 이 state는 Hook 자체가 아니라, 이 Hook을 호출한 컴포넌트(App)의 상태로 React에 연결된다.
  // 따라서 setSnapshot(...)을 호출하면 React가 App을 다시 렌더링한다.
  // React는 최초 마운트 때만 null을 초기값으로 저장하고, 이후 재렌더링에서는 이전 상태를 반환한다.
  // 즉, React는 컴포넌트가 useState를 사용하면 그 상태를 컴포넌트에 연결해 기억해 두었다가, 재렌더링할 때 다시 꺼내 준다.
  const [snapshot, setSnapshot] = useState<OrderBookSnapshot | null>(null);

  // Effect = 렌더링 결과 밖에 영향을 주는 부수 효과.
  // EventSource는 장기 HTTP 연결이므로, 렌더링마다 새 연결을 만들지 않고 컴포넌트 생명주기에 맞춰 생성·종료하려고 useEffect 안에서 만든다.
  useEffect(() => {
    // Vite proxy 덕분에 '/api/...'가 그대로 localhost:8080으로 전달된다.
    // x-accel-buffering: no 헤더는 Nginx 같은 중간 프록시가 SSE를 모으지 말고 바로 전달하라는 뜻이다.
    // EventSource = 브라우저 내장 SSE 클라이언트. new EventSource(url)은 JavaScript 객체를 만들면서
    // 동시에 URL로 SSE HTTP GET 요청을 시작하고, 끝나지 않은 text/event-stream 응답을 계속 읽는다.
    // HTTP 스트림으로는 연결이 유지된 채 긴 바이트/문자열 흐름이 계속 들어옵니다.
    // 그리고 브라우저의 EventSource가 내부적으로 이 스트림을 읽다가 빈 줄을 만나면 “SSE 이벤트 한 건이 끝났다”고 구분합니다.
    // new EventSource(...)로 한 번 만든 객체는 SSE 연결을 계속 유지하며 데이터를 수신하고, useEffect 함수가 끝나도 연결은 끊기지 않는다.
    // 연결이 일시적으로 끊기면 자동 재연결도 시도한다.
    const eventSource = new EventSource('/api/binance-futures/btcusdt/depth/stream');

    // HTTP 응답 스트림은 임의 크기의 바이트 조각으로 나뉘어 도착할 수 있다.
    // EventSource는 받은 바이트를 UTF-8 문자열로 디코딩하고, 줄바꿈(`\n`)을 찾으며 SSE 형식을 파싱한다.
    // 완성된 줄은 `:` 앞부분으로 data, event, id 등의 필드를 구분하고,
    // 아직 줄바꿈이 없는 미완성 문자열은 다음 바이트 조각이 올 때까지 내부 버퍼에 보관한다.
    // 빈 줄(`\n\n`)을 만나 SSE 이벤트 한 건이 완성되면, event 이름이 없으므로 기본 'message' 이벤트를 발생시킨다.
    // 브라우저는 MessageEvent 객체를 event 매개변수로 전달하면서,
    // onmessage 프로퍼티에 등록된 콜백 함수를 호출한다.
    eventSource.onmessage = (event) => {
      const data: OrderBookSnapshot = JSON.parse(event.data); // JSON.parse(...)는 JSON 문자열을 JavaScript 객체로 변환하는 함수입니다.
      setSnapshot(data);
    };

    // EventSource가 관리하는 SSE 연결에 문제가 생기면 브라우저가 error 이벤트를 발생시켜 이 콜백을 호출한다.
    eventSource.onerror = (err) => {
      // 브라우저가 자동 재연결을 시도하므로 여기는 로깅만.
      console.error('SSE error', err);
    };

    // cleanup: useEffect의 return은 React에 "이 Effect를 나중에 되돌릴 방법"을 알려 주는 약속이다.
    // 반환된 함수는 위에서 만든 eventSource를 기억하고, React가 unmount 시 또는 effect 재실행 직전에 호출한다.
    return () => {
      // EventSource는 new EventSource(url) 자체가 장기 HTTP 연결을 시작하고 유지하도록 설계된 브라우저 API다.
      // 따라서 더 이상 필요 없을 때 close()로 SSE HTTP 연결을 직접 종료해 연결 누수를 막는다.
      eventSource.close();
    };
  }, []); // App이 재렌더링되면 useEffect(...) 호출은 다시 평가되어 React가 의존성을 확인한다.
  // 하지만 []에는 비교할 값이 없어 이전과 동일하므로, Effect 콜백(위의 SSE 연결 생성)은 최초 마운트 뒤에만 실행된다.
  // 따라서 snapshot 수신으로 App이 재렌더링되어도 EventSource를 새로 연결하지 않으며,
  // App이 언마운트될 때만 위에서 반환한 cleanup 함수가 연결을 닫는다.

  return snapshot;
}
