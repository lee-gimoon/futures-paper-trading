# AGENTS.md

이 파일은 Codex가 `futures-paper-trading` 프로젝트에서 작업할 때 따르는 전역 가이드다.

## 프로젝트 기준

`futures-paper-trading`은 Binance USDⓈ-M Futures 실시간 시세를 기반으로 학습용 모의 선물 거래소 백엔드를 만드는 프로젝트다.

기본 기준:

```text
기본 작업 위치: C:\JavaSpring\futures-paper-trading
참고 위치: C:\JavaSpring\concurrency
진행 방식: 단계별 md -> 세부 구현 -> 테스트 -> 문서 갱신
전체 로드맵: docs/implementation-roadmap.md
단계별 문서: docs/steps
노션 매핑: docs/notion-page-map.md
```

실제 파일 생성과 수정은 기본적으로 `C:\JavaSpring\futures-paper-trading` 안에서 수행한다.  
`C:\JavaSpring\concurrency`는 학습 참고용으로만 사용하며, 코드를 그대로 복사하기보다 필요한 개념과 패턴을 현재 프로젝트 구조에 맞게 적용한다.

## 작업 흐름

큰 기능을 한 번에 구현하지 않는다. 각 단계는 다시 작은 세부 작업으로 쪼개서 진행한다.

기본 순서:

```text
1. 현재 단계 md 파일을 확인하거나 먼저 만든다.
2. 이번 세부 작업의 목적과 범위를 문서에 적는다.
3. 필요한 패키지, 클래스, API만 구현한다.
4. 작은 테스트 또는 수동 확인 방법을 만든다.
5. 테스트하거나 동작을 확인한다.
6. 문서의 체크리스트와 남은 일을 갱신한다.
```

한 번에 여러 단계를 앞질러 구현하지 않는다.

예시:

- 0단계에서는 WebSocket 코드를 만들지 않는다.
- 1단계에서는 주문, 계좌, 포지션을 만들지 않는다.
- 2단계에서는 체결 엔진을 만들지 않는다.
- 주문 API를 만들기 전에 시세 저장소가 먼저 동작해야 한다.
- 체결 엔진을 만들기 전에 주문 모델과 계좌 모델이 먼저 준비되어야 한다.

## Notion 동기화 규칙

로컬 md 문서를 Notion에 동기화할 때는 `docs/notion-page-map.md`를 먼저 확인한다.

기본 순서:

```text
1. 동기화할 로컬 md 파일 경로를 정한다.
2. docs/notion-page-map.md에서 해당 md 파일의 Notion page id 또는 URL을 찾는다.
3. 매핑이 있으면 Notion 검색 없이 해당 page id 또는 URL로 바로 fetch/update 한다.
4. 매핑이 없을 때만 Notion에서 페이지를 검색하거나 새로 만든다.
5. 새 연결 관계가 생기면 docs/notion-page-map.md에 즉시 추가한다.
```

로컬 md 파일을 원본으로 보고, Notion 페이지는 동기화 대상 문서로 다룬다.

## 단계별 문서 규칙

단계별 문서는 `docs/steps` 아래에 만든다.

파일명 형식:

```text
00-project-skeleton.md
01-binance-market-data.md
02-market-price-store.md
```

각 단계 문서에는 아래 내용을 포함한다.

```text
1. 이 단계의 목표
2. 이 단계에서 만들지 않는 것
3. 이 단계에서 사용하는 기술과 스택
4. concurrency 프로젝트에서 연결되는 Step
5. 이 단계 한정 아키텍처
6. 만들 패키지, 클래스, API
7. 세부 구현 체크리스트
8. 테스트 또는 수동 확인 방법
9. 다음 단계로 넘어가는 기준
```

## concurrency 참고 규칙

작업 중 concurrency 학습 내용이 필요하면 사용자가 매번 지시하지 않아도 `C:\JavaSpring\concurrency`에서 관련 Step 문서와 예제 코드를 찾아 참고한다.

단계별 문서의 concurrency 섹션에는 아래 내용을 적는다.

```text
1. 이 단계에서 사용하는 concurrency Step
2. 해당 Step을 쓰는 이유
3. 이 프로젝트 코드에서 연결되는 부분
4. 이 단계에서는 아직 사용하지 않는 concurrency Step
```

대표 매핑:

- 0단계 프로젝트 뼈대: Step01
- Binance WebSocket 연결: Step06, Step07, Step09
- 시세 저장소: Step03
- 주문 명령 큐와 체결 엔진: Step02, Step03, 필요 시 Step08
- 외부 호출 병렬화: Step04
- 장애 대응: Step09

## 구현 원칙

- 외부 I/O는 `WebClient`, Reactor `Mono`/`Flux`를 우선 고려한다.
- 계좌, 주문, 포지션처럼 정합성이 중요한 상태는 여러 스레드가 동시에 수정하지 않게 설계한다.
- 주문 체결 엔진은 단일 명령 처리 흐름을 우선 사용한다.
- 공유 조회 상태는 `ConcurrentHashMap`, `AtomicReference` 같은 안전한 구조를 사용한다.
- 장애 대응은 timeout, retry, reconnect, fallback을 단계적으로 추가한다.
- 처음에는 DB 없이 인메모리로 구현하고, 저장이 필요한 단계에서 JPA/H2/PostgreSQL을 추가한다.

## 프론트엔드 기준

- 프론트엔드는 React + TypeScript + Vite를 기본 후보로 둔다.
- 실시간 시세 차트는 TradingView Lightweight Charts를 기본 후보로 둔다.
- 프론트엔드 코드는 별도 단계 문서를 만든 뒤 구현한다.
- 백엔드 API가 준비되지 않은 기능 화면을 먼저 크게 만들지 않는다.
- 처음 프론트엔드는 health API와 시세 조회/스트리밍 API가 준비된 뒤 붙인다.
- 차트 라이브러리 사용 전 공식 문서와 라이선스, TradingView 표기 요구사항을 확인하고 단계 문서에 기록한다.
- 실제 거래소처럼 보이더라도 실제 Binance 주문 API는 절대 호출하지 않는다.

## 거래 시스템 주의사항

- 이 프로젝트는 학습용 모의 거래소다.
- 실제 Binance 주문 API를 호출하지 않는다.
- 실제 API key, secret key, private key를 코드나 문서에 저장하지 않는다.
- WebSocket은 공개 시세 데이터 수신 용도로만 사용한다.
- 사용자 데이터나 private stream이 필요해지면 별도 단계 문서를 먼저 만들고 위험 요소를 정리한다.

## 금액과 시간 처리

- 가격, 수량, 잔고, 증거금, PnL에는 `double`과 `float`를 사용하지 않는다.
- 금액 계산에는 `BigDecimal`을 사용한다.
- 반올림, 소수점 자리수, 최소 주문 수량 같은 규칙은 별도 유틸이나 정책 클래스로 분리한다.
- 시간 값은 가능하면 `Instant`를 사용한다.
- 테스트가 필요한 시간 로직은 `Clock` 또는 `TimeProvider`로 분리한다.

## 외부 문서 확인

- Binance endpoint, stream 이름, payload 구조는 변경될 수 있으므로 구현 전에 공식 Binance Developers 문서를 확인한다.
- 오래된 블로그나 예제 코드보다 공식 문서를 우선한다.
- 확인한 WebSocket URL과 stream 규칙은 해당 단계 md 파일에 기록한다.

## 테스트와 완료 기준

기본 확인 명령:

```text
./gradlew.bat test
```

테스트 원칙:

- 각 단계는 가능한 한 작은 테스트를 함께 추가한다.
- WebSocket처럼 외부 네트워크가 필요한 기능은 단위 테스트와 수동 확인 방법을 분리한다.
- 외부 Binance 연결 실패가 테스트 전체 실패로 이어지지 않게 설계한다.

각 단계는 아래 조건을 만족해야 완료로 본다.

```text
1. 단계 md 파일이 있다.
2. 해당 단계에서 만든 코드가 문서의 아키텍처와 크게 어긋나지 않는다.
3. 테스트 또는 수동 확인 방법이 문서에 적혀 있다.
4. 테스트 또는 수동 확인을 수행했다.
5. 실행 가능한 상태로 남겼다.
6. 다음 단계에서 이어받을 남은 일을 문서에 적었다.
```
