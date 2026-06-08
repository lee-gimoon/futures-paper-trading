# paper 폴더 — 호가창 기준 모의 주문(Paper Trading) 모듈

이 폴더는 **"로그인한 사용자가 낸 모의 주문을, 실시간 호가창 기준으로 체결한다"를 책임지는 곳**입니다.
실제 돈이 아니라 가짜(paper) 계좌로, Binance 실시간 호가(depth)를 기준값 삼아
시장가/지정가 매수·매도를 체결하고 그 기록을 DB에 남깁니다. (로드맵 **8단계**)

`auth` 폴더가 "누가 사용자인가"를 끝내줬기 때문에, 이 폴더는 **"이미 로그인된 사용자"**라고
가정하고 "그 사용자의 주문"만 다룹니다.

---

## 핵심 개념: 주문(Order) ≠ 체결(Fill)

이 폴더에서 가장 먼저 잡아야 할 구분입니다.

| | 주문 `PaperOrder` | 체결 `PaperFill` |
|---|---|---|
| 무엇 | "이만큼 사고/팔고 싶다"는 **의도** | 그 의도가 실제로 거래된 **사건** |
| 개수 | 1건 | **0 ~ N건** |
| 종류 구분 | `type`(MARKET/LIMIT) 있음 | 없음 (시장가·지정가 **공통**) |

```
PaperOrder (주문 1건)
   │  1
   │        한 주문이 여러 호가 레벨에 걸쳐 체결되면
   │  N     레벨마다 fill이 한 건씩 생긴다 → 1 : N
PaperFill  (체결 N건)
```

> 예) 시장가 10개 매수인데 best 매도가 3개뿐 → 다음 호가까지 긁어 `3+2+5`로 쪼개져 체결 → fill 3건.
> 단, 0.01 같은 작은 수량은 best 한 줄에서 다 차서 보통 fill 1건.

**왜 쪼개지나? = 체결 가격이 달라서.** `PaperFill`은 `price` 필드를 딱 하나만 갖습니다
(한 레코드 = 한 가격). 그래서 한 PaperFill에 여러 가격을 담을 수 없고, 체결 가격이 바뀔 때마다
레코드를 새로 끊습니다. 호가창 유동성은 가격마다 한정돼 있어, 한 레벨(best ask 3개)을 다 먹으면
더 비싼 다음 가격으로 넘어가야 하기 때문입니다.

```
시장가 10개 매수
  100.0 에서 3개 → PaperFill(price=100.0, qty=3)   ← 가격 다름 → 별도 레코드
  100.1 에서 2개 → PaperFill(price=100.1, qty=2)   ← 가격 다름 → 별도 레코드
  100.2 에서 5개 → PaperFill(price=100.2, qty=5)   ← 가격 다름 → 별도 레코드
```

> 이 쪼개진 가격들은 9단계에서 **평균 진입가**(수량가중평균)를 계산할 때 그대로 쓰입니다:
> `(100.0×3 + 100.1×2 + 100.2×5) / 10`.

---

## 파일별 역할

| 파일 | 종류/계층 | 역할 |
|---|---|---|
| [`OrderSide`](src/main/java/com/example/futurespapertrading/paper/OrderSide.java) | enum | 주문 방향 — `BUY` / `SELL` |
| [`OrderType`](src/main/java/com/example/futurespapertrading/paper/OrderType.java) | enum | 주문 종류 — `MARKET`(시장가) / `LIMIT`(지정가) |
| [`OrderStatus`](src/main/java/com/example/futurespapertrading/paper/OrderStatus.java) | enum | 주문 생애주기 — `NEW`/`OPEN`/`FILLED`/`CANCELED`/`REJECTED` |
| [`PaperOrder`](src/main/java/com/example/futurespapertrading/paper/PaperOrder.java) | 엔티티(record) | `paper_orders` 한 줄 ↔ "주문 한 건"(의도) |
| [`PaperFill`](src/main/java/com/example/futurespapertrading/paper/PaperFill.java) | 엔티티(record) | `paper_fills` 한 줄 ↔ "체결 한 건"(실제 거래) |
| [`PaperOrderRepository`](src/main/java/com/example/futurespapertrading/paper/PaperOrderRepository.java) | DB 접근 | `paper_orders` 조회/저장 — `findByUserId`, `findByStatus` |
| [`PaperFillRepository`](src/main/java/com/example/futurespapertrading/paper/PaperFillRepository.java) | DB 접근 | `paper_fills` 조회/저장 — `findByOrderId` |
| [`PaperTradingEngine`](src/main/java/com/example/futurespapertrading/paper/PaperTradingEngine.java) | 체결 엔진(순수 함수) | `tryFill(order, snapshot)` → 호가를 가격순으로 긁어 `PaperFill` 목록(1:N) 생성 |
| [`PaperOrderController`](src/main/java/com/example/futurespapertrading/paper/PaperOrderController.java) | HTTP 입구(컨트롤러) | `POST /api/paper/orders` — 시장가 주문 접수→체결→저장→요약 응답 (C단계) |
| [`dto/CreateOrderRequest`](src/main/java/com/example/futurespapertrading/paper/dto/CreateOrderRequest.java) | DTO(요청) | 주문 생성 요청 본문 + 검증(`quantity>0`, `side`/`type` 형식) |
| [`dto/OrderResponse`](src/main/java/com/example/futurespapertrading/paper/dto/OrderResponse.java) | DTO(응답) | 주문 결과 요약(상태·체결수량·`avgPrice`). 개별 fill 목록은 미포함 |
| [`BUILD-ORDER.md`](src/main/java/com/example/futurespapertrading/paper/BUILD-ORDER.md) | 문서 | 8단계 내부 구현 순서(A~H)와 단계별 검증 방법 |

> 엔진이 쓰는 best 가격 헬퍼 [`OrderBookQuotes`](src/main/java/com/example/futurespapertrading/market/OrderBookQuotes.java)(bestBid/bestAsk)는
> 호가 도메인이라 `paper`가 아니라 **`market` 폴더**에 있습니다(프론트 `quote.ts`의 백엔드 미러).

### 세 enum이 하는 일

세 개 다 "정해진 값만 허용"해서 `"buy"`, `"markett"` 같은 오타를 **컴파일 단계에서** 막습니다.
단, DB에는 enum이 아니라 **String**으로 저장합니다 (입구/출구에서만 `valueOf()`↔`name()`으로 변환).
이유는 [`OrderSide`](src/main/java/com/example/futurespapertrading/paper/OrderSide.java) 상단 주석 참고.

### 두 엔티티가 하는 일

[`User`](src/main/java/com/example/futurespapertrading/auth/User.java)와 똑같은 패턴(`record` + `@Table` + `@Id` + `@Column`)의
**영속성 전용 그릇**입니다. 외부 응답에는 이걸 그대로 안 쓰고 DTO([`dto/OrderResponse`](src/main/java/com/example/futurespapertrading/paper/dto/OrderResponse.java))를 따로 둡니다(C단계 완료).
가격·수량은 1원 오차도 손익이 되므로 `double`이 아닌 **`BigDecimal`**(DB는 `NUMERIC`).

**왜 엔티티가 `record`인가? (JPA였다면 가변 class였을 것)**
이 프로젝트는 JPA가 아니라 **Spring Data R2DBC**를 씁니다(import가 `jakarta.persistence`가 아니라
`org.springframework.data.relational`). R2DBC는 리액티브라 dirty checking·지연 로딩·프록시 같은
"숨은 마법"이 없고, 객체를 **생성자로 통째 조립**해 불러옵니다 → 불변(immutable) `record`와 궁합이 딱 맞음.
반대로 JPA/Hibernate는 dirty checking·프록시 때문에 **가변 객체가 필수**라 엔티티를 `record`로 못 만들고
가변 `class`로 짭니다. 즉 "record는 DTO용"은 JPA 기준 통념이고, **R2DBC에선 record 엔티티가 정석**입니다.
(단, record는 불변이라 저장 시 생성된 id는 `save()`의 **반환 객체**에 담겨 옵니다.)

### 두 리포지토리가 하는 일

[`UserRepository`](src/main/java/com/example/futurespapertrading/auth/UserRepository.java)와 같은 방식 —
**인터페이스만 선언**하면 Spring Data가 부팅 시 구현체를 자동 생성합니다.
메서드 이름이 곧 쿼리(`findByUserId` → `WHERE user_id = ?`)이고,
결과가 여럿일 수 있어 반환은 전부 **`Flux`**(0개 이상)입니다.

---

## 현재 상태 — C단계(시장가 주문 API)까지 완료

**그릇(A) + 체결 로직(B) + 시장가 주문 입구(C)**까지 됐습니다. 이제 밖에서 시장가 주문을 넣어 체결·저장할 수 있습니다.

- **A단계(영속성 토대)** ✅ — enum·엔티티·리포지토리(주문/체결을 담고 꺼낼 통로)
- **B단계(순수 체결 엔진)** ✅ — `PaperTradingEngine.tryFill` + `OrderBookQuotes`, 단위 테스트 통과
  (DB·웹 없이 "주문+호가 → fill 목록"만 계산하는 순수 함수)
- **C단계(시장가 주문 POST)** ✅ — `PaperOrderController` `POST /api/paper/orders`
  (현재 유저 확인 → 최신 호가[없으면 503] → `tryFill` → 상태 판정[FILLED/REJECTED] → 주문+fill 저장 → 요약 응답)
  · 시장가만 지원(지정가는 E단계). 부분체결도 FILLED. 비로그인은 401(SecurityConfig가 자동 차단).

앞으로 추가될 파일/작업(예정):

| 추가 예정 | 계층 | 단계 |
|---|---|---|
| `PaperOrderController`에 `GET`(내 주문 목록) 추가 | HTTP 입구 | D |
| `PaperOrderController`에 `LIMIT` 분기(즉시 크로싱 + OPEN 등록) 추가 | HTTP 입구 | E |
| `PaperOrderController`에 `DELETE`(주문 취소) 추가 | HTTP 입구 | F |
| `PaperExceptionHandler` | 예외 → HTTP 상태코드 변환 | F |
| `PendingOrderMatcher` | 대기 지정가 자동 체결(백그라운드) | G |

> 자세한 순서·검증 방법·완료 기준 매핑은 [BUILD-ORDER.md](src/main/java/com/example/futurespapertrading/paper/BUILD-ORDER.md)에 있습니다.

---

## 체결 규칙 한눈에 (로드맵 8단계 MVP)

```
시장가 매수 → best ask부터 위로 호가를 긁어 체결 (여러 레벨 → fill N건 가능)
시장가 매도 → best bid부터 아래로 긁어 체결
지정가 매수 → limit ≥ best ask 이면 즉시 체결, 아니면 OPEN으로 대기
지정가 매도 → limit ≤ best bid 이면 즉시 체결, 아니면 OPEN으로 대기
대기(OPEN)  → 호가 snapshot이 들어올 때마다 재평가, 닿으면 FILLED
```

MVP라서 미루는 것: 우리 주문이 호가에 영향 주기, 체결 후 호가 수량 차감,
queue priority, maker/taker, 슬리피지 모델 → 나중 단계.

---

## 한 줄 요약

> **"로그인된 사용자의 주문(PaperOrder)을 실시간 호가로 체결해 그 기록(PaperFill)을 남긴다"** —
> 이 흐름을 위한 그릇(enum·엔티티·리포지토리)이 지금 단계의 paper 폴더이고,
> 체결 엔진·컨트롤러는 [BUILD-ORDER.md](src/main/java/com/example/futurespapertrading/paper/BUILD-ORDER.md) 순서대로 채워 나갑니다.
