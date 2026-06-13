# Futures Paper Trading

> Binance USDⓈ-M 선물의 실시간 호가창을 기준으로 주문을 체결하고, 레버리지·마진·강제청산까지 다루는 학습용 모의 선물거래 웹 애플리케이션

![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-6DB33F?logo=springboot&logoColor=white)
![Spring WebFlux](https://img.shields.io/badge/Spring-WebFlux%20%2B%20R2DBC-6DB33F?logo=spring&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-R2DBC-4169E1?logo=postgresql&logoColor=white)
![React](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white)

마지막 체결가(last price) 한 줄로 체결하는 모의투자와 달리, **현재 호가창의 잔량을 레벨 단위로 소진하며** 평균 체결가를 만드는 paper trading 엔진입니다.
그 위에 선물 거래소의 핵심인 **레버리지·격리 마진·롱/숏 포지션·실현/미실현 PnL·강제청산**을 얹었습니다.
Binance 선물 호가 스트림은 서버가 부팅 시 **단 1개 연결로만** 구독하고, 접속한 모든 브라우저에는 SSE로 fan-out 합니다.

전 구간 **논블로킹 리액티브 스택**(WebFlux + Reactor + R2DBC)이며, 돈 계산은 전부 `BigDecimal` / `NUMERIC(38,8)`로 다룹니다.

![Futures Paper Trading 실행 화면](docs/images/hero.gif)

<p align="center"><sub>실시간 호가창 · 캔들 차트 · 레버리지/포지션/PnL 거래 패널 (BTCUSDT)</sub></p>

---

## 주요 기능

- **실시간 호가창** — Binance `btcusdt@depth20@100ms` WebSocket을 서버가 수신해 SSE로 push, 100ms 간격 갱신
- **캔들 차트** — 과거 봉은 Binance kline REST, 진행 봉은 호가 스트림의 best ask로 실시간 갱신 (TradingView Lightweight Charts)
- **회원가입 / 로그인** — 자체 회원가입, BCrypt 해시, 세션 + HttpOnly 쿠키 인증
- **호가창 기준 모의 주문** — 시장가 / 지정가 BUY·SELL, 지정가 대기·취소, 호가 레벨 단위 부분 체결(주문 1 : 체결 N)
- **레버리지·마진** — 1~125x 레버리지, 격리 마진 모델, 신규 주문 시 가용 증거금 검증
- **포지션·PnL** — 롱/숏 포지션, 포지션 뒤집기, 실현·미실현 PnL, equity, 가용잔고를 체결 내역에서 실시간 계산
- **자동 강제청산** — 현재 mark가 청산가에 닿은 포지션을 백그라운드에서 1초마다 검사해 시장가로 강제 청산
- **사용자별 격리** — 로그인한 사용자는 자신의 계좌·주문·체결·포지션만 조회·취소 가능

## 아키텍처

```mermaid
flowchart LR
    subgraph Binance
        BWS["WebSocket<br/>depth20@100ms"]
        BREST["kline REST"]
    end

    subgraph Backend["Spring Boot (WebFlux, :8080)"]
        ST["RawDepthStreamer<br/>부팅 시 1회 구독"]
        STORE["SnapshotStore<br/>AtomicReference + Sinks.replay(1)"]
        ENG["PaperTradingEngine<br/>호가 기준 체결"]
        MATCH["PendingOrderMatcher<br/>대기 지정가 재평가"]
        LIQ["LiquidationMonitor<br/>1s 청산 검사"]
        PORT["PortfolioService<br/>포지션·PnL·마진 계산"]
        AUTH["Auth<br/>세션 + BCrypt"]
    end

    subgraph Browser["브라우저 (React, :5173)"]
        OB["호가창"]
        CH["캔들 차트"]
        ACC["계좌·포지션·주문 패널"]
    end

    DB[("PostgreSQL<br/>users · paper_accounts<br/>paper_orders · paper_fills")]

    BWS -->|raw JSON| ST --> STORE
    STORE -->|"SSE /depth/stream"| OB
    STORE -->|best ask| CH
    BREST -->|"과거 봉 (직접 요청)"| CH
    STORE --> ENG
    STORE -->|새 스냅샷마다| MATCH --> ENG
    STORE -->|1초마다 샘플링| LIQ --> ENG
    ENG --> DB
    PORT --> DB
    PORT -->|"GET /account"| ACC
    AUTH --> DB
```

데이터 흐름의 핵심:

- Binance 수신은 `@PostConstruct`로 서버 부팅 시 1회 시작되고, 브라우저가 0명이어도 계속 돈다. 브라우저 SSE 구독은 `Sinks.asFlux()` 아래쪽에만 생기므로 **수신 생명주기와 구독 생명주기가 분리**되어 있다.
- `Sinks.many().replay(1)` 덕분에 늦게 접속한 브라우저도 최신 스냅샷 1건을 즉시 받는다.
- 대기 지정가 주문(`PendingOrderMatcher`)과 강제청산 검사(`LiquidationMonitor`)는 둘 다 같은 호가 스냅샷 스트림을 구독하는 **백그라운드 리액티브 소비자**다. 사용자의 HTTP 요청 없이 호가가 들어올 때마다(청산은 1초 샘플링) 자동으로 돈다.

## 거래 엔진

### 체결 규칙

| 주문 | 규칙 |
|---|---|
| 시장가 BUY | best ask부터 위로 호가 레벨 잔량을 소진하며 체결 |
| 시장가 SELL | best bid부터 아래로 소진하며 체결 |
| 지정가 BUY | `limit ≥ best ask`면 즉시 체결, 아니면 OPEN 대기 |
| 지정가 SELL | `limit ≤ best bid`면 즉시 체결, 아니면 OPEN 대기 |

한 주문이 여러 호가 레벨에 걸치면 레벨마다 체결(fill)이 한 건씩 생긴다 → `paper_orders` 1건 : `paper_fills` N건. 평균 체결가는 fill들의 수량가중평균이다.

### 계좌·포지션·마진 모델

| 항목 | 값 / 규칙 |
|---|---|
| 시드 자본 | 10,000 USDT (가입 후 첫 조회 시 1회 적립) |
| 레버리지 | 1 ~ 125x (기본 10x) |
| 마진 방식 | 격리(isolated) 마진, 유지증거금률(MMR) 0, 수수료 0 가정 (MVP) |
| 명목금액 | 평균진입가 × \|수량\| |
| 사용 증거금 | 명목금액 / 레버리지 |
| 청산가 | 롱 = 진입가 × (1 − 1/L), 숏 = 진입가 × (1 + 1/L) |
| 미실현 PnL | (현재 mid − 평균진입가) × 부호수량 |
| 가용잔고 | (현금 + 실현 PnL) − 사용 증거금 |

- **신규 주문 검증** — 새로 여는(또는 뒤집어 늘어나는) 수량의 필요 증거금이 가용잔고를 넘으면 거부(`400`). 순수 축소·청산 주문은 증거금이 들지 않아 항상 통과한다.
- **강제청산** — 현재 mark가 청산가에 닿으면 `LiquidationMonitor`가 반대 방향 시장가로 포지션을 강제 청산하고, 묶였던 증거금만큼 실현 손실이 확정된다.

현재 MVP 가정: 모의 주문은 실제 호가창에 영향을 주지 않으며, depth20 안에 보이는 수량만 유동성으로 간주한다. queue priority / maker·taker / 슬리피지 / 펀딩비 모델은 이후 단계에서 다룬다.

## 설계 결정

**왜 호가창 기준 체결인가** — last price 한 줄로 체결하는 모의투자와 달리, 실제 거래소처럼 주문 수량이 호가 잔량을 소진하며 평균 체결가가 결정된다. 호가창·차트·체결·PnL이 전부 같은 데이터 축(호가 스트림)을 공유한다.

**왜 포지션·PnL을 저장하지 않고 매번 계산하는가** — `paper_accounts`에는 시드 현금과 레버리지만 저장하고, 포지션·실현/미실현 PnL은 체결 내역(`paper_fills`)을 시간순으로 재생해 매번 다시 계산한다(`PositionCalculator`). 상태를 따로 쌓아두지 않으니 체결과 포지션이 어긋날 여지가 없고, 계산 로직은 부수효과 없는 순수 함수로 남는다.

**왜 레버리지를 두 종류로 나눴나** — `계좌 레버리지`(버튼으로 바꾸는 신규 주문용 값)와 `포지션 레버리지`(이미 연 포지션이 진입 시점에 고정한 값)를 구분했다. 격리 마진에서는 포지션을 연 뒤 레버리지 버튼을 눌러도 그 포지션의 증거금·청산가가 흔들리면 안 되기 때문이다. 포지션 레버리지는 `paper_orders.leverage`를 체결 순서대로 되짚어 복원한다.

**왜 가격·수량은 BigDecimal / NUMERIC(38,8)인가** — 돈 계산에 이진 부동소수점(double)을 쓰면 오차가 누적된다. 백엔드 전 구간 BigDecimal, DB는 NUMERIC으로 통일했다.

**왜 핵심 계산을 순수 함수(도메인 계산기)로 뽑았나** — `PaperTradingEngine`(체결)·`PositionCalculator`(포지션/실현 PnL)·`MarginCalculator`(증거금/청산가)는 DB·웹을 일절 모르고 입력만 보고 답을 낸다. 덕분에 주문·체결만 만들어 넣고 결과를 확인하는 단위 테스트가 쉽고, 금융 로직의 정확성을 테스트로 못 박을 수 있다.

**왜 차트의 과거 봉은 프론트가 Binance에 직접 요청하는가** — kline은 API 키가 필요 없는 공개 시세다. 백엔드가 중계하면 사용자 수에 비례해 서버 부담이 늘지만, 브라우저가 직접 받으면 서버 부담은 0이다. 체결·PnL에 쓰는 가격은 이미 백엔드 호가 스트림이 갖고 있어 차트용 kline은 순수 표시용이다.

**왜 진행 봉은 kline WebSocket이 아닌 호가 스트림으로 갱신하는가** — 개발 환경에서 Binance 선물의 체결 계열 push 스트림(`@kline`·`@aggTrade`·`@markPrice`)이 연결은 되지만 메시지 0건으로 차단되는 것을 직접 측정으로 확인했다(호가 계열 `@depth`·`@bookTicker`와 REST는 정상). 이미 받고 있는 호가 SSE의 best ask로 진행 봉을 묶어, 추가 연결 없이 호가창과 가격이 일치하는 차트를 만들었다.

**왜 세션 + HttpOnly 쿠키인가** — 단일 백엔드 + 브라우저 클라이언트 구성에서는 세션이 가장 단순하고, HttpOnly 쿠키는 XSS로 토큰이 탈취되는 면을 줄인다. JWT는 모바일 앱·외부 API 공개 같은 필요가 생길 때 도입한다.

자세한 학습 노트와 다이어그램은 [docs/](docs/)에 있다.

## 기술 스택

| 구분 | 스택 |
|---|---|
| Backend | Java 21, Spring Boot 4.0.6, Spring WebFlux (Reactor), Spring Security (reactive), R2DBC |
| Database | PostgreSQL |
| Frontend | React 18, TypeScript, Vite, TradingView Lightweight Charts v5 |
| Test | JUnit 5 — 체결 엔진·포지션/PnL·마진/청산가·호가 파생값 단위 테스트 |

## 시작하기

### Docker로 실행하기

Docker Desktop을 설치하고 실행한 상태에서 프로젝트 루트 폴더에서 아래 명령어를 실행한다.
별도의 Java, Node.js, PostgreSQL 설치는 필요 없다.

```bash
docker compose up --build
```

실행 후 브라우저에서 `http://localhost:8080`으로 접속한다.
React 빌드 파일은 Spring Boot가 함께 서빙하고, PostgreSQL도 Docker Compose가 같이 실행한다.

종료하려면 터미널에서 `Ctrl + C`를 누른 뒤 아래 명령어를 실행한다.

```bash
docker compose down
```

DB 데이터까지 초기화하고 다시 시작하려면:

```bash
docker compose down -v
docker compose up --build
```

### 로컬 개발용 실행

프론트/백엔드를 따로 띄우며 개발할 때 사용한다.

사전 준비:

- Java 21
- Node.js 18+
- PostgreSQL (localhost:5432)

#### 1. 데이터베이스

```sql
CREATE DATABASE futures_paper_trading;
```

테이블은 서버 부팅 시 `schema.sql`이 자동 생성한다. 접속 정보는 환경변수로 덮어쓸 수 있다 (`DB_NAME` / `DB_USERNAME` / `DB_PASSWORD`, 기본값 `futures_paper_trading` / `postgres` / `postgres`).

#### 2. 백엔드 (:8080)

```bash
./gradlew bootRun        # Windows: gradlew.bat bootRun
```

#### 3. 프론트엔드 (:5173)

```bash
cd frontend
npm install
npm run dev
```

`http://localhost:5173` 접속. `/api` 요청은 Vite 프록시가 8080으로 전달한다.

### 테스트

```bash
./gradlew test
```

## API

| Method | Path | 인증 | 설명 |
|---|---|---|---|
| GET | `/api/binance-futures/btcusdt/depth/latest` | – | 최신 호가 스냅샷 1건 |
| GET | `/api/binance-futures/btcusdt/depth/stream` | – | 호가 SSE 스트림 |
| POST | `/api/auth/signup` | – | 회원가입 |
| POST | `/api/auth/login` | – | 로그인 (세션 발급) |
| POST | `/api/auth/logout` | 세션 | 로그아웃 |
| GET | `/api/auth/me` | 세션 | 내 정보 조회 |
| POST | `/api/paper/orders` | 세션 | 주문 생성 (시장가/지정가) |
| GET | `/api/paper/orders` | 세션 | 내 주문 목록 |
| DELETE | `/api/paper/orders/{id}` | 세션 | 대기 지정가 주문 취소 |
| GET | `/api/paper/account` | 세션 | 내 계좌 (잔고·실현/미실현 PnL·equity·마진·포지션) |
| GET | `/api/paper/fills` | 세션 | 내 체결 내역 |
| PUT | `/api/paper/account/leverage` | 세션 | 레버리지 변경 (1~125x) |

주문 생성 예시:

```bash
curl -X POST http://localhost:8080/api/paper/orders \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{"symbol":"BTCUSDT","side":"BUY","type":"MARKET","quantity":"0.01"}'
```

## 프로젝트 구조

```text
src/main/java/com/example/futurespapertrading/
├── market/    호가 수신 파이프라인 — Binance WebSocket 수신, 파싱, 보관, SSE 노출
├── auth/      회원가입·로그인 — Spring Security(reactive), 세션 + BCrypt
└── paper/     모의 거래 — 체결 엔진, 대기 지정가 매칭, 포지션/PnL·마진 계산, 강제청산
    ├── domain/    순수 도메인 계산기 (PaperTradingEngine · PositionCalculator · MarginCalculator) + 엔티티
    ├── service/   주문 처리 · 포트폴리오 · 강제청산 · 대기 주문 매칭
    ├── controller/ 주문 · 계좌 HTTP 입구
    └── dto/       요청/응답 경계 객체
frontend/      React 화면 — 호가창, 캔들 차트, 거래 패널(주문·계좌·포지션), 회원가입/로그인
docs/          학습 노트, 실행 흐름 다이어그램
roadmap.md     단계별 로드맵 (설계 배경과 결정 이유 포함)
```

## 진행 현황

- [x] Binance 선물 호가 WebSocket 수신 → 파싱 → 메모리 보관
- [x] Sinks 기반 SSE push, React 실시간 호가창
- [x] 캔들 차트 (과거 봉 kline REST + 진행 봉 실시간)
- [x] 자체 회원가입 / 로그인 (세션 + BCrypt)
- [x] 호가창 기준 모의 주문 (시장가·지정가·취소, 1:N 체결)
- [x] 계좌·포지션·실현/미실현 PnL (체결 내역에서 재계산)
- [x] 레버리지·격리 마진·증거금 검증·자동 강제청산
- [ ] 운영성 보강 (재연결, stale 감지, health)
- [ ] REST snapshot + diff depth 기반 정밀 로컬 오더북
- [ ] 수수료·펀딩비·maker/taker 모델

상세 계획은 [roadmap.md](roadmap.md) 참고.
