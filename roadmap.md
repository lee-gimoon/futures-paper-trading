# 호가창 중심 실시간 모의 선물 거래소 로드맵

작성일: 2026-05-14  
마지막 업데이트: 2026-05-29

Binance USDⓈ-M Futures의 실시간 호가창을 축으로 학습용 모의 선물 거래소를 만든다.

현재 방향은 명확하다.

```text
1. 지금까지 만든 "partial depth snapshot → 서버 → SSE → React 호가창" 흐름은 MVP(Minimum Viable Product, 최소 기능 제품) 호가창으로 충분히 완성된 상태다.
2. 진짜 로컬 호가창 동기화(REST snapshot + diff depth + sequence 검증)는 나중으로 미룬다.
3. 당장은 차트 → 자체 회원가입/로그인 → 호가창 기준 모의 주문 → 계좌/포지션/PnL 순서로 거래 앱의 뼈대를 먼저 만든다.
```

---

## 핵심 원칙

```text
1. 호가창 먼저, 체결가 나중.
   - best bid, best ask, mid price는 호가창에서 직접 만든다.
   - 차트, 주문 체결, 미실현 PnL 전부 이 값을 축으로 확장한다.

2. 지금은 "정확한 거래소 로컬 오더북"보다 "학습 가능한 거래 흐름"이 우선이다.
   - 현재 Binance partial depth20@100ms는 상위 20개 bid/ask snapshot을 계속 준다.
   - 화면 표시, best bid/ask 계산, 간단한 모의 체결에는 충분하다.
   - diff depth sequence 검증은 나중에 정확도가 필요해질 때 붙인다.

3. 단계별로 가장 단순한 코드부터 짠다.
   - 동시성 보호, 재연결, 중복 방지, 복수 계좌는 실제로 필요해질 때 도입한다.
   - 인증은 차트 다음에 자체 회원가입/로그인 방식으로 도입한다.

4. 가격과 수량은 백엔드에서 BigDecimal로 다룬다.
   - 프론트 표시는 number로 시작해도 되지만, 정밀도가 중요해지는 단계에서 string/decimal 전략을 다시 잡는다.

5. 초기 검증은 로그 + 화면 + 수동 호출 중심이다.
   - 도메인 계산이 복잡해지는 주문/계좌 단계부터 테스트를 붙인다.
```

---

## 현재 완료된 흐름

```text
Binance depth20@100ms
  → WebFlux WebSocket 수신
  → raw JSON 로그
  → OrderBookSnapshot 파싱
  → LatestOrderBookSnapshotStore.latest 저장
  → Sinks.Many.replay(1)에 tryEmitNext
  → /depth/stream SSE
  → React EventSource
  → 호가창 표시
```

현재 코드는 "브라우저가 붙어 있어야 Binance를 구독하는 구조"가 아니다. Binance 수신은 `@PostConstruct`에서 서버 부팅 시 1회 시작되고, 브라우저 SSE 구독은 `sink.asFlux()` 아래쪽에서만 생긴다. 그래서 브라우저가 나가도 Binance 수신 파이프라인은 영향을 받지 않는다.

---

## 0단계. Spring Boot 기본 기동 완료

목표:

```text
Spring Boot 애플리케이션이 뜬다.
WebFlux 기반으로 서버를 띄운다.
```

구현 완료 항목:

```text
[O] Spring Boot 프로젝트 생성
[O] spring.application.name 설정
[O] 기본 contextLoads 테스트 유지
```

완료 기준:

```text
./gradlew.bat test
./gradlew.bat bootRun
```

---

## 1단계. Binance Futures raw WebSocket 수신 완료

목표:

```text
BTCUSDT USDⓈ-M Futures partial depth stream에 연결한다.
받은 JSON 문자열을 로그에 찍는다.
```

stream:

```text
wss://fstream.binance.com/ws/btcusdt@depth20@100ms
```

구현 완료 항목:

```text
[O] ReactorNettyWebSocketClient 사용
[O] session.receive()로 WebSocketMessage Flux 수신
[O] WebSocketMessage::getPayloadAsText로 raw JSON 추출
[O] raw JSON 길이 로그 출력
[O] @PostConstruct에서 서버 부팅 시 자동 연결
```

처음 로드맵과 달라진 점:

```text
초기에는 POST /raw/start로 수동 시작하는 계획이었다.
현재는 사용자 행동과 Binance 연결 생명주기를 분리하기 위해 @PostConstruct 1회 자동 시작으로 바꿨다.
이 방향이 더 맞다. 브라우저가 몇 명 붙든 Binance 연결은 서버당 1개로 유지된다.
```

---

## 2단계. raw JSON 파싱 완료

목표:

```text
raw JSON에서 bids, asks 배열을 뽑아 자바 도메인 객체로 만든다.
```

현재 모델:

```text
OrderBookLevel
- price: BigDecimal
- quantity: BigDecimal

OrderBookSnapshot
- symbol: String
- eventTime: long
- bids: List<OrderBookLevel>
- asks: List<OrderBookLevel>
```

구현 완료 항목:

```text
[O] OrderBookSnapshotParser 구현
[O] Jackson JsonNode로 s, E, b, a 필드 추출
[O] price/quantity는 String → BigDecimal로 변환
[O] 파싱 실패 시 전체 스트림을 죽이지 않고 warn 로그만 남김
```

주의:

```text
현재 데이터는 "정밀한 로컬 오더북"이 아니라 Binance가 100ms마다 보내주는 상위 20레벨 snapshot이다.
그래도 호가창 표시, best bid/ask 계산, 간단한 paper trading에는 충분하다.
```

---

## 3단계. 최신 호가창 메모리 보관 완료

목표:

```text
서버가 떠 있는 동안 최신 BTCUSDT OrderBookSnapshot 1개를 메모리에 유지한다.
HTTP GET으로 현재 snapshot을 조회할 수 있다.
```

구현 완료 항목:

```text
[O] LatestOrderBookSnapshotStore 구현
[O] AtomicReference<OrderBookSnapshot> latest 사용
[O] update(snapshot) 때 latest.set(snapshot)
[O] GET /api/binance-futures/btcusdt/depth/latest 구현
```

역할:

```text
AtomicReference latest
  - /depth/latest 단발 조회용
  - 현재 최신 snapshot 1개 확인용

Sinks.Many sink
  - 실시간 SSE push용
  - 브라우저 fan-out + 최신 1건 replay용
```

---

## 4단계. Sink 기반 SSE push 완료

목표:

```text
호가 snapshot이 들어오는 순간 브라우저 SSE 구독자들에게 바로 push한다.
브라우저 생명주기와 Binance 수신 생명주기를 분리한다.
```

구현 완료 항목:

```text
[O] Sinks.many().replay().limit(1) 사용
[O] update(snapshot)에서 sink.tryEmitNext(snapshot)
[O] stream()에서 sink.asFlux() 노출
[O] Controller가 latestStore.stream().map(ServerSentEvent::build) 반환
[O] 브라우저가 늦게 붙어도 최신 1건 replay
```

핵심 의미:

```text
Binance 수신 파이프라인
  - 서버 부팅 시 @PostConstruct에서 1회 subscribe
  - 브라우저가 없어도 계속 돈다

브라우저 응답 파이프라인
  - /depth/stream 요청마다 sink.asFlux() 아래쪽에 구독이 생긴다
  - 탭을 닫으면 그 구독만 cancel된다

tryEmitNext
  - 구독을 만드는 메서드가 아니라 Sink의 producer-side emission API
  - #D에서 호출되지만 #D와 Sink 사이에 Subscription을 만들지 않는다
```

확장 한계:

```text
현재 구조는 단일 서버 local fan-out이다.
수백 명까지는 괜찮을 수 있지만 1,000명 전후부터는 부하 테스트가 필요하다.
수천~만 명 규모는 로드밸런싱, 브로커, 선직렬화, throttle/diff 전송 같은 확장 설계가 필요하다.
```

---

## 5단계. React 실시간 호가창 표시 완료

목표:

```text
React 화면에서 실시간 BTCUSDT 호가창을 보여준다.
```

구현 완료 항목:

```text
[O] Vite + React + TypeScript 프론트 구성
[O] EventSource('/api/binance-futures/btcusdt/depth/stream') 사용
[O] SSE data를 OrderBookSnapshot으로 파싱
[O] asks / bids 테이블 표시
[O] Vite proxy로 /api → localhost:8080 연결
[O] SSE buffering 비활성 힌트 추가
```

현재 호가창의 의미:

```text
Binance partial depth20 snapshot을 그대로 보여주는 MVP(Minimum Viable Product, 최소 기능 제품) 호가창이다.
정밀한 로컬 오더북은 아니지만, 사용자 화면과 다음 단계의 차트/모의 주문 기준값으로 쓰기에 충분하다.
```

---

## 6단계. 실시간 차트 (호가 파생 → 진짜 캔들 차트)

배경 / 방향 전환:

```text
처음엔 호가창에서 파생한 bestBid / bestAsk / midPrice 라인을 그렸지만,
이건 "호가(걸린 주문)" 기반이라 바이낸스 캔들차트와 데이터가 다르다.
바이낸스식 봉차트는 "체결(trade)"을 모은 kline 데이터에서 나온다.

호가 데이터의 한계:
- midPrice는 체결가가 아니라 호가 중간값 → 공식 캔들과 미세하게 다름
- 거래량(volume)이 없음 → 호가엔 체결량 정보가 없음
- 과거 봉 backfill 불가 → 접속 순간부터만 쌓임

따라서 진짜 바이낸스식 캔들 차트는 kline 데이터로 그린다.
```

데이터 경로 결정 — 프론트엔드에서 직접:

```text
kline은 프론트엔드(브라우저)에서 바이낸스에 직접 요청한다. 백엔드는 건드리지 않는다.
- 과거 봉:  REST  GET https://fapi.binance.com/fapi/v1/klines?symbol=BTCUSDT&interval=<i>&limit=500
- 실시간 봉: 같은 REST를 ~0.3초마다 폴링(limit=2, 이전 요청 완료 후 재요청)해서 최신 봉을 갱신.

※ 원래 @kline WebSocket을 쓰려 했으나, 이 지역/네트워크에서 바이낸스 선물의 "체결 계열"
  push 스트림(kline·aggTrade·markPrice)이 메시지 0개로 차단돼 있다(측정 확인).
  반면 "호가 계열"(depth·bookTicker)과 REST는 정상. → 실시간 봉은 REST 폴링으로 우회한다.
  (나중에 체결가 라인·거래량도 같은 제약을 받으므로 REST 폴링/호가 파생으로 풀어야 한다.)
```

왜 프론트 직접인가 (정리):

```text
※ 용어: 이 프로젝트엔 서버가 둘이다.
  (a) 프론트엔드 정적 호스팅 서버 = Vercel·Netlify·nginx 등(개발 중엔 Vite dev 서버 5173).
      HTML/JS 파일만 내려준다.
      ─ Vite = (a)의 "개발판". npm run dev로 파일 서빙 + 저장 시 자동 새로고침(HMR), localhost라 나만 접속.
        배포 땐 npm run build → dist/를 Vercel이 서빙(= (a)의 배포판). 둘 다 파일만 줄 뿐 바이낸스 연결과 무관.
  (b) 스프링부트 백엔드(8080) = 호가(depth) 스트림, 나중에 체결·PnL 담당.
  차트 kline은 (a)도 (b)도 거치지 않고, 브라우저가 바이낸스로 직접 간다.

1. kline은 공개 시세라 API 키/시크릿이 필요 없다 → 프론트에 둬도 보안 문제 없음.
2. 체결·PnL용 가격은 이미 (b) 스프링부트 백엔드의 호가 스트림이 갖고 있다
   → 차트 kline은 순수 "표시용", (b)를 거칠 필요가 없다.
3. 프론트 코드는 (a) 정적 호스팅 서버가 아니라 사용자 브라우저에서 실행된다.
   (a)는 HTML/JS 파일만 한 번 내려주고, WebSocket 연결은 사용자 컴퓨터 → 바이낸스로 직접 나간다.
4. 따라서 스트리밍 부담은 사용자 컴퓨터 ↔ 바이낸스가 진다.
   사용자가 1명이든 100만 명이든 (a) 정적 호스팅 서버의 부담은 늘지 않는다 (파일만 제공).
   (만약 (b) 스프링부트 백엔드가 kline을 중계하면 (b)가 SSE N개를 떠안는다.
    fan-out으로 바이낸스 연결은 1개로 줄지만 (b)의 부담은 사용자 수에 비례해 늘어난다.
    지금은 프론트 직접이 더 단순·합리적.)
```

구현 방향:

```text
1. 라이브러리: TradingView Lightweight Charts v5  (chart.addSeries(CandlestickSeries, ...))
2. 캔들 시리즈 + 타임프레임 선택 버튼 (선물 지원 봉: 1m·3m·5m·15m·30m·1h·4h·1d·1w)
   (1초봉은 선물 kline에 없음 → 필요하면 나중에 별도 집계)
3. 마운트 시 REST로 과거 봉을 채우고(setData), REST 폴링(~0.3초)으로 마지막 봉을 실시간 갱신(update)
4. 백엔드(.java)는 변경 없음
```

완료 기준:

```text
과거 봉이 채워진 채로 차트가 뜨고, 실시간으로 마지막 봉이 갱신된다.
타임프레임 버튼을 누르면 해당 봉으로 다시 그린다.
백엔드는 건드리지 않는다 (프론트가 바이낸스에 직접 요청).
```

나중에 보충:

```text
거래량(volume) 막대 — kline volume 사용
이동평균선(MA7 / 25 / 99)
호가 파생 라인(midPrice 등)을 캔들 위 오버레이로 (선택)
```

---

## 7단계. 자체 회원가입 / 로그인

목표:

```text
OAuth 없이 우리 DB에 회원 정보를 저장하는 자체 회원가입/로그인을 만든다.
이후 모의 주문, 계좌, 포지션, PnL은 로그인한 사용자 기준으로 분리한다.
```

범위:

```text
회원가입
로그인
로그아웃
내 정보 조회
```

엔드포인트 후보:

```text
POST /api/auth/signup
POST /api/auth/login
POST /api/auth/logout
GET  /api/auth/me
```

Postgres DB에 저장할 데이터:

```text
users
- id
- email
- password_hash
- display_name
- created_at
- updated_at
```

7단계에서는 회원가입/로그인을 위한 `users`만 DB에 저장한다. 모의 계좌, 주문, 체결 내역은 뒤 단계에서 각각 필요한 시점에 추가한다.

인증 방식:

```text
초기에는 OAuth를 쓰지 않는다.
Google/GitHub/Kakao 같은 외부 계정 로그인이 아니라 자체 회원가입/로그인을 구현한다.
DB는 Postgres를 사용한다.
Postgres에 사용자와 password_hash를 저장한다.
비밀번호는 평문 저장 금지. BCrypt 같은 단방향 해시를 사용한다.
로그인 방식은 우선 Session + HttpOnly Cookie로 시작한다.
JWT는 모바일 앱, 외부 API 공개, 서버 간 인증 같은 필요가 생기면 나중에 검토한다.
```

완료 기준:

```text
새 사용자가 회원가입할 수 있다.
로그인하면 내 정보를 조회할 수 있다.
로그아웃하면 보호 API에 접근할 수 없다.
주문 단계에서 사용할 current user / current account를 얻을 수 있다.
```

나중에 보충:

```text
이메일 인증
비밀번호 재설정
refresh token
소셜 로그인 OAuth
권한/관리자 기능
```

---

## 8단계. 호가창 기준 모의 주문

목표:

```text
last price가 아니라 현재 호가창으로 paper trading 주문을 체결한다.
로그인한 사용자 자신의 paper account에만 주문을 넣는다.
심볼은 우선 BTCUSDT 하나만 지원한다.
```

주문 타입:

```text
시장가 BUY
시장가 SELL
지정가 BUY
지정가 SELL
지정가 주문 취소
```

체결 규칙 MVP(Minimum Viable Product, 최소 기능 제품):

```text
시장가 매수:
  ask 쪽을 best ask부터 위로 먹는다.

시장가 매도:
  bid 쪽을 best bid부터 아래로 먹는다.

지정가 매수:
  limit price >= best ask 이면 즉시 체결.
  아니면 대기 주문으로 둔다.

지정가 매도:
  limit price <= best bid 이면 즉시 체결.
  아니면 대기 주문으로 둔다.
```

MVP(Minimum Viable Product, 최소 기능 제품) 가정:

```text
1. 우리 주문은 Binance 실제 호가창에 영향을 주지 않는다.
2. 체결 후 호가창 수량을 차감하지 않는다.
3. partial depth20 안에서 보이는 수량만 유동성으로 본다.
4. 대기 지정가 주문은 snapshot이 들어올 때마다 재평가한다.
5. 정확한 queue priority, maker/taker, 슬리피지 모델은 나중에 붙인다.
```

백엔드 후보 구조:

```text
PaperOrder
OrderSide        BUY / SELL
OrderType        MARKET / LIMIT
OrderStatus      NEW / OPEN / FILLED / CANCELED / REJECTED
Fill
PaperTradingEngine
InMemoryOrderStore
```

Postgres DB에 저장할 데이터:

```text
paper_orders
- id
- user_id
- account_id
- symbol
- side
- type
- status
- limit_price
- quantity
- filled_quantity
- created_at
- updated_at

paper_fills
- id
- order_id
- account_id
- symbol
- side
- price
- quantity
- fee
- executed_at
```

8단계에서는 사용자가 낸 주문과 실제 체결 내역을 DB에 저장한다. 현재 포지션과 잔고 원장은 처음부터 DB에 따로 저장하지 않고, 다음 단계에서 계좌/PnL을 만들 때 필요한 범위만 추가한다.

엔드포인트 후보:

```text
POST   /api/paper/orders
GET    /api/paper/orders
DELETE /api/paper/orders/{orderId}
```

완료 기준:

```text
로그인하지 않은 사용자는 주문을 넣을 수 없다.
시장가 BUY 0.01 → 현재 best ask 기준으로 즉시 체결된다.
시장가 SELL 0.01 → 현재 best bid 기준으로 즉시 체결된다.
지정가 BUY가 bestAsk 아래에 있으면 OPEN으로 남고, 호가가 내려와 닿으면 FILLED가 된다.
지정가 SELL이 bestBid 위에 있으면 OPEN으로 남고, 호가가 올라와 닿으면 FILLED가 된다.
주문/체결 결과가 로그인한 사용자 계좌에만 반영된다.
```

---

## 9단계. 계좌, 포지션, PnL

목표:

```text
8단계의 체결 결과를 계좌 잔고, 포지션, 실현/미실현 PnL로 누적한다.
```

MVP(Minimum Viable Product, 최소 기능 제품) 계좌 모델:

```text
Account
- cashBalance
- realizedPnl
- unrealizedPnl

Position
- symbol
- side 또는 signed quantity
- quantity
- averageEntryPrice
- markPrice 또는 midPrice
- unrealizedPnl

Trade
- orderId
- side
- price
- quantity
- fee
- timestamp
```

Postgres DB에 저장할 데이터:

```text
paper_accounts
- id
- user_id
- cash_balance
- created_at
- updated_at
```

9단계에서는 사용자별 모의 계좌와 현금 잔고를 DB에 저장한다. `paper_positions`와 `paper_balance_ledger`는 처음부터 저장하지 않는다. 포지션은 주문/체결 내역과 현재 호가 기준으로 먼저 계산해보고, 구조가 안정되면 별도 테이블로 분리한다.

계산 기준:

```text
진입 가격:
  체결 가격의 수량 가중 평균.

실현 PnL:
  포지션을 줄이거나 닫는 체결에서 확정.

미실현 PnL:
  우선 mid price 기준으로 계산한다.
  mark price, funding fee, liquidation은 나중에 미룬다.
```

화면:

```text
주문 입력 폼
계좌 잔고
현재 포지션
미실현 PnL
실현 PnL
주문 목록
체결 내역
```

완료 기준:

```text
BUY → SELL 순서로 거래했을 때 손익이 계좌에 반영된다.
열린 포지션의 미실현 PnL이 mid price 변화에 따라 갱신된다.
체결 내역과 현재 포지션이 서로 맞는다.
```

---

## 10단계. 운영성 보강

목표:

```text
호가창 / 차트 / 주문 / 계좌가 모두 동작한 뒤에 안정성과 운영 가시성을 추가한다.
```

추가 후보:

```text
health 엔드포인트
WebSocket 연결 status (UP / DOWN / STALE)
stale order book 감지 및 화면 표시
재연결 (지수 백오프)
중복 connect 방지
Disposable 보관 및 @PreDestroy 정리
설정 properties (stream URL, 심볼, 레벨 수)
에러 응답 표준화
```

원칙:

```text
운영성 코드는 처음부터 크게 넣지 않는다.
거래 화면이 먼저 움직인 뒤, 실제로 보이는 끊김/혼선에 대응해 붙인다.
```

---

## 11단계. 진짜 로컬 호가창 동기화(diff depth)

목표:

```text
현재 partial depth20 통째 교체 방식을 버리고,
REST snapshot + diff depth stream으로 sequence 검증 가능한 로컬 order book을 유지한다.
```

이 단계까지 미루는 이유:

```text
차트, 간단한 모의 주문, 계좌/PnL 학습에는 현재 partial depth snapshot으로 충분하다.
diff 동기화는 정확하지만 구현량이 크고, 지금 당장 주문/계좌 학습을 막는 병목은 아니다.
따라서 거래 앱의 큰 흐름을 먼저 만든 뒤 정밀도를 올리는 순서가 맞다.
```

처음 다룰 Binance 데이터:

```text
GET /fapi/v1/depth?symbol=BTCUSDT&limit=1000
wss://fstream.binance.com/ws/btcusdt@depth@100ms
각 이벤트의 U, u, pu 필드
```

동기화 알고리즘:

```text
1. diff stream 구독을 먼저 켜고 이벤트를 큐에 쌓는다.
2. REST snapshot을 받는다. snapshot에는 lastUpdateId가 들어 있다.
3. 큐에 쌓인 이벤트 중 u < lastUpdateId 인 것은 버린다.
4. 첫 유효 이벤트는 U <= lastUpdateId AND u >= lastUpdateId 여야 한다.
5. 이후 매 이벤트의 pu가 직전 이벤트의 u와 같아야 한다.
6. 불일치하면 sequence가 끊긴 것이므로 snapshot부터 다시 받는다.
7. quantity == 0 이면 해당 가격레벨을 제거한다.
```

완료 기준:

```text
sequence mismatch 발생 시 snapshot 재조회가 자동으로 일어난다.
로컬 order book의 상위 20레벨이 Binance 웹사이트와 시각적으로 일치한다.
현재의 OrderBookSnapshot/SSE/React 화면은 가능한 한 그대로 유지하고, 내부 source만 정밀한 로컬 book으로 교체한다.
```

---

## 지금 당장 할 일

```text
[O] 0단계: Spring Boot 기본 기동
[O] 1단계: Binance raw WebSocket 수신
[O] 2단계: OrderBookSnapshot 파싱
[O] 3단계: 최신 snapshot 메모리 보관
[O] 4단계: Sinks.Many 기반 SSE push
[O] 5단계: React 실시간 호가창 표시
[ ] 6단계: best bid / best ask / mid price / spread 차트
[ ] 7단계: 자체 회원가입 / 로그인
[ ] 8단계: 호가창 기준 모의 주문
[ ] 9단계: 계좌, 포지션, PnL
[ ] 10단계: 운영성 보강
[ ] 11단계: REST snapshot + diff depth 로컬 호가창 동기화
```

---

## 전체 로드맵 이후로 미루는 것

```text
- OAuth 소셜 로그인: 자체 회원가입/로그인이 굳어진 뒤 필요하면 도입.
- 복수 심볼: 단일 BTCUSDT 주문/계좌 흐름이 안정된 뒤.
- 멀티 계좌: 사용자별 기본 paper account가 안정된 뒤.
- 레버리지 / 마진 / 청산: 기본 포지션/PnL 이후.
- 수수료 / 펀딩비: 체결과 포지션 계산이 안정된 뒤.
- 백테스트 / 시뮬레이션 시간 가속: 실시간 흐름이 굳어진 뒤.
```
