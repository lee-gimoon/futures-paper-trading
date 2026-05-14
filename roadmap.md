# 호가창 중심 실시간 모의 거래소 로드맵

작성일: 2026-05-14

이 프로젝트는 Binance USDⓈ-M Futures의 실시간 호가창을 기준으로 학습용 모의 선물 거래소를 만든다.

핵심 기준은 하나다.

```text
최신 체결가가 아니라 호가창을 먼저 본다.
호가창에서 best bid, best ask, mid price를 만들고,
그 값을 기준으로 화면, 차트, 주문 체결을 확장한다.
```

처음부터 health, properties, status, reconnect, 주문, 계좌를 만들지 않는다.
먼저 실제 Binance Futures 호가 데이터가 화면에서 움직이는 것을 확인한다.

---

## 0단계. Spring Boot 초기 실행

목표:

```text
Spring Boot 애플리케이션이 실행된다.
추가 API, 설정 클래스, health controller는 만들지 않는다.
```

완료 기준:

```text
./gradlew.bat test
./gradlew.bat bootRun
```

---

## 1단계. Binance Futures 호가 raw JSON 출력

목표:

```text
BTCUSDT USDⓈ-M Futures 호가 stream에 연결한다.
받은 JSON 문자열을 그대로 로그에 찍는다.
```

기본 후보 stream:

```text
btcusdt@depth20@100ms
```

이 stream은 상위 20개 bid/ask를 계속 보내준다.
초반에는 로컬 order book 동기화 알고리즘을 만들지 않고, 실제 호가 배열이 들어오는지만 본다.

확인할 공식 문서:

```text
Binance USDⓈ-M Futures WebSocket Market Streams
Partial Book Depth Streams
```

---

## 2단계. bids / asks 배열만 추출

목표:

```text
raw JSON에서 bids, asks 배열만 뽑는다.
각 가격 레벨은 price, quantity로 다룬다.
```

주의:

```text
가격과 수량은 double이 아니라 BigDecimal로 읽는다.
```

만들 최소 모델:

```text
OrderBookLevel
- price
- quantity

OrderBookSnapshot
- symbol
- eventTime
- bids
- asks
```

---

## 3단계. 현재 호가창을 메모리에 유지

목표:

```text
서버 안에 현재 BTCUSDT 상위 호가창을 들고 있는다.
```

메모리에 유지한다는 뜻:

```text
DB에 저장하는 것이 아니다.
서버가 실행 중인 동안 Java 객체가 최신 bids/asks를 들고 있다는 뜻이다.
```

초기 기준:

```text
Partial depth stream에서 받은 bids/asks 전체를 최신 화면용 snapshot으로 교체한다.
```

이 단계에서 아직 하지 않는 것:

```text
REST snapshot + diff depth sequence 검증
정밀한 로컬 order book 복구
재연결
stale 상태 관리
```

---

## 4단계. React에서 실시간 호가창 표시

목표:

```text
백엔드가 받은 bids/asks를 React 화면에 실시간으로 보여준다.
```

초기 전송 방식 후보:

```text
SSE
또는 WebSocket
```

처음 UI:

```text
asks는 위쪽
bids는 아래쪽
best ask와 best bid가 가운데에서 만나는 구조
price, quantity, 누적 quantity 표시
```

---

## 5단계. 호가창 기준 실시간 차트

목표:

```text
호가창에서 나온 best bid, best ask, mid price를 실시간 차트로 그린다.
```

계산 기준:

```text
bestBid = bids 중 가장 높은 price
bestAsk = asks 중 가장 낮은 price
midPrice = (bestBid + bestAsk) / 2
spread = bestAsk - bestBid
```

차트 기준:

```text
처음에는 mid price line chart
그 다음 best bid / best ask 두 줄
그 다음 spread 표시
```

차트 라이브러리 후보:

```text
TradingView Lightweight Charts
```

---

## 6단계. 진짜 로컬 호가창 동기화

목표:

```text
Partial depth 표시용 구조에서 벗어나,
REST order book snapshot + diff depth stream으로 로컬 order book을 정확히 유지한다.
```

필요한 Binance 데이터:

```text
REST order book snapshot
diff book depth stream
update id sequence
```

이 단계에서 처음 다루는 것:

```text
누락 이벤트 감지
sequence 불일치 시 snapshot 재조회
수량 0인 가격 레벨 제거
가격 레벨별 갱신
```

---

## 7단계. 호가창 기준 모의 주문

목표:

```text
주문 체결 기준을 last price가 아니라 현재 호가창으로 잡는다.
```

MVP 체결 규칙:

```text
시장가 매수: ask 쪽을 위에서부터 먹는다.
시장가 매도: bid 쪽을 위에서부터 먹는다.
지정가 매수: limit price 이하 ask를 먹거나 대기한다.
지정가 매도: limit price 이상 bid를 먹거나 대기한다.
```

처음에는 단일 계좌, 단일 심볼, BTCUSDT만 지원한다.

---

## 8단계. 계좌, 포지션, PnL

목표:

```text
호가창 체결 결과를 계좌와 포지션에 반영한다.
```

계산 기준:

```text
진입 가격은 실제 체결 가격 기준
미실현 PnL은 mid price 또는 mark price 중 하나를 선택해 명시
```

처음에는 수수료, 펀딩비, 레버리지를 단순화한다.

---

## 9단계. 운영성 보강

목표:

```text
호가창 기반 화면과 주문 흐름이 보인 뒤에 안정성을 추가한다.
```

이때 추가할 것:

```text
health API
설정 properties
연결 status
reconnect
stale order book 표시
에러 응답 정리
```

중요한 원칙:

```text
운영성 코드는 처음부터 넣지 않는다.
실시간 호가창과 차트가 먼저 움직인 뒤에 필요해진 만큼만 붙인다.
```

---

## 지금 당장 할 일

```text
1. 0단계로 Spring Boot 기본 실행을 확인한다.
2. 1단계에서 btcusdt@depth20@100ms raw JSON을 로그로 본다.
3. raw JSON 구조를 눈으로 확인한 다음 2단계 모델을 정한다.
```
