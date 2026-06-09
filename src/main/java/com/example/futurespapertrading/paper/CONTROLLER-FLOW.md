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
1. POST /api/paper/orders 요청이 들어온다.
2. WebFlux가 요청 body를 CreateOrderRequest로 만들고 @Valid 검증을 한다.
3. 검증을 통과하면 PaperOrderController.create(req)가 호출된다.
4. create() 안에서 currentUserId()가 호출된다.
5. currentUserId()는 아래 흐름을 담은 Mono<Long> 파이프라인 객체를 만든다.
   SecurityContext 조회 → email 추출 → userRepository.findByEmail(email) → User::id
6. 그 뒤 .flatMap(userId -> orderService.placeOrder(req, userId))가 붙으면서 새 Mono<OrderResponse> 객체가 만들어진다.
7. create()는 이 Mono<OrderResponse> 객체를 즉시 return한다.
   이 시점에는 아직 currentUserId() 내부 조회도, 주문 처리도, DB 저장도 실행되지 않았다.
8. WebFlux가 컨트롤러가 반환한 Mono<OrderResponse>를 구독한다.
9. 구독 이후 currentUserId() 내부 로직이 실제 실행된다.
10. ReactiveSecurityContextHolder.getContext()에서 인증 정보를 꺼낸다.
11. 인증 정보에서 email을 추출한다.
12. userRepository.findByEmail(email)이 구독되고, 실제 DB 조회가 실행된다.
13. 조회된 User에서 id를 꺼내 userId가 emit된다.
14. userId가 흐르면 바깥쪽 flatMap이 람다를 호출한다.
15. 람다 안에서 orderService.placeOrder(req, userId)가 호출된다.
16. 현재 placeOrder()는 전체가 Mono.defer(...)로 감싸져 있지 않기 때문에, 호출 즉시 일반 자바 코드가 실행된다.
    latestStore.latest() → engine.tryFill(...) → filledQty 계산 → status 계산
17. placeOrder()는 마지막에 orderRepository.save(...).flatMap(...) 형태의 Mono<OrderResponse>를 만들어 반환한다.
18. 바깥쪽 flatMap이 placeOrder()가 반환한 inner Mono<OrderResponse>를 바로 구독한다.
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
1. GET /api/paper/orders 요청이 들어온다.
2. Security filter가 인증 여부를 확인한다.
3. PaperOrderController.list()가 호출된다.
4. list() 안에서 currentUserId()가 호출된다.
5. currentUserId()는 Mono<Long> 파이프라인 객체를 만든다.
6. .flatMapMany(orderService::listOrders)가 붙으면서 Flux<OrderResponse> 객체가 만들어진다.
7. list()는 이 Flux<OrderResponse> 객체를 즉시 return한다.
   이 시점에는 아직 유저 조회도, 주문 목록 조회도 실행되지 않았다.
8. WebFlux가 컨트롤러가 반환한 Flux<OrderResponse>를 구독한다.
9. 구독 이후 currentUserId() 내부 로직이 실제 실행된다.
10. 인증 정보에서 email을 추출한다.
11. userRepository.findByEmail(email)이 구독되고, 실제 DB 조회가 실행된다.
12. 조회된 User에서 id를 꺼내 userId가 emit된다.
13. userId가 흐르면 flatMapMany가 orderService.listOrders(userId)를 호출한다.
14. listOrders()는 orderRepository.findByUserIdOrderByIdDesc(userId).map(...) 형태의 Flux<OrderResponse>를 만들어 반환한다.
15. flatMapMany가 listOrders()가 반환한 inner Flux<OrderResponse>를 구독한다.
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

실행 흐름:
1. 요청
2. 컨트롤러 메서드 호출
3. Mono/Flux 조립
4. Mono/Flux 반환
5. WebFlux 구독
6. upstream 값 emit
7. flatMap/flatMapMany가 람다 또는 메서드 참조 호출
8. inner Mono/Flux 반환
9. inner Mono/Flux 구독
10. 실제 DB/비즈니스 작업 실행
```
