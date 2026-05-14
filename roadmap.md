# 호가창 중심 실시간 모의 거래소 로드맵

작성일: 2026-05-14

Binance USDⓈ-M Futures의 실시간 호가창을 축으로 학습용 모의 선물 거래소를 만든다.

---

## 핵심 원칙

```text
1. 호가창 먼저, 체결가 나중.
   - best bid, best ask, mid price는 호가창에서 직접 만든다.
   - 화면, 차트, 주문 체결 전부 이 값을 축으로 확장한다.

2. 단계별로 "가장 단순한 코드"부터 짠다.
   - 동시성 보호, 재연결, 중복 방지, 테스트 hook 같은 코드는
     실제로 필요해지는 단계에서 도입한다. 미리 만들지 않는다.

3. 운영성 코드는 마지막에 붙인다.
   - health, properties, status, reconnect, stale 표시는 9단계.
   - 호가창과 차트가 먼저 움직이는 것을 본 뒤에 붙인다.

4. 가격과 수량은 BigDecimal로 다룬다. double로 다루지 않는다.

5. 초기에는 신규 테스트 파일을 만들지 않는다.
   - 기존 테스트는 유지한다.
   - 검증은 로그 + 화면 + 수동 호출 중심이다.
```

---

## 0단계. Spring Boot 실행

목표:

```text
Spring Boot 애플리케이션이 뜬다.
추가 컨트롤러, 설정 클래스, health 엔드포인트는 만들지 않는다.
```

완료 기준:

```text
./gradlew.bat test      → 통과
./gradlew.bat bootRun   → 정상 기동
```

이 단계에서 도입하지 않는 것:

```text
application.yaml의 커스텀 properties
프로필 분리
DB, JPA, Redis
```

---

## 1단계. Binance Futures 호가 raw JSON 출력

목표:

```text
BTCUSDT USDⓈ-M Futures partial depth stream에 연결한다.
받은 JSON 문자열을 그대로 로그에 찍는다.
앱 시작 시 자동 연결하지 않고, HTTP POST 요청이 들어왔을 때만 연결한다.
```

stream:

```text
wss://fstream.binance.com/ws/btcusdt@depth20@100ms
```

이 stream은 BTCUSDT 상위 20개 bid / ask 가격레벨을 100ms 단위로 통째로 보내준다.

완료 기준:

```text
POST /api/binance-futures/btcusdt/depth/raw/start
→ 100ms 간격으로 로그 라인이 흘러나온다.
→ 각 라인이 bids, asks 배열을 담은 JSON으로 보인다.
```

이 단계에서 도입하지 않는 것:

```text
JSON 파싱
중복 connect 방지 동시성 코드 (synchronized, volatile)
재연결
연결 status 노출
@PreDestroy 정리 (JVM 종료에 맡긴다)
테스트 주입용 부생성자
```

확인할 공식 문서:

```text
Binance USDⓈ-M Futures WebSocket Market Streams
Partial Book Depth Streams
```

---

## 2단계. bids / asks 배열만 추출

목표:

```text
raw JSON에서 bids, asks 배열만 뽑아 자바 객체로 만든다.
1단계의 raw 로그는 그대로 두되, 파싱된 결과도 함께 로그에 찍는다.
```

만들 최소 모델:

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

주의:

```text
가격과 수량은 double이 아니라 BigDecimal로 읽는다.
배열 원소는 ["price", "quantity"] 두 개짜리 문자열 배열이다.
정렬 가정에 기대지 않는다.
  bids는 가격 내림차순, asks는 가격 오름차순으로 도착하지만,
  사용 시점에 다시 정렬해서 쓴다.
```

완료 기준:

```text
파싱된 OrderBookSnapshot이 toString 또는 별도 log 라인으로 찍힌다.
bids.size() == 20, asks.size() == 20.
```

이 단계에서 도입하지 않는 것:

```text
메모리 보관
diff 적용
update id 검증
```

---

## 3단계. 현재 호가창을 메모리에 유지

목표:

```text
서버가 떠 있는 동안 최신 BTCUSDT OrderBookSnapshot을 들고 있는다.
새 메시지가 오면 통째로 교체한다.
HTTP GET 요청으로 현재 snapshot을 조회할 수 있다.
```

"메모리에 유지"의 의미:

```text
DB 저장이 아니다.
싱글톤 빈이 latest snapshot 참조를 들고 있다는 뜻이다.
```

완료 기준:

```text
GET /api/binance-futures/btcusdt/depth/latest
→ 마지막에 받은 bids/asks가 JSON으로 응답된다.
연속 호출하면 100ms 단위로 값이 바뀐다.
```

이 단계에서 도입하지 않는 것:

```text
REST snapshot + diff depth sequence 검증 (6단계)
정밀한 로컬 order book 복구 (6단계)
재연결, stale 표시 (9단계)
```

---

## 4단계. React에서 실시간 호가창 표시

목표:

```text
백엔드의 최신 OrderBookSnapshot을 React 화면이 실시간으로 보여준다.
사용자가 처음 만나는 시각화는 호가창이다.
```

전송 방식:

```text
SSE를 먼저 쓴다.
이유:
  - 서버 → 브라우저 단방향이라 호가창 push에 정확히 맞다.
  - WebSocket보다 코드가 짧고 디버깅이 쉽다.
  - 100ms 주기는 SSE로 충분히 처리된다.
양방향 통신이 정말 필요해지면 그때 WebSocket으로 바꾼다.
```

초기 UI:

```text
asks는 위쪽, 가격 내림차순으로 쌓는다.
bids는 아래쪽, 가격 내림차순으로 쌓는다.
가운데에서 best ask와 best bid가 만난다.
한 행에 표시:
  - price
  - quantity
  - 누적 quantity (해당 가격레벨까지의 quantity 합)
```

프론트 스택:

```text
Vite + React + TypeScript.
같은 레포 안에 frontend/ 디렉터리로 둔다.
```

완료 기준:

```text
React 페이지를 열면 호가창이 100ms 단위로 갱신된다.
새로고침해도 잠시 후 다시 채워진다.
```

이 단계에서 도입하지 않는 것:

```text
차트
주문 입력 폼
계좌 표시
인증
```

---

## 5단계. 호가창 기준 실시간 차트

목표:

```text
호가창에서 파생되는 best bid, best ask, mid price를 실시간 차트로 그린다.
```

계산:

```text
bestBid  = bids 중 가장 높은 price
bestAsk  = asks 중 가장 낮은 price
midPrice = (bestBid + bestAsk) / 2
spread   = bestAsk - bestBid
```

차트 추가 순서:

```text
1. bestBid, bestAsk 두 줄을 먼저 그린다. 호가창에 직접 보이는 값이므로
   별도 계산 없이 plot할 수 있다.
2. midPrice 한 줄을 추가한다. 파생값임을 명시한다.
3. spread를 보조 차트나 텍스트로 추가한다.
```

라이브러리 후보:

```text
TradingView Lightweight Charts.
```

완료 기준:

```text
차트가 100ms 단위로 새 포인트를 받는다.
bestBid 라인이 bestAsk 라인보다 항상 낮다.
같은 화면의 호가창과 차트가 같은 데이터에서 갈라져 나온 것이 눈으로 확인된다.
```

이 단계에서 도입하지 않는 것:

```text
캔들 차트
시간 단위 집계 (1m, 5m 등)
체결가(last price) 라인
```

---

## 6단계. 진짜 로컬 호가창 동기화

목표:

```text
3단계의 "partial depth 통째 교체" 방식을 버리고,
REST snapshot + diff depth stream으로 로컬 order book을 sequence 검증과 함께 유지한다.
```

처음 다루는 Binance 데이터:

```text
GET /fapi/v1/depth?symbol=BTCUSDT&limit=1000        (REST snapshot)
wss://fstream.binance.com/ws/btcusdt@depth@100ms    (diff depth stream)
각 이벤트의 U, u, pu 필드
```

동기화 알고리즘 (USDⓈ-M Futures 기준):

```text
1. diff stream 구독을 먼저 켜고, 이벤트를 큐에 쌓는다.
2. REST snapshot을 받는다. snapshot에는 lastUpdateId가 들어 있다.
3. 큐에 쌓인 이벤트 중 u < lastUpdateId 인 것은 모두 버린다.
4. 첫 유효 이벤트는 U <= lastUpdateId AND u >= lastUpdateId 여야 한다.
   조건을 못 맞추면 snapshot부터 다시 받는다.
5. 그 이후로는 매 이벤트의 pu가 직전 이벤트의 u와 같아야 한다.
   불일치 시 sequence가 끊긴 것이므로 snapshot부터 다시 받는다.
6. 각 이벤트의 b / a를 가격레벨별로 적용한다.
   quantity == 0 이면 해당 가격레벨을 제거한다.
```

완료 기준:

```text
sequence mismatch 발생 시 snapshot 재조회가 자동으로 일어난다 (로그로 확인).
GET /api/binance-futures/btcusdt/depth/latest 응답의 상위 20레벨이
Binance 웹사이트 호가창과 시각적으로 일치한다.
```

이 단계에서 도입하지 않는 것:

```text
주문 체결 (7단계)
정교한 재연결 전략 (단순 재구독으로 충분)
복수 심볼
```

---

## 7단계. 호가창 기준 모의 주문

목표:

```text
주문 체결을 last price가 아니라 현재 호가창으로 정의한다.
단일 계좌, 단일 심볼(BTCUSDT)만 지원한다.
```

체결 규칙:

```text
시장가 매수:  ask 쪽을 best ask부터 위로 먹는다.
시장가 매도:  bid 쪽을 best bid부터 아래로 먹는다.
지정가 매수:  limit price >= best ask 이면 즉시 체결, 아니면 대기.
지정가 매도:  limit price <= best bid 이면 즉시 체결, 아니면 대기.
```

MVP 가정:

```text
대기 중인 지정가 주문은 호가창이 업데이트될 때마다 재평가된다.
부분체결은 한 tick 안에서만 다룬다.
체결 후 호가창 자체에서 우리 주문량을 차감하지 않는다 (paper trading).
취소 기능은 지정가 대기가 정상 동작하는 것을 확인한 뒤 같은 단계 안에 추가한다.
```

완료 기준:

```text
시장가 BUY 0.01 → 체결 가격이 호출 시점 best ask 근처에서 잡힌다.
지정가 SELL @ (bestBid + delta) → 호가창이 그 가격을 칠 때 체결된다.
체결 결과가 화면과 백엔드 로그 양쪽에서 보인다.
```

이 단계에서 도입하지 않는 것:

```text
복수 심볼
복수 계좌
레버리지, 마진, 펀딩비
청산
```

---

## 8단계. 계좌, 포지션, PnL

목표:

```text
7단계의 체결 결과를 계좌와 포지션으로 누적한다.
```

계산 기준:

```text
진입 가격:   실제 체결 가격의 수량 가중 평균.
실현 PnL:    청산 체결 가격 - 평균 진입 가격.
미실현 PnL:  우선 mid price 기준으로 계산한다.
             mark price 도입은 운영성 단계 이후로 미룬다.
```

화면:

```text
계좌 잔고
열린 포지션 (수량, 평균 진입가, 미실현 PnL)
체결 내역
```

완료 기준:

```text
BUY → SELL 순서로 거래했을 때 손익이 즉시 계좌에 반영된다.
미실현 PnL이 mid price 변화에 따라 100ms 단위로 갱신된다.
```

이 단계에서 도입하지 않는 것:

```text
수수료
펀딩비
복수 심볼
복수 계좌
```

---

## 9단계. 운영성 보강

목표:

```text
호가창 / 차트 / 주문 / 계좌가 모두 동작한 뒤에
안정성과 운영 가시성을 추가한다.
```

추가하는 것:

```text
health 엔드포인트
설정 properties (stream URL, 심볼, 레벨 수 등을 외부 설정으로 분리)
WebSocket 연결 status (UP / DOWN / STALE)
재연결 (지수 백오프)
stale order book 감지 및 화면 표시
중복 connect 방지 (이 시점에 synchronized + Disposable 보관 도입)
에러 응답 표준화 (RFC 7807 problem detail 등)
```

원칙 재확인:

```text
운영성 코드는 처음부터 넣지 않는다.
실시간 호가창과 차트가 먼저 움직인 뒤에,
실제로 끊김/혼선이 보일 때 그 문제에 대응해 붙인다.
```

---

## 지금 당장 할 일

```text
[x] 0단계: Spring Boot 기동 확인.
[x] 1단계: btcusdt@depth20@100ms raw JSON을 로그로 확인.
[ ] 2단계: raw JSON 구조를 직접 본 뒤 OrderBookLevel / OrderBookSnapshot 정의,
          파싱 결과를 로그에 찍는다.
```

---

## 현재 로드맵에서 명시적으로 다루지 않는 것

```text
- 영속화 (DB): paper trading이 in-memory로 충분한지 9단계 이후 판단.
- 복수 심볼 (ETHUSDT 등): 7단계 안정화 후 도입할지 결정.
- 인증 / 멀티 계좌: 단일 계좌 흐름이 굳어진 뒤.
- 캔들 / 시간 봉 차트: 호가창 기반 학습 목표 달성 후 추가.
- 백테스트 / 시뮬레이션 시간 가속: 실시간 흐름이 굳어진 뒤.
```
