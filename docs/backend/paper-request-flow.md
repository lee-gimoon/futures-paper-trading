# Paper Create Request Flow

이 문서는 `POST /api/paper/orders` 요청 하나만 따라간다.

방식은 단순하다.

```text
1. 실제 코드를 주석 없이 먼저 본다.
2. 그 코드에서 내가 구현한 호출이 무엇인지 확인한다.
3. 그 호출의 내부 코드로 내려간다.
4. 저장과 응답까지 같은 방식으로 따라간다.
```

즉 이 문서는 `PaperOrderController` 클래스의 `create()` 메서드로 들어온 요청의 실행 흐름을 코드 순서대로 펼쳐 놓은 것이다.

## 전체 호출 순서

```text
PaperOrderController 클래스의 create(req) 메서드
├─ req: CreateOrderRequest
├─ PaperOrderController 클래스의 currentUserId() 메서드
│  └─ userRepository.findByEmail(email)
└─ PaperOrderService 클래스의 placeOrder(req, userId) 메서드
   ├─ PortfolioService 클래스의 accountState(userId) 메서드
   │  ├─ PortfolioService 클래스의 getOrCreateAccount(userId) 메서드
   │  ├─ fillRepository.findByUserIdOrderByIdAsc(userId)
   │  ├─ orderRepository.findByUserIdOrderByIdDesc(userId)
   │  └─ PortfolioService 클래스의 toState(account, fills, orderLeverage) 메서드
   │     ├─ PositionCalculator.compute(fills)
   │     ├─ PositionCalculator.openPositionLeverage(...)
   │     ├─ PortfolioService 클래스의 midPrice() 메서드
   │     │  ├─ latestStore.latest()
   │     │  ├─ OrderBookQuotes.bestBid(snapshot)
   │     │  └─ OrderBookQuotes.bestAsk(snapshot)
   │     └─ MarginCalculator.usedMargin(pos, positionLeverage)
   ├─ PaperOrderService 클래스의 requiredAdditionalMargin(req, state) 메서드
   └─ PaperOrderService 클래스의 placeOrderAfterMarginCheck(req, userId, leverage) 메서드
      ├─ latestStore.latest()
      ├─ engine.tryFill(probe, snapshot)
      ├─ PaperOrderService 클래스의 totalQuantity(fills) 메서드
      ├─ PaperOrderService 클래스의 saveOrder(req, userId, status, filledQty, fills, leverage) 메서드
      │  ├─ orderRepository.save(toSave)
      │  ├─ openOrderCounter.increment()
      │  ├─ PaperOrderService 클래스의 saveFills(saved.id(), fills) 메서드
      │  │  └─ fillRepository.saveAll(withOrderId)
      │  └─ OrderResponse record의 from(saved, fills) 메서드
      └─ OrderResponse
```

## 1. 시작점: PaperOrderController 클래스의 create() 메서드

파일: [`PaperOrderController.java`](../../src/main/java/com/example/futurespapertrading/paper/controller/PaperOrderController.java)

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public Mono<OrderResponse> create(@Valid @RequestBody CreateOrderRequest req) {
    return currentUserId().flatMap(userId -> orderService.placeOrder(req, userId));
}
```

이 코드에서 직접 봐야 하는 것은 3개다.

| 코드 | 무엇인가 |
|---|---|
| `CreateOrderRequest req` | 요청 JSON이 변환된 주문 생성 DTO |
| `currentUserId()` | `PaperOrderController` 클래스에서 현재 로그인 사용자의 DB id를 꺼내는 메서드 |
| `orderService.placeOrder(req, userId)` | `PaperOrderService` 클래스의 `placeOrder()` 메서드로 이어지는 주문 생성 유스케이스의 서비스 진입점 |

실행 순서는 이렇다.

```text
1. 요청 body가 CreateOrderRequest로 변환된다.
2. @Valid가 CreateOrderRequest 검증을 수행한다.
3. 검증 통과 후 `PaperOrderController` 클래스의 `create()` 메서드 본문에 들어온다.
4. 같은 클래스의 `currentUserId()` 메서드로 userId를 구하는 Mono를 만든다.
5. userId가 나오면 flatMap(userId -> ...) 형태로 `PaperOrderService` 클래스의 `placeOrder()` 메서드를 호출한다.
```

`PaperOrderController` 클래스의 `create()` 메서드는 주문을 직접 저장하지 않는다. HTTP 입구 역할만 하고, 실제 주문 처리는 `PaperOrderService`로 넘긴다.

## 2. req: CreateOrderRequest

파일: [`CreateOrderRequest.java`](../../src/main/java/com/example/futurespapertrading/paper/dto/CreateOrderRequest.java)

```java
public record CreateOrderRequest(
        @NotBlank @Pattern(regexp = "BTCUSDT", message = "지원하는 symbol은 BTCUSDT 뿐입니다") String symbol,
        @Pattern(regexp = "BUY|SELL", message = "side는 BUY 또는 SELL 이어야 합니다") String side,
        @Pattern(regexp = "MARKET|LIMIT", message = "type은 MARKET 또는 LIMIT 이어야 합니다") String type,
        @NotNull @Positive BigDecimal quantity,
        BigDecimal limitPrice
) {
    @AssertTrue(message = "지정가(LIMIT)는 limitPrice가 양수여야 합니다")
    private boolean isLimitPriceValid() {
        if (!"LIMIT".equals(type)) return true;
        return limitPrice != null && limitPrice.signum() > 0;
    }
}
```

`PaperOrderController` 클래스의 `create()` 메서드 매개변수 `req`는 이 record 객체다.

```java
public Mono<OrderResponse> create(@Valid @RequestBody CreateOrderRequest req)
```

여기서 일어나는 일:

```text
1. 클라이언트 JSON body가 CreateOrderRequest로 변환된다.
2. @Valid 때문에 record에 붙은 검증 애너테이션들이 실행된다.
3. symbol은 BTCUSDT만 가능하다.
4. side는 BUY 또는 SELL만 가능하다.
5. type은 MARKET 또는 LIMIT만 가능하다.
6. quantity는 null이면 안 되고 양수여야 한다.
7. type이 LIMIT이면 limitPrice도 null이 아니고 양수여야 한다.
```

`CreateOrderRequest` record의 `isLimitPriceValid()` 메서드는 지정가 주문의 추가 규칙이다.

```text
MARKET 주문
→ limitPrice를 쓰지 않으므로 true

LIMIT 주문
→ limitPrice != null && limitPrice.signum() > 0 이어야 true
```

검증 실패 시에는 `PaperOrderController` 클래스의 `create()` 메서드 본문이 끝까지 실행되지 않고 400 응답으로 막힌다.

## 3. PaperOrderController 클래스의 currentUserId() 메서드

파일: [`PaperOrderController.java`](../../src/main/java/com/example/futurespapertrading/paper/controller/PaperOrderController.java)

```java
private Mono<Long> currentUserId() {
    return ReactiveSecurityContextHolder.getContext()
            .map(ctx -> ctx.getAuthentication().getName())
            .flatMap(userRepository::findByEmail)
            .map(User::id);
}
```

`PaperOrderController` 클래스의 `create()` 메서드에서 이 부분이다.

```java
return currentUserId().flatMap(userId -> orderService.placeOrder(req, userId));
```

`PaperOrderController` 클래스의 `currentUserId()` 메서드 흐름:

```text
1. ReactiveSecurityContextHolder.getContext()
   → 현재 요청의 보안 컨텍스트를 Mono로 꺼낸다.

2. .map(ctx -> ctx.getAuthentication().getName())
   → 인증 객체에서 로그인 식별자인 email을 꺼낸다.

3. .flatMap(userRepository::findByEmail)
   → email로 DB에서 User를 찾는다.

4. .map(User::id)
   → User 객체에서 id만 꺼낸다.
```

`PaperOrderController` 클래스의 `currentUserId()` 메서드 결과 타입은 `Mono<Long>`이다.

```text
아직 Long 값이 바로 있는 것이 아니라,
나중에 userId Long 하나를 흘려보낼 비동기 파이프라인이다.
```

그래서 `Long userId = currentUserId()`처럼 바로 꺼내 쓸 수 없다.
대신 `flatMap(userId -> ...)` 형태로 작성한다.
여기서 `userId`는 `PaperOrderController` 클래스의 `currentUserId()` 메서드가 나중에 만들어 낸 Long 값이다.
그 값을 사용해 `PaperOrderService` 클래스의 `placeOrder()` 메서드를 호출한다.

여기서 `map`이 아니라 `flatMap`을 쓰는 직접적인 이유는,
userId를 받은 뒤 호출하는 `PaperOrderService` 클래스의 `placeOrder()` 메서드가 일반 `OrderResponse`가 아니라 `Mono<OrderResponse>`를 반환하기 때문이다.

```text
map을 쓰면:
Mono<Long> → Mono<Mono<OrderResponse>>

flatMap을 쓰면:
Mono<Long> → Mono<OrderResponse>
```

즉 `flatMap`은 `placeOrder(...)`가 반환한 안쪽 `Mono`를 한 겹 펼쳐서,
컨트롤러의 최종 반환 타입을 `Mono<OrderResponse>`로 이어준다.

## 4. placeOrder(req, userId)

파일: [`PaperOrderService.java`](../../src/main/java/com/example/futurespapertrading/paper/service/PaperOrderService.java)

```java
public Mono<OrderResponse> placeOrder(CreateOrderRequest req, Long userId) {
    return portfolioService.accountState(userId).flatMap(state -> {
        boolean isLimit = OrderType.LIMIT.name().equals(req.type());
        if (!isLimit && state.mark() == null)
            return Mono.<OrderResponse>error(new QuoteUnavailableException("호가 수신 전이라 증거금을 계산할 수 없습니다."));

        BigDecimal requiredAdditionalMargin = requiredAdditionalMargin(req, state);
        if (requiredAdditionalMargin.compareTo(state.availableBalance()) > 0)
            return Mono.<OrderResponse>error(new InsufficientMarginException(
                    "가용 증거금 부족: 필요 " + requiredAdditionalMargin + " > 가용 " + state.availableBalance()
                            + " (레버리지 " + state.account().leverage() + "x)"));
        return placeOrderAfterMarginCheck(req, userId, state.account().leverage());
    });
}
```

`PaperOrderController` 클래스의 `create()` 메서드에서 이 부분이다.

```java
return currentUserId().flatMap(userId -> orderService.placeOrder(req, userId));
```

`PaperOrderController` 클래스의 `currentUserId()` 메서드가 만든 userId로 이 서비스 메서드를 호출한다.

`PaperOrderService` 클래스의 `placeOrder()` 메서드에서 봐야 하는 내부 호출은 3개다.

| 코드 | 역할 |
|---|---|
| `portfolioService.accountState(userId)` | `PortfolioService` 클래스의 `accountState()` 메서드로 계좌, 포지션, mark, 가용잔고 계산 |
| `requiredAdditionalMargin(req, state)` | 이번 주문에 필요한 추가 증거금 계산 |
| `placeOrderAfterMarginCheck(...)` | 증거금 통과 후 체결 판정과 저장 |

실행 흐름:

```text
1. accountState(userId)로 현재 계좌 상태를 계산한다.
2. state가 나오면 flatMap 안으로 들어온다.
3. req.type()이 LIMIT인지 확인한다.
4. 시장가 주문인데 state.mark()가 null이면 증거금 기준가가 없어 예외를 반환한다.
5. 추가 필요 증거금을 계산한다.
6. 필요 증거금이 가용잔고보다 크면 예외를 반환한다.
7. 통과하면 placeOrderAfterMarginCheck(...)로 넘어간다.
```

조건문을 풀면 이렇다.

```java
if (!isLimit && state.mark() == null)
```

```text
!isLimit
→ 지정가가 아니다
→ 시장가 주문이다

state.mark() == null
→ 현재 평가 기준 가격이 없다

둘 다 true
→ 시장가인데 가격 기준이 없으므로 증거금 계산 불가
```

## 5. accountState(userId)

파일: [`PortfolioService.java`](../../src/main/java/com/example/futurespapertrading/paper/service/PortfolioService.java)

```java
public Mono<AccountState> accountState(Long userId) {
    return getOrCreateAccount(userId).flatMap(account ->
            Mono.zip(
                    fillRepository.findByUserIdOrderByIdAsc(userId).collectList(),
                    orderRepository.findByUserIdOrderByIdDesc(userId)
                            .collectMap(PaperOrder::id, PaperOrder::leverage)
            ).map(t -> toState(account, t.getT1(), t.getT2())));
}
```

`PaperOrderService` 클래스의 `placeOrder()` 메서드 첫 줄에서 호출된다.

```java
return portfolioService.accountState(userId).flatMap(state -> {
```

이 메서드는 주문을 넣기 전에 현재 계좌 상태를 만든다.

실행 흐름:

```text
1. getOrCreateAccount(userId)
   → 계좌가 있으면 가져오고, 없으면 새 계좌를 만든다.

2. 계좌 account가 나오면 flatMap 안으로 들어온다.

3. Mono.zip(...)으로 두 조회를 함께 실행한다.
   - fillRepository.findByUserIdOrderByIdAsc(userId).collectList()
   - orderRepository.findByUserIdOrderByIdDesc(userId).collectMap(...)

4. 두 조회가 모두 끝나면 Tuple이 나온다.

5. t.getT1()
   → 체결 목록 List<PaperFill>

6. t.getT2()
   → 주문 id별 레버리지 Map<Long, Integer>

7. toState(account, fills, orderLeverage)로 AccountState를 만든다.
```

여기서 만든 값들은 `PaperOrderService` 클래스의 `placeOrder()` 메서드와,
그 안에서 호출하는 `requiredAdditionalMargin(req, state)` 메서드에서 사용된다.

| 값 | 어디에 쓰이나 |
|---|---|
| `state.mark()` | `placeOrder()`에서 시장가 주문의 mark 존재 여부 확인, `requiredAdditionalMargin(req, state)`에서 시장가 주문의 증거금 기준가로 사용 |
| `state.availableBalance()` | `placeOrder()`에서 필요 증거금과 비교 |
| `state.account().leverage()` | `requiredAdditionalMargin(req, state)`에서 필요 증거금 계산, `placeOrderAfterMarginCheck(...)`에 넘겨 주문에 저장 |
| `state.position()` | `requiredAdditionalMargin(req, state)` 안에서 현재 포지션 방향/수량을 확인할 때 사용 |

## 6. getOrCreateAccount(userId)

파일: [`PortfolioService.java`](../../src/main/java/com/example/futurespapertrading/paper/service/PortfolioService.java)

```java
private Mono<PaperAccount> getOrCreateAccount(Long userId) {
    return accountRepository.findByUserId(userId)
            .switchIfEmpty(Mono.defer(() ->
                    accountRepository.save(new PaperAccount(null, userId, SEED_CASH, DEFAULT_LEVERAGE))));
}
```

`PortfolioService` 클래스의 `accountState()` 메서드에서 가장 먼저 호출된다.

```java
return getOrCreateAccount(userId).flatMap(account -> ...)
```

실행 흐름:

```text
1. accountRepository.findByUserId(userId)
   → paper_accounts에서 현재 사용자의 계좌를 찾는다.

2. 계좌가 있으면 그 계좌를 그대로 흘려보낸다.

3. 계좌가 없으면 switchIfEmpty(...)가 실행된다.

4. Mono.defer(...) 안에서 새 PaperAccount를 만든다.

5. accountRepository.save(...)로 시드 계좌를 DB에 저장한다.
```

새 계좌 기본값:

```text
cashBalance = SEED_CASH = 10000
leverage = DEFAULT_LEVERAGE = 10
```

## 7. accountState 안의 repository 조회

파일:

- [`PaperFillRepository.java`](../../src/main/java/com/example/futurespapertrading/paper/repository/PaperFillRepository.java)
- [`PaperOrderRepository.java`](../../src/main/java/com/example/futurespapertrading/paper/repository/PaperOrderRepository.java)

```java
@Query("SELECT f.* FROM paper_fills f JOIN paper_orders o ON f.order_id = o.id WHERE o.user_id = :userId ORDER BY f.id")
Flux<PaperFill> findByUserIdOrderByIdAsc(Long userId);
```

```java
Flux<PaperOrder> findByUserIdOrderByIdDesc(Long userId);
```

`PortfolioService` 클래스의 `accountState()` 메서드 안에서는 이렇게 쓰인다.

```java
Mono.zip(
        fillRepository.findByUserIdOrderByIdAsc(userId).collectList(),
        orderRepository.findByUserIdOrderByIdDesc(userId)
                .collectMap(PaperOrder::id, PaperOrder::leverage)
)
```

각각의 의미:

```text
fillRepository.findByUserIdOrderByIdAsc(userId)
→ 내 체결 기록을 오래된 순서대로 가져온다.
→ PositionCalculator가 체결을 시간순으로 재생해야 하므로 오름차순이다.

collectList()
→ Flux<PaperFill>을 Mono<List<PaperFill>>로 모은다.

orderRepository.findByUserIdOrderByIdDesc(userId)
→ 내 주문 목록을 가져온다.

collectMap(PaperOrder::id, PaperOrder::leverage)
→ 주문 id → 주문 당시 레버리지 Map을 만든다.
```

## 8. toState(account, fills, orderLeverage)

파일: [`PortfolioService.java`](../../src/main/java/com/example/futurespapertrading/paper/service/PortfolioService.java)

```java
private AccountState toState(PaperAccount account, List<PaperFill> fills, Map<Long, Integer> orderLeverage) {
    Position pos = PositionCalculator.compute(fills);
    int positionLeverage = PositionCalculator.openPositionLeverage(fills, orderLeverage, account.leverage());
    BigDecimal mark = midPrice();
    boolean flat = pos.signedQuantity().signum() == 0;

    BigDecimal unrealized = (flat || mark == null)
            ? BigDecimal.ZERO
            : mark.subtract(pos.averageEntryPrice()).multiply(pos.signedQuantity());

    BigDecimal walletBalance = account.cashBalance().add(pos.realizedPnl());
    BigDecimal usedMargin = MarginCalculator.usedMargin(pos, positionLeverage);
    BigDecimal available = walletBalance.subtract(usedMargin).max(BigDecimal.ZERO);

    return new AccountState(account, pos, positionLeverage, mark, unrealized, walletBalance, usedMargin, available);
}
```

`PortfolioService` 클래스의 `accountState()` 메서드 마지막에서 호출된다.

```java
).map(t -> toState(account, t.getT1(), t.getT2())));
```

이 메서드는 DB에서 읽은 재료를 주문 검증에 쓸 수 있는 상태로 바꾼다.

순서:

```text
1. PositionCalculator.compute(fills)
   → 체결 기록으로 현재 포지션을 계산한다.

2. PositionCalculator.openPositionLeverage(...)
   → 현재 열린 포지션의 레버리지를 계산한다.

3. `PortfolioService` 클래스의 `midPrice()` 메서드
   → 현재 호가의 중간 가격을 mark로 계산한다.

4. flat 판단
   → 현재 포지션 수량이 0인지 본다.

5. unrealized 계산
   → 포지션과 mark가 있으면 미실현손익을 계산한다.

6. walletBalance 계산
   → 초기 계좌 현금 + 이미 확정된 실현손익
   → 아직 보유 중인 포지션의 미실현손익은 포함하지 않는다.

7. usedMargin 계산
   → 현재 포지션에 묶인 증거금

8. available 계산
   → walletBalance - usedMargin

9. AccountState로 묶어 반환한다.
```

`PaperOrderService` 클래스의 `placeOrder()` 메서드에서는 이 중 특히 `mark`, `availableBalance`, `position`, `account.leverage`를 사용한다.

## 9. PositionCalculator.compute(fills)

파일: [`PositionCalculator.java`](../../src/main/java/com/example/futurespapertrading/paper/domain/PositionCalculator.java)

```java
public static Position compute(List<PaperFill> fills) {
    BigDecimal signedQty = BigDecimal.ZERO;
    BigDecimal avgEntry = BigDecimal.ZERO;
    BigDecimal realized = BigDecimal.ZERO;

    for (PaperFill f : fills) {
        BigDecimal signedFillQuantity = OrderSide.BUY.name().equals(f.side()) ? f.quantity() : f.quantity().negate();
        BigDecimal price = f.price();

        if (signedQty.signum() == 0 || signedQty.signum() == signedFillQuantity.signum()) {
            BigDecimal absSignedQty = signedQty.abs();
            BigDecimal absSignedFillQuantity = signedFillQuantity.abs();
            BigDecimal totalAbs = absSignedQty.add(absSignedFillQuantity);
            avgEntry = absSignedQty.multiply(avgEntry).add(absSignedFillQuantity.multiply(price))
                    .divide(totalAbs, 8, RoundingMode.HALF_UP);
            signedQty = signedQty.add(signedFillQuantity);
        } else {
            BigDecimal closeQty = signedFillQuantity.abs().min(signedQty.abs());
            BigDecimal direction = BigDecimal.valueOf(signedQty.signum());
            realized = realized.add(price.subtract(avgEntry).multiply(closeQty).multiply(direction));

            BigDecimal newSignedQty = signedQty.add(signedFillQuantity);
            if (newSignedQty.signum() == 0) {
                avgEntry = BigDecimal.ZERO;
            } else if (signedQty.signum() != newSignedQty.signum()) {
                avgEntry = price;
            }
            signedQty = newSignedQty;
        }
    }
    return new Position(signedQty, avgEntry, realized);
}
```

`PortfolioService` 클래스의 `toState()` 메서드에서 이 부분이다.

```java
Position pos = PositionCalculator.compute(fills);
```

이 메서드는 `fills`를 시간순으로 누적해서 현재 포지션을 만든다.

핵심 변수:

| 변수 | 의미 |
|---|---|
| `signedQty` | 부호 있는 순포지션 수량. 양수면 롱, 음수면 숏, 0이면 flat |
| `avgEntry` | 현재 열린 포지션의 평균 진입가 |
| `realized` | 지금까지 확정된 실현손익 |

체결 방향:

```text
BUY fill
→ signedFillQuantity = +quantity

SELL fill
→ signedFillQuantity = -quantity
```

분기:

```text
현재 포지션 없음 또는 같은 방향 체결
→ 포지션 증가
→ 평균 진입가를 다시 계산

반대 방향 체결
→ 기존 포지션 축소/청산/뒤집기
→ 닫힌 수량만큼 realized PnL 계산
```

결과는 `Position`이다.

```java
public record Position(
        BigDecimal signedQuantity,
        BigDecimal averageEntryPrice,
        BigDecimal realizedPnl
) {
}
```

## 10. PositionCalculator.openPositionLeverage(...)

파일: [`PositionCalculator.java`](../../src/main/java/com/example/futurespapertrading/paper/domain/PositionCalculator.java)

```java
public static int openPositionLeverage(List<PaperFill> fills, Map<Long, Integer> orderLeverage, int fallback) {
    BigDecimal signedQty = BigDecimal.ZERO;
    int lev = fallback;

    for (PaperFill f : fills) {
        int before = signedQty.signum();

        signedQty = signedQty.add(
                OrderSide.BUY.name().equals(f.side())
                        ? f.quantity()
                        : f.quantity().negate()
        );

        int after = signedQty.signum();

        if (after == 0) {
            lev = fallback;
        } else if (before != after) {
            lev = orderLeverage.getOrDefault(f.orderId(), fallback);
        }
    }

    return lev;
}
```

`PortfolioService` 클래스의 `toState()` 메서드에서 이 부분이다.

```java
int positionLeverage = PositionCalculator.openPositionLeverage(fills, orderLeverage, account.leverage());
```

이 메서드는 현재 열린 포지션의 레버리지를 찾는다.

왜 필요한가:

```text
계좌 레버리지는 나중에 바뀔 수 있다.
하지만 이미 열린 포지션은 진입 당시 레버리지를 유지해야 한다.
```

흐름:

```text
1. fills를 시간순으로 다시 재생한다.
2. 체결 전 방향 before를 본다.
3. 이번 체결을 signedQty에 반영한다.
4. 체결 후 방향 after를 본다.
5. after == 0이면 포지션이 완전히 닫힌 것이므로 fallback으로 되돌린다.
6. before != after이면 새 포지션 run이 시작된 것이므로 해당 fill의 orderId로 주문 당시 레버리지를 찾는다.
7. 마지막 lev가 현재 열린 포지션의 레버리지다.
```

## 11. PortfolioService 클래스의 midPrice() 메서드

파일: [`PortfolioService.java`](../../src/main/java/com/example/futurespapertrading/paper/service/PortfolioService.java)

```java
private BigDecimal midPrice() {
    OrderBookSnapshot snapshot = latestStore.latest().orElse(null);
    if (snapshot == null) return null;
    BigDecimal bestBid = OrderBookQuotes.bestBid(snapshot);
    BigDecimal bestAsk = OrderBookQuotes.bestAsk(snapshot);
    if (bestBid == null || bestAsk == null) return null;
    return bestBid.add(bestAsk).divide(BigDecimal.valueOf(2));
}
```

`PortfolioService` 클래스의 `toState()` 메서드에서 호출된다.

```java
BigDecimal mark = midPrice();
```

이 메서드는 현재 평가 기준 가격인 `mark`를 만든다.

흐름:

```text
1. latestStore.latest()에서 최신 호가 snapshot을 꺼낸다.
2. snapshot이 없으면 null을 반환한다.
3. bestBid를 계산한다.
4. bestAsk를 계산한다.
5. 둘 중 하나라도 없으면 null을 반환한다.
6. 둘 다 있으면 (bestBid + bestAsk) / 2를 반환한다.
```

이 값이 `PaperOrderService` 클래스의 `placeOrder()` 메서드에서 시장가 증거금 계산 기준가로 쓰인다.

## 12. OrderBookQuotes.bestBid / bestAsk

파일: [`OrderBookQuotes.java`](../../src/main/java/com/example/futurespapertrading/market/domain/OrderBookQuotes.java)

```java
public static BigDecimal bestBid(OrderBookSnapshot snapshot) {
    return snapshot.bids().stream()
            .map(OrderBookLevel::price)
            .max(Comparator.naturalOrder())
            .orElse(null);
}

public static BigDecimal bestAsk(OrderBookSnapshot snapshot) {
    return snapshot.asks().stream()
            .map(OrderBookLevel::price)
            .min(Comparator.naturalOrder())
            .orElse(null);
}
```

`PortfolioService` 클래스의 `midPrice()` 메서드에서 이 부분이다.

```java
BigDecimal bestBid = OrderBookQuotes.bestBid(snapshot);
BigDecimal bestAsk = OrderBookQuotes.bestAsk(snapshot);
```

의미:

```text
bestBid
→ bids 중 가장 높은 가격
→ 지금 가장 비싸게 사겠다는 매수호가

bestAsk
→ asks 중 가장 낮은 가격
→ 지금 가장 싸게 팔겠다는 매도호가
```

`PortfolioService` 클래스의 `midPrice()` 메서드는 이 둘의 중간값을 mark로 쓴다.

## 13. MarginCalculator.usedMargin(...)

파일: [`MarginCalculator.java`](../../src/main/java/com/example/futurespapertrading/paper/domain/MarginCalculator.java)

```java
public static BigDecimal notional(Position pos) {
    return pos.averageEntryPrice().multiply(pos.signedQuantity().abs());
}

public static BigDecimal usedMargin(Position pos, int leverage) {
    if (pos.signedQuantity().signum() == 0) return BigDecimal.ZERO;
    return notional(pos).divide(BigDecimal.valueOf(leverage), 8, RoundingMode.HALF_UP);
}
```

`PortfolioService` 클래스의 `toState()` 메서드에서 호출된다.

```java
BigDecimal usedMargin = MarginCalculator.usedMargin(pos, positionLeverage);
```

의미:

```text
notional
→ 평균 진입가 × 포지션 수량 절댓값
→ 포지션의 명목금액

usedMargin
→ 명목금액 / 레버리지
→ 현재 포지션에 묶인 증거금
```

포지션이 없으면 사용 증거금은 0이다.

## 14. AccountState

파일: [`PortfolioService.java`](../../src/main/java/com/example/futurespapertrading/paper/service/PortfolioService.java)

```java
public record AccountState(
        PaperAccount account,
        Position position,
        int positionLeverage,
        BigDecimal mark,
        BigDecimal unrealizedPnl,
        BigDecimal walletBalance,
        BigDecimal usedMargin,
        BigDecimal availableBalance
) {
}
```

`PortfolioService` 클래스의 `toState()` 메서드 마지막에서 만들어 반환하는 객체다.

```java
return new AccountState(account, pos, positionLeverage, mark, unrealized, walletBalance, usedMargin, available);
```

그리고 `PortfolioService` 클래스의 `accountState()` 메서드 최종 결과이기도 하다.

`PaperOrderService` 클래스의 `placeOrder()` 메서드에서는 이 값을 받아서 주문 가능 여부를 검증한다.

특히 create 흐름에서 쓰는 필드는 이렇다.

| 필드 | 사용 위치 |
|---|---|
| `account()` | 주문에 저장할 계좌 레버리지 |
| `position()` | 추가 증거금 대상 수량 계산 |
| `mark()` | 시장가 주문의 기준 가격 |
| `availableBalance()` | 필요 증거금과 비교 |

## 15. requiredAdditionalMargin(req, state)

파일: [`PaperOrderService.java`](../../src/main/java/com/example/futurespapertrading/paper/service/PaperOrderService.java)

```java
private static BigDecimal requiredAdditionalMargin(CreateOrderRequest req, PortfolioService.AccountState state) {
    boolean isLimit = OrderType.LIMIT.name().equals(req.type());
    BigDecimal refPrice = isLimit ? req.limitPrice() : state.mark();

    int orderDir = OrderSide.BUY.name().equals(req.side()) ? 1 : -1;
    BigDecimal currentQty = state.position().signedQuantity();
    BigDecimal additionalMarginQty = (currentQty.signum() == 0 || currentQty.signum() == orderDir)
            ? req.quantity()
            : req.quantity().subtract(currentQty.abs()).max(BigDecimal.ZERO);

    if (additionalMarginQty.signum() <= 0) return BigDecimal.ZERO;

    return additionalMarginQty.multiply(refPrice)
            .divide(BigDecimal.valueOf(state.account().leverage()), 8, RoundingMode.HALF_UP);
}
```

`PaperOrderService` 클래스의 `placeOrder()` 메서드에서 호출된다.

```java
BigDecimal requiredAdditionalMargin = requiredAdditionalMargin(req, state);
```

이 메서드는 이번 주문 때문에 추가로 필요한 증거금을 계산한다.

순서:

```text
1. 주문이 LIMIT인지 확인한다.
2. 기준가 refPrice를 정한다.
   - LIMIT이면 req.limitPrice()
   - MARKET이면 state.mark()

3. 주문 방향 orderDir을 정한다.
   - BUY면 +1
   - SELL이면 -1

4. 현재 포지션 수량 currentQty를 꺼낸다.

5. 추가 증거금 대상 수량 additionalMarginQty를 계산한다.
```

`additionalMarginQty` 분기:

```text
현재 포지션 없음
→ 주문 수량 전체가 추가 증거금 대상

현재 포지션과 같은 방향 주문
→ 포지션을 늘리므로 주문 수량 전체가 추가 증거금 대상

현재 포지션과 반대 방향 주문
→ 먼저 기존 포지션을 줄인다
→ 기존 포지션보다 많이 주문한 초과분만 추가 증거금 대상
```

예시:

```text
현재 LONG 3
SELL 5 주문

3개는 기존 LONG 청산
2개만 새 SHORT 진입
→ 추가 증거금 대상 수량 = 2
```

최종 계산:

```text
추가 필요 증거금 = 추가 증거금 대상 수량 × 기준가 / 계좌 레버리지
```

## 16. 증거금 부족 검사

파일: [`PaperOrderService.java`](../../src/main/java/com/example/futurespapertrading/paper/service/PaperOrderService.java)

`PaperOrderService` 클래스의 `placeOrder()` 메서드의 이 부분이다.

```java
if (requiredAdditionalMargin.compareTo(state.availableBalance()) > 0)
    return Mono.<OrderResponse>error(new InsufficientMarginException(
            "가용 증거금 부족: 필요 " + requiredAdditionalMargin + " > 가용 " + state.availableBalance()
                    + " (레버리지 " + state.account().leverage() + "x)"));
```

의미:

```text
requiredAdditionalMargin > availableBalance
→ 이번 주문을 열기 위한 증거금이 부족하다
→ 주문 생성 중단
→ InsufficientMarginException
```

통과하면 다음 단계로 간다.

```java
return placeOrderAfterMarginCheck(req, userId, state.account().leverage());
```

## 17. placeOrderAfterMarginCheck(...)

파일: [`PaperOrderService.java`](../../src/main/java/com/example/futurespapertrading/paper/service/PaperOrderService.java)

```java
private Mono<OrderResponse> placeOrderAfterMarginCheck(CreateOrderRequest req, Long userId, int leverage) {
    boolean isLimit = OrderType.LIMIT.name().equals(req.type());

    Optional<OrderBookSnapshot> maybeSnapshot = latestStore.latest();
    if (maybeSnapshot.isEmpty()) {
        if (!isLimit) {
            return Mono.error(new QuoteUnavailableException("호가 수신 전이라 체결할 수 없습니다."));
        }
        return saveOrder(req, userId, OrderStatus.OPEN.name(), BigDecimal.ZERO, List.of(), leverage);
    }

    PaperOrder probe = new PaperOrder(
            null, userId, req.symbol(),
            req.side(), req.type(), OrderStatus.NEW.name(),
            req.limitPrice(), req.quantity(), BigDecimal.ZERO, leverage);

    List<PaperFill> fills = engine.tryFill(probe, maybeSnapshot.get());
    BigDecimal filledQty = totalQuantity(fills);

    boolean fullyFilled = filledQty.compareTo(req.quantity()) >= 0;
    String status;
    if (isLimit)  status = fullyFilled ? OrderStatus.FILLED.name() : OrderStatus.OPEN.name();
    else          status = fills.isEmpty() ? OrderStatus.REJECTED.name() : OrderStatus.FILLED.name();

    return saveOrder(req, userId, status, filledQty, fills, leverage);
}
```

`PaperOrderService` 클래스의 `placeOrder()` 메서드에서 증거금 검사를 통과하면 이 부분으로 호출된다.

```java
return placeOrderAfterMarginCheck(req, userId, state.account().leverage());
```

이 메서드는 증거금 검증을 다시 하지 않는다.

여기서 하는 일은 3개다.

```text
1. 최신 호가 snapshot이 있는지 확인한다.
2. 호가가 있으면 체결 엔진으로 fills를 계산한다.
3. fills 결과로 주문 상태를 정하고 저장한다.
```

## 18. 호가 snapshot 없음 분기

파일: [`PaperOrderService.java`](../../src/main/java/com/example/futurespapertrading/paper/service/PaperOrderService.java)

```java
Optional<OrderBookSnapshot> maybeSnapshot = latestStore.latest();
if (maybeSnapshot.isEmpty()) {
    if (!isLimit) {
        return Mono.error(new QuoteUnavailableException("호가 수신 전이라 체결할 수 없습니다."));
    }
    return saveOrder(req, userId, OrderStatus.OPEN.name(), BigDecimal.ZERO, List.of(), leverage);
}
```

이 코드는 `PaperOrderService` 클래스의 `placeOrderAfterMarginCheck()` 메서드에서 `latestStore.latest()`로 최신 호가를 꺼낸 직후의 분기다.

분기 의미:

```text
호가 없음 + 시장가
→ 지금 체결 기준이 없다
→ 503 예외

호가 없음 + 지정가
→ 지금 체결 계산은 못 한다
→ 그래도 지정가 주문은 대기 주문으로 저장 가능
→ status = OPEN
→ filledQty = 0
→ fills = 빈 리스트
```

이 경우 프론트엔드 주문 목록에는 `OPEN` 대기 주문으로 보일 수 있다. DB에 주문이 저장되기 때문이다.

## 19. 체결 계산용 probe 주문

파일: [`PaperOrderService.java`](../../src/main/java/com/example/futurespapertrading/paper/service/PaperOrderService.java)

```java
PaperOrder probe = new PaperOrder(
        null, userId, req.symbol(),
        req.side(), req.type(), OrderStatus.NEW.name(),
        req.limitPrice(), req.quantity(), BigDecimal.ZERO, leverage);
```

`PaperOrderService` 클래스의 `placeOrderAfterMarginCheck()` 메서드에서 호가 snapshot이 있을 때 이 부분으로 내려온다.

`probe`는 DB에 저장할 최종 주문이 아니다.

목적:

```text
현재 호가에 이 주문을 대보면 얼마나 체결되는지
PaperTradingEngine에게 계산시키기 위한 임시 주문 객체
```

그래서:

```text
id = null
→ 아직 DB에 저장하지 않았으므로 id가 없다.

status = NEW
→ 계산용 임시 상태다.

filledQuantity = 0
→ 아직 체결 계산 전이다.
```

## 20. engine.tryFill(probe, snapshot)

파일: [`PaperTradingEngine.java`](../../src/main/java/com/example/futurespapertrading/paper/domain/PaperTradingEngine.java)

```java
public List<PaperFill> tryFill(PaperOrder order, OrderBookSnapshot snapshot) {
    OrderSide side = OrderSide.valueOf(order.side());
    OrderType type = OrderType.valueOf(order.type());

    List<OrderBookLevel> levels = (side == OrderSide.BUY)
            ? sortedAscending(snapshot.asks())
            : sortedDescending(snapshot.bids());

    List<PaperFill> fills = new ArrayList<>();
    BigDecimal remaining = order.quantity();

    for (OrderBookLevel level : levels) {
        if (remaining.signum() <= 0) break;

        if (type == OrderType.LIMIT) {
            BigDecimal limit = order.limitPrice();
            if (side == OrderSide.BUY && level.price().compareTo(limit) > 0) break;
            if (side == OrderSide.SELL && level.price().compareTo(limit) < 0) break;
        }

        BigDecimal take = remaining.min(level.quantity());

        fills.add(new PaperFill(
                null,
                order.id(),
                order.symbol(),
                order.side(),
                level.price(),
                take,
                FEE_ZERO
        ));

        remaining = remaining.subtract(take);
    }

    return fills;
}

private static List<OrderBookLevel> sortedAscending(List<OrderBookLevel> levels) {
    return levels.stream()
            .sorted(Comparator.comparing(OrderBookLevel::price))
            .toList();
}

private static List<OrderBookLevel> sortedDescending(List<OrderBookLevel> levels) {
    return levels.stream()
            .sorted(Comparator.comparing(OrderBookLevel::price).reversed())
            .toList();
}
```

`PaperOrderService` 클래스의 `placeOrderAfterMarginCheck()` 메서드에서 호출된다.

```java
List<PaperFill> fills = engine.tryFill(probe, maybeSnapshot.get());
```

흐름:

```text
1. 주문 side 문자열을 OrderSide enum으로 바꾼다.
2. 주문 type 문자열을 OrderType enum으로 바꾼다.
3. BUY 주문이면 asks를 싼 가격부터 본다.
4. SELL 주문이면 bids를 비싼 가격부터 본다.
5. remaining을 주문 수량으로 시작한다.
6. 호가 level을 하나씩 돈다.
7. remaining이 0 이하가 되면 멈춘다.
8. LIMIT 주문이면 limitPrice를 넘는 순간 멈춘다.
9. 현재 level에서 먹을 수량 take를 계산한다.
10. PaperFill을 만들어 fills에 추가한다.
11. remaining에서 take를 뺀다.
12. 최종 fills를 반환한다.
```

매수/매도별로 먹는 호가:

```text
BUY
→ 내가 사는 주문
→ 팔겠다는 사람의 호가인 asks를 먹는다
→ 싼 ask부터 먹는다

SELL
→ 내가 파는 주문
→ 사겠다는 사람의 호가인 bids를 먹는다
→ 비싼 bid부터 먹는다
```

지정가 제한:

```text
BUY LIMIT
→ limitPrice보다 비싼 ask는 먹지 않는다

SELL LIMIT
→ limitPrice보다 싼 bid는 먹지 않는다
```

## 21. totalQuantity(fills)

파일: [`PaperOrderService.java`](../../src/main/java/com/example/futurespapertrading/paper/service/PaperOrderService.java)

```java
private static BigDecimal totalQuantity(List<PaperFill> fills) {
    BigDecimal sum = BigDecimal.ZERO;
    for (PaperFill f : fills) sum = sum.add(f.quantity());
    return sum;
}
```

`PaperOrderService` 클래스의 `placeOrderAfterMarginCheck()` 메서드에서 호출된다.

```java
BigDecimal filledQty = totalQuantity(fills);
```

의미:

```text
fills에 들어있는 모든 체결 수량을 더한다.
그 결과가 이번 주문의 누적 체결 수량 filledQty다.
```

## 22. 주문 상태 결정

파일: [`PaperOrderService.java`](../../src/main/java/com/example/futurespapertrading/paper/service/PaperOrderService.java)

```java
boolean fullyFilled = filledQty.compareTo(req.quantity()) >= 0;
String status;
if (isLimit)  status = fullyFilled ? OrderStatus.FILLED.name() : OrderStatus.OPEN.name();
else          status = fills.isEmpty() ? OrderStatus.REJECTED.name() : OrderStatus.FILLED.name();
```

`PaperOrderService` 클래스의 `placeOrderAfterMarginCheck()` 메서드에서 `filledQty`를 구한 다음 이 부분으로 상태를 정한다.

상태 결정표:

| 상황 | 상태 |
|---|---|
| 지정가 + 전량 체결 | `FILLED` |
| 지정가 + 일부 체결 | `OPEN` |
| 지정가 + 미체결 | `OPEN` |
| 시장가 + 체결 있음 | `FILLED` |
| 시장가 + 체결 없음 | `REJECTED` |

핵심:

```text
지정가는 체결되지 않은 잔량을 대기 주문으로 남길 수 있다.
그래서 완전 체결이 아니면 OPEN이다.

시장가는 대기 주문으로 남기지 않는다.
체결이 0건이면 REJECTED, 1건 이상이면 FILLED다.
```

## 23. saveOrder(...)

파일: [`PaperOrderService.java`](../../src/main/java/com/example/futurespapertrading/paper/service/PaperOrderService.java)

```java
private Mono<OrderResponse> saveOrder(CreateOrderRequest req, Long userId,
                                      String status, BigDecimal filledQty, List<PaperFill> fills, int leverage) {
    PaperOrder toSave = new PaperOrder(
            null, userId, req.symbol(),
            req.side(), req.type(), status,
            req.limitPrice(), req.quantity(), filledQty, leverage);

    return orderRepository.save(toSave)
            .doOnNext(saved -> { if (OrderStatus.OPEN.name().equals(status)) openOrderCounter.increment(); })
            .flatMap(saved ->
            saveFills(saved.id(), fills)
                    .thenReturn(OrderResponse.from(saved, fills)));
}
```

`PaperOrderService` 클래스의 `placeOrderAfterMarginCheck()` 메서드 마지막에서 호출된다.

```java
return saveOrder(req, userId, status, filledQty, fills, leverage);
```

흐름:

```text
1. 저장용 PaperOrder toSave를 만든다.
2. orderRepository.save(toSave)로 paper_orders에 INSERT한다.
3. DB가 id를 채운 saved 주문을 반환한다.
4. status가 OPEN이면 openOrderCounter를 증가시킨다.
5. saved.id()를 가지고 saveFills(saved.id(), fills)를 호출한다.
6. `PaperOrderService` 클래스의 `saveFills()` 메서드가 끝나면 `OrderResponse` record의 `from(saved, fills)` 메서드를 호출해 반환한다.
```

여기서 저장되는 주문 상태는 앞 단계에서 이미 정해진 값이다.

```text
FILLED
OPEN
REJECTED
```

중 하나다.

`leverage`도 주문에 같이 저장된다.

```text
나중에 계좌 레버리지가 바뀌어도,
이 주문은 진입 당시 레버리지로 계산하기 위해서다.
```

## 24. saveFills(orderId, fills)

파일: [`PaperOrderService.java`](../../src/main/java/com/example/futurespapertrading/paper/service/PaperOrderService.java)

```java
private Mono<Void> saveFills(Long orderId, List<PaperFill> fills) {
    if (fills.isEmpty()) return Mono.empty();
    List<PaperFill> withOrderId = fills.stream()
            .map(f -> new PaperFill(null, orderId, f.symbol(),
                    f.side(), f.price(), f.quantity(), f.fee()))
            .toList();
    return fillRepository.saveAll(withOrderId).then();
}
```

`PaperOrderService` 클래스의 `saveOrder()` 메서드 안에서 호출된다.

```java
saveFills(saved.id(), fills)
        .thenReturn(OrderResponse.from(saved, fills))
```

흐름:

```text
1. fills가 비어 있으면 저장할 체결이 없으므로 Mono.empty()를 반환한다.
2. fills가 있으면 각 PaperFill에 orderId를 넣은 새 PaperFill을 만든다.
3. fillRepository.saveAll(withOrderId)로 paper_fills에 저장한다.
4. .then()으로 저장 결과 값은 버리고 완료 신호만 반환한다.
```

왜 주문을 먼저 저장해야 하는가:

```text
PaperFill에는 order_id가 필요하다.
order_id는 paper_orders에 주문을 저장해야 DB가 만들어준다.

그래서 순서가 반드시:
1. paper_orders 저장
2. saved.id() 획득
3. paper_fills 저장
```

## 25. fillRepository.saveAll(...)

파일: [`PaperFillRepository.java`](../../src/main/java/com/example/futurespapertrading/paper/repository/PaperFillRepository.java)

```java
public interface PaperFillRepository extends ReactiveCrudRepository<PaperFill, Long> {
    Flux<PaperFill> findByOrderId(Long orderId);

    @Query("SELECT f.* FROM paper_fills f JOIN paper_orders o ON f.order_id = o.id WHERE o.user_id = :userId ORDER BY f.id")
    Flux<PaperFill> findByUserIdOrderByIdAsc(Long userId);
}
```

`PaperOrderService` 클래스의 `saveFills()` 메서드에서 이 부분이다.

```java
return fillRepository.saveAll(withOrderId).then();
```

`saveAll(...)`은 이 인터페이스에 직접 적은 메서드는 아니다.

```java
public interface PaperFillRepository extends ReactiveCrudRepository<PaperFill, Long>
```

`ReactiveCrudRepository`를 상속했기 때문에 Spring Data가 제공한다.

create 흐름에서 쓰는 저장 메서드:

```text
fillRepository.saveAll(withOrderId)
→ paper_fills에 체결 기록들을 INSERT
```

## 26. OrderResponse record의 from(saved, fills) 메서드

파일: [`OrderResponse.java`](../../src/main/java/com/example/futurespapertrading/paper/dto/OrderResponse.java)

```java
public record OrderResponse(
        Long id,
        String symbol,
        String side,
        String type,
        String status,
        BigDecimal limitPrice,
        BigDecimal quantity,
        BigDecimal filledQuantity,
        BigDecimal avgPrice
) {
    public static OrderResponse from(PaperOrder order, List<PaperFill> fills) {
        return new OrderResponse(
                order.id(), order.symbol(), order.side(), order.type(),
                order.status(), order.limitPrice(), order.quantity(), order.filledQuantity(),
                averagePrice(fills));
    }

    private static BigDecimal averagePrice(List<PaperFill> fills) {
        if (fills.isEmpty()) return null;

        BigDecimal notional = BigDecimal.ZERO;
        BigDecimal totalQuantity = BigDecimal.ZERO;
        for (PaperFill f : fills) {
            notional = notional.add(f.price().multiply(f.quantity()));
            totalQuantity = totalQuantity.add(f.quantity());
        }

        if (totalQuantity.signum() == 0) return null;
        return notional.divide(totalQuantity, 8, RoundingMode.HALF_UP);
    }
}
```

`PaperOrderService` 클래스의 `saveOrder()` 메서드 마지막 응답 생성 코드다.

```java
.thenReturn(OrderResponse.from(saved, fills))
```

흐름:

```text
1. 저장된 주문 saved를 받는다.
2. 체결 목록 fills를 받는다.
3. 주문 필드를 응답 필드로 옮긴다.
4. fills로 평균 체결가 avgPrice를 계산한다.
5. OrderResponse를 만든다.
```

`OrderResponse` record의 `averagePrice(fills)` 메서드:

```text
체결이 없으면 null

체결이 있으면:
avgPrice = Σ(체결가 × 체결수량) / Σ체결수량
```

예:

```text
100원에 1개 체결
200원에 9개 체결

단순 평균 = 150
가중 평균 = (100×1 + 200×9) / 10 = 190
```

그래서 평균 체결가는 단순 평균이 아니라 수량 가중 평균이다.

## 27. create 요청의 최종 결과

정상 흐름은 `OrderResponse` 하나를 반환한다.

컨트롤러에 이 애너테이션이 있으므로:

```java
@ResponseStatus(HttpStatus.CREATED)
```

성공 응답은 `201 Created`다.

주요 결과는 이렇다.

| 상황 | DB 저장 | 응답 상태 |
|---|---|---|
| 시장가 체결 있음 | 주문 `FILLED`, fills 저장 | 201 |
| 시장가 체결 없음 | 주문 `REJECTED`, fills 없음 | 201 |
| 지정가 전량 체결 | 주문 `FILLED`, fills 저장 | 201 |
| 지정가 일부 체결 | 주문 `OPEN`, fills 저장 | 201 |
| 지정가 미체결 | 주문 `OPEN`, fills 없음 | 201 |
| 지정가인데 호가 없음 | 주문 `OPEN`, fills 없음 | 201 |
| 시장가인데 mark 없음 | 저장 없음 | 503 |
| 시장가인데 호가 snapshot 없음 | 저장 없음 | 503 |
| 증거금 부족 | 저장 없음 | 400 |
| DTO 검증 실패 | 컨트롤러 본문 실행 전 중단 | 400 |
| 비로그인 | 컨트롤러 도달 전 중단 | 401 |

## 28. 한 번에 다시 읽기

```text
PaperOrderController 클래스의 create(req) 메서드
→ req는 CreateOrderRequest이고 @Valid로 먼저 검증된다.
→ `PaperOrderController` 클래스의 `currentUserId()` 메서드로 현재 로그인 사용자의 userId를 구한다.
→ userId가 나오면 `PaperOrderService` 클래스의 `placeOrder(req, userId)` 메서드를 호출한다.

PaperOrderService 클래스의 placeOrder(req, userId) 메서드
→ `PortfolioService` 클래스의 `accountState(userId)` 메서드로 계좌 상태를 만든다.
→ 시장가인데 mark가 없으면 거부한다.
→ `PaperOrderService` 클래스의 `requiredAdditionalMargin(req, state)` 메서드로 필요 증거금을 계산한다.
→ 가용잔고보다 필요 증거금이 크면 거부한다.
→ 통과하면 `PaperOrderService` 클래스의 `placeOrderAfterMarginCheck(...)` 메서드로 간다.

PaperOrderService 클래스의 placeOrderAfterMarginCheck(...) 메서드
→ latestStore.latest()로 최신 호가 snapshot을 본다.
→ 호가가 없고 시장가면 거부한다.
→ 호가가 없고 지정가면 OPEN 주문으로 저장한다.
→ 호가가 있으면 probe 주문을 만든다.
→ `PaperTradingEngine` 클래스의 `tryFill(probe, snapshot)` 메서드로 fills를 계산한다.
→ `PaperOrderService` 클래스의 `totalQuantity(fills)` 메서드로 filledQty를 계산한다.
→ 주문 상태를 FILLED / OPEN / REJECTED 중 하나로 정한다.
→ `PaperOrderService` 클래스의 `saveOrder(...)` 메서드로 주문을 저장한다.
→ `PaperOrderService` 클래스의 `saveFills(...)` 메서드로 체결을 저장한다.
→ `OrderResponse` record의 `from(saved, fills)` 메서드로 응답을 만든다.
```

이 문서에서 기준은 하나다.

```text
`PaperOrderController` 클래스의 `create()` 메서드에서 보이는 내가 만든 코드가 무엇을 호출하는지,
그 호출 내부에서 다시 내가 만든 코드가 무엇을 호출하는지,
그 순서를 끝까지 따라간다.
```
