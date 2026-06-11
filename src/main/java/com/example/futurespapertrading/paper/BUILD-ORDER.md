8단계 안에서 다시 더 잘게 쪼갠 구현 순서입니다. 각 단계는 **독립적으로 검증 가능**하고, 앞 검증의 정정 사항(`sample()` 제거, 빈 카운터 가드, 중복 체결 방지)을 반영했습니다.

## A. 영속성 토대 (동작 없음, 그릇만) ✅ 완료
- **A-1** [schema.sql](src/main/resources/schema.sql)에 `paper_orders` + `paper_fills` 두 테이블 추가
- **A-2** enum 3개: `OrderSide`(BUY/SELL), `OrderType`(MARKET/LIMIT), `OrderStatus`(NEW/OPEN/FILLED/CANCELED/REJECTED)
- **A-3** `PaperOrder`, `PaperFill` record 엔티티 ([User](src/main/java/com/example/futurespapertrading/auth/domain/User.java) 미러)
- **A-4** `PaperOrderRepository`, `PaperFillRepository` (`findByUserIdOrderByIdDesc`, `findByStatus`)
- **확인**: 앱이 뜨고 psql `\d`로 두 테이블 생성 확인. 끝.

## B. 순수 체결 엔진 + 단위 테스트 ★ (로드맵이 "주문 단계부터 테스트"라 한 지점) ✅ 완료
- **B-1** bestBid/bestAsk 헬퍼 (백엔드 BigDecimal — 프론트 [quote.ts](frontend/src/quote.ts) 미러, 정렬 가정 안 함)
      → [OrderBookQuotes](src/main/java/com/example/futurespapertrading/market/domain/OrderBookQuotes.java) (market 폴더)
- **B-2** `PaperTradingEngine.tryFill(order, snapshot)` 순수 함수 → fill 목록 또는 empty
      → [PaperTradingEngine](src/main/java/com/example/futurespapertrading/paper/domain/PaperTradingEngine.java). 호가를 가격순으로 긁어 레벨마다 fill 1건(1:N)
- **B-3** 단위 테스트: 시장가 BUY/SELL 체결가, 지정가 크로싱/미크로싱, **딱 닿는 경계**(`≤/≥`)
      → [PaperTradingEngineTest](src/test/java/com/example/futurespapertrading/paper/domain/PaperTradingEngineTest.java)(10) + [OrderBookQuotesTest](src/test/java/com/example/futurespapertrading/market/domain/OrderBookQuotesTest.java)(3)
- **확인**: ✅ `./gradlew test --tests "*PaperTradingEngineTest" --tests "*OrderBookQuotesTest"` 통과.
      DB·웹 없이 로직만 (`@SpringBootTest` 안 거침). (기준 2·3·4·5의 *로직*이 여기서 굳음)

## C. 시장가 주문 — POST (즉시 체결만) ✅ 구현 + curl 검증 완료
- **C-1** ✅ dto `CreateOrderRequest` / `OrderResponse`
- **C-2** ✅ `PaperOrderController` `POST /api/paper/orders` — 현재 user_id 확인 → `latest()` → `tryFill` → 주문+fill 저장 → 응답. **MARKET만**
      (시장가 부분체결도 FILLED, 0건이면 REJECTED. fill의 order_id는 주문 저장 후 받은 id로 채워 저장)
- **C-3** ✅ `@Valid`(quantity>0, side/type 형식), 호가 없을 때 503. 지정가(LIMIT)는 400으로 거부(E단계까지).
- **확인(curl)**: 로그인 쿠키로 시장가 BUY 0.01 → FILLED + best ask 가격. **비로그인 → 401**. (기준 1·2·3 달성)
      → ✅ 검증 완료(2026-06-08): Postgres18 + Binance depth 스트림 + 앱을 띄워 curl로 확인.
        비로그인 401 / BUY→FILLED@best ask / SELL→FILLED@best bid(1틱 차) / LIMIT·검증위반 → 400 /
        DB에 paper_orders(FILLED, user_id 격리) + paper_fills(order_id 연결) 저장 확인.
        (호가 없을 때 503만 스트림이 살아있어 런타임 미재현 — 코드 경로는 확인.)

## D. 내 주문 목록 — GET ✅ 구현 + curl 검증 완료
- **D-1** ✅ `GET /api/paper/orders` → `findByUserIdOrderByIdDesc(현재 유저)` (최신순). 가벼운 목록이라 avgPrice는 생략(null) — fill 조회 없이 주문 행만 반환.
- **변경 파일**:
  - [`PaperOrderController`](src/main/java/com/example/futurespapertrading/paper/controller/PaperOrderController.java) — `list()` 메서드(`@GetMapping`) 신규 추가. import 2개(`GetMapping`·`Flux`) 추가. 클래스 헤더 주석에 GET 엔드포인트 명시(POST/GET 2개로).
  - [`PaperOrderRepository`](src/main/java/com/example/futurespapertrading/paper/repository/PaperOrderRepository.java) — 조회 메서드 `findByUserId` → **`findByUserIdOrderByIdDesc`** 로 변경(쿼리 파생으로 `ORDER BY id DESC` = 최신순 추가). 관련 주석도 갱신.
  - [`README.md`](src/main/java/com/example/futurespapertrading/paper/README.md) — 리포지토리 메서드명·컨트롤러 엔드포인트 표를 위 변경에 맞춰 동기화.
  - 이 문서(`BUILD-ORDER.md`) — A-4의 메서드명 표기, D 항목 상태(✅) 갱신.
- **확인(curl)**: 내 주문만 보임. (기준 6 절반 — user_id 격리 확인)
      → ✅ 검증 완료(2026-06-09): Postgres18 + depth 스트림 + 앱을 띄워 curl로 확인.
        userA(id=7) 5건 / userB(id=8) 2건을 넣고 GET → **각자 자기 주문만** 최신순(id DESC)으로 반환
        (A=[7,6,5,4,3], B=[9,8] — 서로의 주문 안 보임 = user_id 격리). 비로그인·엉터리 쿠키 → 401.
        목록은 설계대로 avgPrice=null(fill 미조회), filledQuantity는 상태대로(FILLED=수량, OPEN=0).

## E. 지정가 주문 — POST (즉시 크로싱 + OPEN 등록) ✅ 구현 + curl 검증 완료
- **E-1** ✅ POST에 LIMIT 분기 (`placeMarketOrder`→`placeOrder`로 일반화). 지정가: **완전 체결이면 FILLED, 부분·미체결이면 OPEN**(잔량은 limit 가격에 걸려 대기 → G단계 matcher가 채움). 시장가: 1건이라도 체결 FILLED·0건 REJECTED(잔량 드롭). 호가 없을 때는 지정가 OPEN(실거래소처럼)·시장가 503.
- **E-2** ✅ LIMIT일 때 `limitPrice` 필수+양수 검증 — `CreateOrderRequest`에 `@AssertTrue`(isLimitPriceValid) 교차검증. @Valid 게이트에서 컨트롤러 전에 자동 400.
- **변경 파일**:
  - [`PaperOrderController`](src/main/java/com/example/futurespapertrading/paper/controller/PaperOrderController.java) — `create()`의 MARKET-only 차단(400) 제거. `placeMarketOrder`→`placeOrder`(fills 0건 시 지정가 OPEN/시장가 REJECTED, 호가 없는 지정가 OPEN) + 저장 공통부 `saveOrder` 추출. import `Optional` 추가. (※ `placeOrder`·`saveOrder`는 이후 '서비스 계층 추출' 리팩터로 `PaperOrderService`로 이동)
  - [`dto/CreateOrderRequest`](src/main/java/com/example/futurespapertrading/paper/dto/CreateOrderRequest.java) — `@AssertTrue isLimitPriceValid()` 교차검증 추가(LIMIT면 limitPrice 필수·양수). import `AssertTrue`.
  - [`README.md`](src/main/java/com/example/futurespapertrading/paper/README.md) — 현재 상태·엔드포인트 표 동기화.
- **확인(curl)**: 지정가 BUY를 bestAsk **아래** → OPEN, **위** → 즉시 FILLED. limitPrice 없는/0·음수 LIMIT → 400. (기준 4·5의 OPEN 부분)
      → ✅ 검증 완료(2026-06-09, bestAsk≈62304): BUY @31152(아래)→OPEN / BUY @93456(위)→즉시 FILLED.
        SELL @93456(위)→OPEN / SELL @31152(아래)→즉시 FILLED. **크로싱 체결가는 limit가 아니라 호가** —
        BUY @93456은 limit가 아닌 ask 62304.9에 체결(paper_fills.price로 확인). limitPrice 없음/0/−100 → 400(주문 미생성).
        DB 확인: paper_orders 7건(FILLED 4·OPEN 3, MARKET은 limit_price=NULL), 400요청은 행 0개(max id=9 그대로).
        paper_fills 4건이 FILLED 주문(3·5·7·8)에만 order_id로 연결, OPEN(4·6·9)은 fill 0건, orphan 0.

## (리팩터) 서비스 계층 추출 ✅ 완료 (동작 불변, E와 F 사이)
- **왜**: E까지 컨트롤러가 직접 들고 있던 체결·저장 로직을, F의 취소·G의 matcher가 재사용할 수 있게 서비스로 분리. (컨트롤러 = HTTP만, 비즈니스 = 서비스)
- **무엇**: `placeOrder`·`saveOrder`·`saveFills`·`totalQuantity`를 컨트롤러 → 새 [`PaperOrderService`](src/main/java/com/example/futurespapertrading/paper/service/PaperOrderService.java)로 **주석 그대로 이동**(로직 변경 0). `placeOrder`는 `public`이 되고, `create()`는 `orderService.placeOrder(...)`로 위임.
- **변경 파일**:
  - [`PaperOrderService`](src/main/java/com/example/futurespapertrading/paper/service/PaperOrderService.java) — 신규. 체결 평가·상태 판정·저장 로직 + 의존성(`engine`/`orderRepository`/`fillRepository`/`latestStore`). 컨트롤러 클래스 헤더의 '처리 흐름·⚠️트랜잭션' 주석도 이전.
  - [`PaperOrderController`](src/main/java/com/example/futurespapertrading/paper/controller/PaperOrderController.java) — 슬림화: `create()`는 서비스 위임, 옮긴 4개 메서드 제거, 의존성에서 `engine`/`fillRepository`/`latestStore` 제거. 이후 `list()`도 `PaperOrderService.listOrders`로 위임해 `orderRepository` 의존성까지 제거.
  - [`README.md`](src/main/java/com/example/futurespapertrading/paper/README.md) — 파일별 역할 표에 `PaperOrderService` 행 추가.
- **확인**: 순수 리팩터라 동작 불변 — curl 결과가 추출 전과 동일해야 함. (컴파일 ✅ / 런타임 회귀는 DB 필요)

## F. 주문 취소 — DELETE + 예외 처리 ✅ 구현 + curl 검증 완료
- **F-1** ✅ `DELETE /api/paper/orders/{id}` → **소유자 + OPEN** 검증 후 CANCELED. 검증 순서 = 상태코드 순서(404→403→409). 취소는 행 삭제가 아니라 status를 CANCELED로 바꾸는 **UPDATE**(주문은 원장이라 남김) — record 복제본을 `save()`(id 있음→UPDATE). 성공 시 200 + CANCELED된 `OrderResponse`.
- **F-2** ✅ `PaperExceptionHandler` (404 없음 / 403 남의 것 / 409 OPEN 아님 / 503 호가 없음). 서비스가 던지던 503(`ResponseStatusException`)도 도메인 예외 `QuoteUnavailableException`으로 정리 — 서비스는 HTTP를 모르게, 예외→상태코드 번역은 핸들러로.
- **변경 파일**:
  - [`PaperOrderService`](src/main/java/com/example/futurespapertrading/paper/service/PaperOrderService.java) — `cancel(orderId, userId)` 추가(`findById`→`switchIfEmpty` 404→403→409→CANCELED 복제본 `save`). `placeOrder`의 503을 `QuoteUnavailableException`으로 교체, 웹 타입 import 제거.
  - [`PaperOrderController`](src/main/java/com/example/futurespapertrading/paper/controller/PaperOrderController.java) — `cancel()`(`@DeleteMapping("/{id}")` + `@PathVariable`) 신규. 헤더 주석 엔드포인트 3개로.
  - 도메인 예외 4개 신규 — [`OrderNotFoundException`](src/main/java/com/example/futurespapertrading/paper/exception/OrderNotFoundException.java)(404) / [`OrderForbiddenException`](src/main/java/com/example/futurespapertrading/paper/exception/OrderForbiddenException.java)(403) / [`OrderNotOpenException`](src/main/java/com/example/futurespapertrading/paper/exception/OrderNotOpenException.java)(409) / [`QuoteUnavailableException`](src/main/java/com/example/futurespapertrading/paper/exception/QuoteUnavailableException.java)(503).
  - [`PaperExceptionHandler`](src/main/java/com/example/futurespapertrading/paper/controller/PaperExceptionHandler.java) 신규 — `AuthExceptionHandler`와 같은 `@RestControllerAdvice` 패턴, 도메인 예외 → `상태코드 + {"message": ...}` 번역 4개.
  - [`README.md`](src/main/java/com/example/futurespapertrading/paper/README.md) — 파일 표·현재 상태 동기화.
- **확인(curl)**: E에서 만든 OPEN을 취소 → CANCELED. 남의 주문 취소 불가(403). 없는 id 404 / FILLED 취소 409 / 재취소 409. (기준 6 완성)
      → ✅ 검증 완료(2026-06-11): Postgres18 + depth 스트림 + 앱을 띄워 curl로 확인. 유저 2명(A=user_id 9, B)으로 —
        A의 OPEN(지정가 BUY @10000, id=10) 취소 → **200 + CANCELED** / 같은 주문 재취소 → **409** "OPEN 상태가 아니라…CANCELED" /
        B가 A의 OPEN(id=11) 취소 시도 → **403** "내 주문이 아닙니다" / 없는 id 999999 → **404** /
        시장가 FILLED(id=12) 취소 → **409** "…FILLED" / 비로그인 DELETE → **401**(Security 필터).
        DB 확인: id 10·11이 행 삭제 없이 status=CANCELED로 남음(UPDATE), filled_quantity=0 유지, fill 미생성.

## (리팩터) 도메인 안 계층 폴더 분리 ✅ 완료 (동작 불변, F와 G 사이)
- **왜**: 도메인 폴더가 평면(flat)이라 paper에 자바 파일 14개가 뒤섞임(F에서 5개 추가). 폴더 트리가 계층 구조(컨트롤러→서비스→도메인→저장소)를 그대로 보여주도록 분리 — "큰 칸은 도메인, 작은 칸은 계층".
- **무엇**: 클래스 **내용 변경 0** — 파일 이동 + `package` 선언 + `import`만 갱신. 배치 기준: HTTP를 알면 controller, DB를 알면 repository, 둘 다 모르는 업무 개념·규칙이면 domain.
  - `paper/` → `controller`(컨트롤러+예외핸들러) / `service` / `domain`(엔티티·enum·엔진) / `repository` / `exception`(도메인 예외 4개) / `dto`(기존)
  - `auth/` → `controller` / `service`(AuthService·SecurityUserDetailsService) / `domain`(User) / `repository` / `config`(SecurityConfig·PasswordEncoderConfig) / `dto`(기존)
  - `market/` → `controller` / `domain`(snapshot·level·quotes) / `stream`(수신→파싱→보관 파이프라인: streamer·parser·store). DB·서비스가 없는 도메인이라 계층 이름도 실제 있는 것만.
  - 테스트도 같은 패키지로 미러링: `paper/domain/PaperTradingEngineTest`, `market/domain/OrderBookQuotesTest`.
  - ※ 자바엔 '하위 패키지 가시성'이 없어 `paper.domain`과 `paper.service`는 서로 남남(전부 public이라 실질 영향 없음).
- **확인**: 순수 이동이라 동작 불변 — 컴파일 ✅ / 단위테스트 13개 ✅ / 부팅 + curl 회귀 ✅.

## G. 대기 주문 자동 체결 — 백그라운드 matcher (정정된 설계)
- **G-1** `openOrderCount` 카운터: OPEN 생성 +1 / 체결·취소 −1
- **G-2** `PendingOrderMatcher`: `@PostConstruct`에서 `stream()` 구독 — **`sample()` 없이 모든 snapshot**, `concatMap`(직렬화), `openOrderCount==0이면 skip`, `onErrorContinue`. (OPEN 주문 재평가·체결은 `PaperOrderService`의 체결·저장 로직을 재사용)
- **G-3** 중복 체결 방지: 조건부 갱신 `UPDATE … WHERE status='OPEN'`
- **확인(curl)**: 지정가 BUY를 bestAsk 살짝 아래로 → OPEN, 가격이 내려와 닿으면 **자동 FILLED**. SELL 반대. (기준 4·5 "닿으면 FILLED" 완성)

## H. (선택) 프론트 주문 폼
- 9단계 거래화면(잔고/포지션/PnL)과 함께 만드는 걸 권장. 8단계는 G까지 curl로 완결.

---

### 완료 기준이 채워지는 지점
| 기준 | 달성 단계 |
|---|---|
| 1 비로그인 주문 불가 | C |
| 2·3 시장가 best ask/bid 체결 | C |
| 4·5 지정가 OPEN → 닿으면 FILLED | E(OPEN) + **G**(자동 체결) |
| 6 로그인 사용자 것에만 반영 | D + F (user_id 격리) |

**A→B→C→D→E→F→G** 순서면 각 단계가 앞 단계 위에서만 검증되고, G까지 끝나면 8단계 완료 기준 6개가 전부 충족됩니다. (E와 F 사이에 '서비스 계층 추출' 리팩터 1회 — 동작 불변.) 어느 단계부터 코드로 들어갈지 말씀해 주세요.
