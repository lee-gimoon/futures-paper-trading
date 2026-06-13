# Futures Paper Trading

> Binance USDⓈ-M 선물의 실시간 호가창을 기준으로 주문을 체결하는 학습용 모의 선물거래 웹 애플리케이션

![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-6DB33F?logo=springboot&logoColor=white)
![Spring WebFlux](https://img.shields.io/badge/Spring-WebFlux%20%2B%20R2DBC-6DB33F?logo=spring&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-R2DBC-4169E1?logo=postgresql&logoColor=white)
![React](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white)

마지막 체결가(last price)가 아니라 **현재 호가창의 잔량을 레벨 단위로 소진하며** 체결하는 paper trading 엔진입니다.
Binance 선물 호가 스트림은 서버가 부팅 시 **1개 연결로만** 구독하고, 접속한 모든 브라우저에는 SSE로 fan-out 합니다.

<!-- TODO: 실행 화면 스크린샷 또는 GIF (호가창 + 차트가 움직이는 모습) -->

---

## 주요 기능

- **실시간 호가창** — Binance `btcusdt@depth20@100ms` WebSocket을 서버가 수신해 SSE로 push, 100ms 간격 갱신
- **캔들 차트** — 과거 봉은 Binance kline REST, 진행 봉은 호가 스트림의 best ask로 실시간 갱신 (TradingView Lightweight Charts)
- **회원가입 / 로그인** — 자체 회원가입, BCrypt 해시, 세션 + HttpOnly 쿠키 인증
- **모의 주문** — 시장가 / 지정가 BUY·SELL, 지정가 대기·취소, 호가 레벨 단위 부분 체결(주문 1 : 체결 N)
- **사용자별 격리** — 로그인한 사용자는 자신의 주문·체결만 조회·취소 가능

## 아키텍처

```mermaid
flowchart LR
    subgraph Binance
        BWS["WebSocket<br/>depth20@100ms"]
        BREST["kline REST"]
    end

    subgraph Backend["Spring Boot (WebFlux, :8080)"]
        ST["RawDepthStreamer<br/>부팅 시 1회 구독"]
        PA["SnapshotParser"]
        STORE["SnapshotStore<br/>AtomicReference + Sinks.replay(1)"]
        ENG["PaperTradingEngine<br/>호가 기준 체결"]
        MATCH["PendingOrderMatcher<br/>대기 지정가 재평가"]
        AUTH["Auth<br/>세션 + BCrypt"]
    end

    subgraph Browser["브라우저 (React, :5173)"]
        OB["호가창"]
        CH["캔들 차트"]
    end

    DB[("PostgreSQL<br/>users · paper_orders · paper_fills")]

    BWS -->|raw JSON| ST --> PA --> STORE
    STORE -->|"SSE /depth/stream"| OB
    STORE -->|best ask| CH
    BREST -->|"과거 봉 (직접 요청)"| CH
    STORE --> ENG
    STORE -->|새 스냅샷마다| MATCH --> ENG
    ENG --> DB
    AUTH --> DB
```

데이터 흐름의 핵심:

- Binance 수신은 `@PostConstruct`로 서버 부팅 시 1회 시작되고, 브라우저가 0명이어도 계속 돈다. 브라우저 SSE 구독은 `Sinks.asFlux()` 아래쪽에만 생기므로 **수신 생명주기와 구독 생명주기가 분리**되어 있다.
- `Sinks.many().replay(1)` 덕분에 늦게 접속한 브라우저도 최신 스냅샷 1건을 즉시 받는다.
- 대기 중인 지정가 주문은 새 호가 스냅샷이 들어올 때마다 재평가되어, 호가가 닿으면 체결된다.

## 체결 규칙

| 주문 | 규칙 |
|---|---|
| 시장가 BUY | best ask부터 위로 호가 레벨 잔량을 소진하며 체결 |
| 시장가 SELL | best bid부터 아래로 소진하며 체결 |
| 지정가 BUY | `limit ≥ best ask`면 즉시 체결, 아니면 OPEN 대기 |
| 지정가 SELL | `limit ≤ best bid`면 즉시 체결, 아니면 OPEN 대기 |

한 주문이 여러 호가 레벨에 걸치면 레벨마다 체결(fill)이 한 건씩 생긴다 → `paper_orders` 1건 : `paper_fills` N건.

현재 MVP 가정: 모의 주문은 실제 호가창에 영향을 주지 않으며, depth20 안에 보이는 수량만 유동성으로 간주한다. queue priority / maker·taker / 슬리피지 모델은 이후 단계에서 다룬다.

## 설계 결정

**왜 호가창 기준 체결인가** — last price 한 줄로 체결하는 모의투자와 달리, 실제 거래소처럼 주문 수량이 호가 잔량을 소진하며 평균 체결가가 결정된다. 호가창·차트·체결이 전부 같은 데이터 축(호가 스트림)을 공유한다.

**왜 가격·수량은 BigDecimal / NUMERIC(38,8)인가** — 돈 계산에 이진 부동소수점(double)을 쓰면 오차가 누적된다. 백엔드 전 구간 BigDecimal, DB는 NUMERIC으로 통일했다.

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
| Test | JUnit 5 (체결 엔진·호가 파생값 단위 테스트) |

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

### 1. 데이터베이스

```sql
CREATE DATABASE futures_paper_trading;
```

테이블은 서버 부팅 시 `schema.sql`이 자동 생성한다. 접속 정보는 환경변수로 덮어쓸 수 있다 (`DB_NAME` / `DB_USERNAME` / `DB_PASSWORD`, 기본값 `futures_paper_trading` / `postgres` / `postgres`).

### 2. 백엔드 (:8080)

```bash
./gradlew bootRun        # Windows: gradlew.bat bootRun
```

### 3. 프론트엔드 (:5173)

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
└── paper/     모의 주문 — 체결 엔진, 대기 지정가 매칭, 주문/체결 저장
frontend/      React 화면 — 호가창, 캔들 차트, 회원가입/로그인
docs/          학습 노트, 실행 흐름 다이어그램
roadmap.md     단계별 로드맵 (설계 배경과 결정 이유 포함)
```

## 진행 현황

- [x] Binance 선물 호가 WebSocket 수신 → 파싱 → 메모리 보관
- [x] Sinks 기반 SSE push, React 실시간 호가창
- [x] 캔들 차트 (과거 봉 kline REST + 진행 봉 실시간)
- [x] 자체 회원가입 / 로그인 (세션 + BCrypt)
- [x] 호가창 기준 모의 주문 (시장가·지정가·취소, 1:N 체결)
- [ ] 계좌·포지션·실현/미실현 PnL
- [ ] 운영성 보강 (재연결, stale 감지, health)
- [ ] REST snapshot + diff depth 기반 정밀 로컬 오더북

상세 계획은 [roadmap.md](roadmap.md) 참고.
