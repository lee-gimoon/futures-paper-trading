# Paper 컨트롤러 엔드포인트 실행 흐름

엔드포인트별로 **요청 → 컨트롤러 호출 → Mono/Flux 반환 → WebFlux 구독 → 실제 실행** 순서만 정리합니다.

---

## POST /api/paper/orders

대상 코드:

```java
return currentUserId().flatMap(userId -> orderService.placeOrder(req, userId));
```

실행 흐름:

```text
──── ① 요청 수신 ────────────────────────────────────────────

1. POST /api/paper/orders 요청이 들어온다.

2. WebFlux가 요청 body를 CreateOrderRequest로 만들고 @Valid 검증을 한다.

3. 검증을 통과하면 PaperOrderController.create(req)가 호출된다.

──── ② 조립: 파이프라인 설계도만 만든다 (실행은 아직 0줄) ────

4. create() 안에서 currentUserId()가 호출된다.

5. currentUserId()는 아래 흐름을 담은 Mono<Long> 파이프라인 객체를 만든다.
   SecurityContext 조회 → email 추출 → userRepository.findByEmail(email) → User::id
   (흐름을 '담기만' 했지, 조회는 아직 실행되지 않았다)

6. 그 뒤 .flatMap(userId -> orderService.placeOrder(req, userId))가 붙으면서 새 Mono<OrderResponse> 객체가 만들어진다.
   · 한 줄이지만 실제로는 두 단계다 (자바는 메서드 인자를 먼저 평가하므로):
     (a) 람다식 평가 → 람다 '객체' 생성
         · 람다 객체 = Function 인터페이스 구현 객체. 본문(placeOrder 호출)은 apply(userId) 메서드의
           구현이 되고, req는 필드처럼 캡처돼 객체 안에 저장된다.
         · 본문이 지금 안 도는 건 구독 같은 특별한 장치가 있어서가 아니라,
           '객체 생성과 메서드 호출은 별개의 사건'이라는 자바 기본 규칙 때문이다.
           14번에서 flatMap이 보내는 '신호'의 정체도 apply(userId)라는 평범한 메서드 호출이다.
     (b) .flatMap(람다객체) 메서드 호출
         └→ new MonoFlatMap(upstream, 람다객체) 생성·반환
            = 이것이 6번의 "새 Mono<OrderResponse>". upstream 참조와 람다 객체만 든 '계획 껍데기'라
              placeOrder() 실행 없이 만들어진다 — placeOrder()가 리턴하는 inner Mono(17번)와는 별개 객체.
   · 타입이 Mono<OrderResponse>인 건 실행의 결과가 아니라 컴파일러의 추론이다:
     flatMap 선언이 "Function<T, Mono<R>>를 받아 Mono<R>를 돌려준다"이고 람다의 반환 타입이
     Mono<OrderResponse>이므로, 코드만 보고 R = OrderResponse로 계산해 미리 붙인 '라벨'일 뿐이다.

7. create()는 이 Mono<OrderResponse> 객체를 즉시 return한다.
   이 시점에는 아직 currentUserId() 내부 조회도, 주문 처리도, DB 저장도 실행되지 않았다.

──── ③ 구독: 단 한 번의 외부 subscribe ──────────────────────

8. WebFlux가 컨트롤러가 반환한 Mono<OrderResponse>를 구독한다.
   구독 신호는 ②에서 연결해 둔 바깥 체인을 따라 위로 전파된다(flatMap → currentUserId 쪽).

──── ④ 바깥 체인 실행: userId가 만들어져 흐른다 ──────────────

9. 구독 이후 currentUserId() 내부 로직이 실제 실행된다.

10. ReactiveSecurityContextHolder.getContext()에서 인증 정보를 꺼낸다.

11. 인증 정보에서 email을 추출한다.

12. userRepository.findByEmail(email)이 구독되고, 실제 DB 조회가 실행된다.

13. 조회된 User에서 id를 꺼내 userId가 emit된다.

──── ⑤ 람다 호출 + inner 구독: onNext(userId)가 도착한 순간 한 호흡에 ────

14. userId가 흐르면 바깥쪽 flatMap이 6번에서 저장해 둔 람다를 그제서야 '호출'한다.

15. 람다 본문에서 orderService.placeOrder(req, userId)가 호출된다.

16. 현재 placeOrder()는 전체가 Mono.defer(...)로 감싸져 있지 않기 때문에, 호출 즉시 일반 자바 코드가 실행된다.
    latestStore.latest() → engine.tryFill(...) → filledQty 계산 → status 계산

17. placeOrder()는 마지막에 orderRepository.save(...).flatMap(...) 형태의 inner Mono<OrderResponse>를 만들어 반환한다.
    inner Mono는 이 순간(런타임) 처음 생긴 새 파이프라인이다 — ② 조립 때는 존재하지 않았다.

18. 바깥쪽 flatMap이 그 inner Mono<OrderResponse>를 바로 구독한다.
    · 8번의 WebFlux 구독은 ②에서 연결돼 있던 바깥 체인으로만 전파되므로,
      런타임에 생긴 inner까지는 닿을 연결 자체가 없다.
    · Mono는 구독 없이는 실행되지 않으니, inner를 받아 든 유일한 존재인 flatMap이 직접 subscribe()를 건다.
      이렇게 '안쪽을 구독해 결과를 자기 것처럼 펼쳐 보내는 것(flatten)'이 flatMap의 존재 이유다.

──── ⑥ inner 체인 실행 → 응답 ───────────────────────────────

19. inner Mono가 구독되면서 orderRepository.save(...)의 실제 DB INSERT가 실행된다.

20. 주문 저장 결과로 saved 주문이 emit된다.

21. saveFills(saved.id(), fills)가 호출된다.

22. fill이 있으면 fillRepository.saveAll(...)이 구독되고, 실제 DB INSERT가 실행된다.

23. fill 저장까지 끝나면 OrderResponse.from(saved, fills)로 응답 DTO를 만든다.

24. OrderResponse가 emit된다.

25. WebFlux가 OrderResponse를 JSON으로 변환해 HTTP 201 Created 응답을 쓴다.
```

한 줄 요약:

```text
create()는 Mono를 만들어 반환만 한다.
WebFlux가 subscribe한 뒤 currentUserId가 실행된다.
userId가 나오면 flatMap이 람다를 호출한다.
람다가 placeOrder()를 호출하는 순간 engine 계산은 즉시 실행된다.
placeOrder()가 반환한 inner Mono를 flatMap이 구독하면서 DB 저장이 실행된다.
```

---

## GET /api/paper/orders

대상 코드:

```java
return currentUserId()
        .flatMapMany(orderService::listOrders);
```

실행 흐름:

```text
──── ① 요청 수신 ────────────────────────────────────────────

1. GET /api/paper/orders 요청이 들어온다.

2. Security filter가 인증 여부를 확인한다.

3. PaperOrderController.list()가 호출된다.

──── ② 조립: 파이프라인 설계도만 만든다 (실행은 아직 0줄) ────

4. list() 안에서 currentUserId()가 호출된다.

5. currentUserId()는 Mono<Long> 파이프라인 객체를 만든다.

6. .flatMapMany(orderService::listOrders)가 붙으면서 Flux<OrderResponse> 객체가 만들어진다.
   (메서드 참조도 람다처럼 이때 '객체'로 저장만 된다 — listOrders 호출은 13번에서)

7. list()는 이 Flux<OrderResponse> 객체를 즉시 return한다.
   이 시점에는 아직 유저 조회도, 주문 목록 조회도 실행되지 않았다.

──── ③ 구독: 단 한 번의 외부 subscribe ──────────────────────

8. WebFlux가 컨트롤러가 반환한 Flux<OrderResponse>를 구독한다.

──── ④ 바깥 체인 실행: userId가 만들어져 흐른다 ──────────────

9. 구독 이후 currentUserId() 내부 로직이 실제 실행된다.

10. 인증 정보에서 email을 추출한다.

11. userRepository.findByEmail(email)이 구독되고, 실제 DB 조회가 실행된다.

12. 조회된 User에서 id를 꺼내 userId가 emit된다.

──── ⑤ 메서드 참조 호출 + inner 구독 ────────────────────────

13. userId가 흐르면 flatMapMany가 orderService.listOrders(userId)를 호출한다.

14. listOrders()는 orderRepository.findByUserIdOrderByIdDesc(userId).map(...) 형태의 inner Flux<OrderResponse>를 만들어 반환한다.

15. flatMapMany가 listOrders()가 반환한 inner Flux<OrderResponse>를 구독한다.
    · 이유는 POST 18번과 동일 — inner는 런타임에 생긴 새 파이프라인이라 연산자가 직접 구독해야 실행된다.

──── ⑥ inner 체인 실행 → 응답 ───────────────────────────────

16. inner Flux가 구독되면서 orderRepository.findByUserIdOrderByIdDesc(userId)의 실제 DB SELECT가 실행된다.

17. 조회된 각 PaperOrder를 OrderResponse.from(order, List.of())로 변환한다.

18. OrderResponse들이 emit된다.

19. WebFlux가 결과를 JSON으로 변환해 HTTP 응답을 쓴다.
```

한 줄 요약:

```text
list()는 Flux를 만들어 반환만 한다.
WebFlux가 subscribe한 뒤 currentUserId가 실행된다.
userId가 나오면 flatMapMany가 listOrders()를 호출한다.
listOrders()가 반환한 inner Flux를 flatMapMany가 구독하면서 주문 목록 DB 조회가 실행된다.
```

---

## 다음 엔드포인트 추가 형식

새 엔드포인트가 생기면 아래에 같은 방식으로 추가합니다.

```text
## METHOD /path

대상 코드:

실행 흐름 (6개 국면으로 나눠 적는다):

── ① 요청 수신 ──
요청 → 검증/필터 → 컨트롤러 메서드 호출

── ② 조립 (실행 0줄) ──
Mono/Flux 파이프라인 조립 → 람다/메서드 참조는 '객체'로 저장만 됨 → 즉시 return

── ③ 구독 ──
WebFlux의 단 한 번 subscribe → 구독 신호가 바깥 체인 위로 전파

── ④ 바깥 체인 실행 ──
upstream 로직 실제 실행 → 값 emit

── ⑤ 람다 호출 + inner 구독 ──
flatMap/flatMapMany가 저장해 둔 람다 호출 → inner Mono/Flux 생성·반환 → 연산자가 inner를 직접 구독

── ⑥ inner 체인 실행 → 응답 ──
실제 DB/비즈니스 작업 실행 → 결과 emit → HTTP 응답
```
