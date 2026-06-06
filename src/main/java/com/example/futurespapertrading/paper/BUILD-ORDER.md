8단계 안에서 다시 더 잘게 쪼갠 구현 순서입니다. 각 단계는 **독립적으로 검증 가능**하고, 앞 검증의 정정 사항(`sample()` 제거, 빈 카운터 가드, 중복 체결 방지)을 반영했습니다.

## A. 영속성 토대 (동작 없음, 그릇만)
- **A-1** [schema.sql](src/main/resources/schema.sql)에 `paper_orders` + `paper_fills` 두 테이블 추가
- **A-2** enum 3개: `OrderSide`(BUY/SELL), `OrderType`(MARKET/LIMIT), `OrderStatus`(NEW/OPEN/FILLED/CANCELED/REJECTED)
- **A-3** `PaperOrder`, `PaperFill` record 엔티티 ([User](src/main/java/com/example/futurespapertrading/auth/User.java) 미러)
- **A-4** `PaperOrderRepository`, `PaperFillRepository` (`findByUserId`, `findByStatus`)
- **확인**: 앱이 뜨고 psql `\d`로 두 테이블 생성 확인. 끝.

## B. 순수 체결 엔진 + 단위 테스트 ★ (로드맵이 "주문 단계부터 테스트"라 한 지점)
- **B-1** bestBid/bestAsk 헬퍼 (백엔드 BigDecimal — 프론트 [quote.ts](frontend/src/quote.ts) 미러, 정렬 가정 안 함)
- **B-2** `PaperTradingEngine.tryFill(order, snapshot)` 순수 함수 → fill 목록 또는 empty
- **B-3** 단위 테스트: 시장가 BUY/SELL 체결가, 지정가 크로싱/미크로싱, **딱 닿는 경계**(`≤/≥`)
- **확인**: `./gradlew test` 통과. DB·웹 없이 로직만. (기준 2·3·4·5의 *로직*이 여기서 굳음)

## C. 시장가 주문 — POST (즉시 체결만)
- **C-1** dto `CreateOrderRequest` / `OrderResponse`
- **C-2** `PaperOrderController` `POST /api/paper/orders` — 현재 user_id 확인 → `latest()` → `tryFill` → 주문+fill 저장 → 응답. **MARKET만**
- **C-3** `@Valid`(quantity>0), 호가 없을 때 503
- **확인(curl)**: 로그인 쿠키로 시장가 BUY 0.01 → FILLED + best ask 가격. **비로그인 → 401**. (기준 1·2·3 달성)

## D. 내 주문 목록 — GET
- **D-1** `GET /api/paper/orders` → `findByUserId(현재 유저)`만
- **확인(curl)**: 내 주문만 보임. (기준 6 절반 — user_id 격리 확인)

## E. 지정가 주문 — POST (즉시 크로싱 + OPEN 등록)
- **E-1** POST에 LIMIT 분기: 이미 닿으면 즉시 FILLED, 아니면 **OPEN 저장**
- **E-2** LIMIT일 때 `limit_price` 필수 검증
- **확인(curl)**: 지정가 BUY를 bestAsk **아래** → OPEN, **위** → 즉시 FILLED. (기준 4·5의 OPEN 부분)

## F. 주문 취소 — DELETE + 예외 처리
- **F-1** `DELETE /api/paper/orders/{id}` → **소유자 + OPEN** 검증 후 CANCELED
- **F-2** `PaperExceptionHandler` (404 없음 / 403 남의 것 / 409 OPEN 아님)
- **확인(curl)**: E에서 만든 OPEN을 취소 → CANCELED. 남의 주문 취소 불가. (기준 6 완성)

## G. 대기 주문 자동 체결 — 백그라운드 matcher (정정된 설계)
- **G-1** `openOrderCount` 카운터: OPEN 생성 +1 / 체결·취소 −1
- **G-2** `PendingOrderMatcher`: `@PostConstruct`에서 `stream()` 구독 — **`sample()` 없이 모든 snapshot**, `concatMap`(직렬화), `openOrderCount==0이면 skip`, `onErrorContinue`
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

**A→B→C→D→E→F→G** 순서면 각 단계가 앞 단계 위에서만 검증되고, G까지 끝나면 8단계 완료 기준 6개가 전부 충족됩니다. 어느 단계부터 코드로 들어갈지 말씀해 주세요.
