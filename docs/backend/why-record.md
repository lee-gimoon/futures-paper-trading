# 공부 노트: PaperOrder는 왜 record인가 — 불변 객체, 복제 비용, GC

작성일: 2026-06-11

출발 질문은 이거였다.

> `placeOrder` 한 번에 PaperOrder를 `probe`로 만들고, `toSave`로 또 만들고…
> **객체를 이렇게 계속 만들어도 지장 안 생기나? 왜 setter로 고치지 않고 굳이 record로 만들어서 매번 새로 만드나?
> 그럼 그 버려진 객체들은 GC가 언제 치우나?**

이 노트는 그 세 질문에 대한 답을 순서대로 쌓아 올린 정리다.

---

## 한 줄 요약

```text
record를 쓰는 이유   = 불변(immutable)이 주는 보장 — "아무도 못 바꾼다"
그 선택의 대가       = 수정 대신 복제 → 객체가 더 생긴다
그 대가의 실제 크기  = JVM에선 사실상 0 (생성은 포인터 한 번, 일찍 죽는 객체는 GC 비용도 0)
```

대가가 거의 없는데 보장은 확실하다 → 그래서 record를 쓴다. 이게 결론이고, 아래는 그 근거다.

---

## 1. 출발 장면 — 주문 1건에 PaperOrder가 몇 번 태어나나

[PaperOrderService.placeOrder](../../src/main/java/com/example/futurespapertrading/paper/service/PaperOrderService.java:60)를 따라가면 같은 주문 내용이 **PaperOrder로만 세 번** 새로 태어난다.

```text
CreateOrderRequest (입력 DTO — record지만 PaperOrder 아님, 출발 재료일 뿐)
   │
   ▼  ① 엔진에 떠볼 입력으로 '번역'
PaperOrder probe    ← new 1회차  (status=NEW 자리만, id=null)
   │
   ▼  ② 체결 계산 결과로 status·filledQty 확정
PaperOrder toSave   ← new 2회차  (probe를 고치지 않고 새로 만듦)
   │
   ▼  ③ 저장 후 DB가 매긴 id가 채워진 저장본
PaperOrder saved    ← new 3회차  (save()가 돌려주는 또 다른 새 객체)
```

(이 흐름 주변에서 `PaperFill` 복제본이나 `OrderResponse` 같은 **다른** record들도 같은 식으로 새로 만들어지지만, 헷갈리지 않게 이 노트는 PaperOrder 세 번에만 집중한다. 다른 record에도 논리는 똑같이 적용된다.)

setter가 있었다면 ①에서 만든 객체 하나를 ②~③에서 고쳐 쓰면 될 일이다.
"한 번 만들고 고치면 될 걸 왜 세 번 만들지?"가 자연스러운 의문인데, 답은 두 단계다 —
**(A) 매번 새로 만드는 쪽이 버그를 구조적으로 막아주고, (B) 그 비용이 사실상 공짜라서.**

---

## 2. record는 '값'이다 — 상태를 가진 기계가 아니라

자바의 클래스는 쓰임새가 크게 두 부류다.

- **상태를 가진 기계** — 살아 움직이며 내부 상태가 변하는 것. 예: `PaperOrderService`(의존성을 들고 일하는 일꾼), DB 커넥션.
- **값(value)** — 한번 정해지면 그 자체로 완결된 데이터. 예: 숫자 `42`, `"BTCUSDT"`, 그리고 **주문 1건의 기록**.

`PaperOrder`는 후자다. "2026-06-11에 user 7이 BTCUSDT 0.5개 매수 주문을 냈고 FILLED됐다"는 **사실의 기록**이지, 시간이 지나며 스스로 변하는 기계가 아니다. 값은 고치는 게 아니라 **새 값으로 갈아끼우는** 것이다 — `int x = 1; x = x + 1;`에서 숫자 1 자체를 2로 고치는 게 아니라 새 값 2를 대입하듯이.

record는 정확히 이 '값'을 위한 문법이다. 예전엔 값 클래스를 만들려면 `final` 필드 + 생성자 + getter + `equals`/`hashCode`/`toString`을 전부 손으로 써야 했는데, record는 그 보일러플레이트를 선언 한 줄로 줄였다. 그리고 **setter는 아예 만들 수 없다** — 실수로라도 못 바꾼다는 게 언어 차원에서 보장된다.

핵심 한 줄: **record를 골랐다 = "이 객체는 값이다, 변하지 않는다"를 컴파일러에게 선언한 것.**

---

## 3. 불변이 사주는 보장 세 가지

성능 때문이 아니다. 불변은 **버그가 생길 수 있는 통로 자체를 막는다.**

### 3-1. 엔진의 '순수함'이 진짜로 지켜진다

[PaperTradingEngine](../../src/main/java/com/example/futurespapertrading/paper/domain/PaperTradingEngine.java)은 "입력만 보고 답을 내는 순수 계산기"다. 그런데 `probe`에 setter가 있다면? `engine.tryFill(probe, snapshot)`을 부르는 순간부터 "엔진이 안에서 `probe.setStatus(...)`를 해버린 건 아닐까?"를 **항상 의심해야** 한다. 순수함이 약속이 아니라 희망사항이 된다.

record면 의심 자체가 불가능하다. 넘긴 입력이 안 변한다는 건 코드 리뷰로 확인하는 게 아니라 **타입이 보장**한다.

### 3-2. 객체의 '이름값'이 끝까지 유지된다

`probe`는 끝까지 탐색용이고, `toSave`는 정확히 저장된 그 값이고, `saved`는 id까지 박힌 저장본이다. 변수 이름이 곧 그 객체의 상태를 말해준다.

가변 객체 하나로 돌려썼다면 그 객체가 probe였다가 toSave로 '변신'하므로, 코드 중간 어느 줄을 읽든 "**지금 이 시점에** status가 뭐지?"를 위에서부터 추적해야 한다. 불변이면 추적이 필요 없다 — 만들어진 순간의 값이 곧 영원한 값이니까.

### 3-3. WebFlux 멀티스레드에서 락 없이 안전하다

이건 이 프로젝트에 살아있는 예가 있다. [PaperOrderService.java:66](../../src/main/java/com/example/futurespapertrading/paper/service/PaperOrderService.java:66)의 주석 그대로 —

> 여기서 받아간 snapshot은 그 시점에 박제된 불변 객체 — 아래 tryFill 계산 도중 store가 100ms마다 새 snapshot으로 교체돼도, 이 주문은 끝까지 같은 시점 호가 기준으로 계산된다.

`LatestOrderBookSnapshotStore`는 다른 스레드에서 쉴 새 없이 최신 호가로 갈아끼우는 중이다. 그런데 `OrderBookSnapshot`이 불변이라, 주문 계산 스레드가 받아간 snapshot은 계산 도중 절대 안 변한다. 락도, `synchronized`도 필요 없다.

만약 snapshot이 가변이고 store가 "교체" 대신 "내용 수정"을 했다면 — 계산 절반은 옛 호가, 절반은 새 호가로 섞이는 **찢어진 읽기(torn read)** 가 가능해진다. 체결 가격이 미묘하게 틀어지는, 재현도 안 되는 최악 부류의 버그다. 불변은 이 부류를 통째로 없앤다.

핵심 한 줄: **불변 = "공유해도 안전"의 다른 말. 갈아끼우기(swap)는 한 동작이라 중간 상태가 노출될 틈이 없다.**

---

## 4. 대가 — 수정 대신 '복제+수정'

물론 공짜 점심은 없다. 불변이라 **고칠 일이 생기면 새로 만들어야** 한다. 1절의 'new 2회차'가 정확히 그것이다.

[saveOrder](../../src/main/java/com/example/futurespapertrading/paper/service/PaperOrderService.java:145)에서 — 체결 계산이 끝나 status·filledQty가 확정돼도, 이미 만든 `probe`의 그 두 칸을 **못 고친다**(record니까). 그래서 확정값만 갈아끼운 새 PaperOrder를 만든다:

```java
PaperOrder toSave = new PaperOrder(
        null, userId, null, req.symbol(),
        req.side(), req.type(), status,            // ← probe와 다른 칸은
        req.limitPrice(), req.quantity(), filledQty); // status·filledQty 둘뿐
```

생성자를 다시 부르며 바꿀 칸만 갈아끼우는 이 모양이 불변 객체의 표준 수정법이다. 같은 패턴이 [saveFills](../../src/main/java/com/example/futurespapertrading/paper/service/PaperOrderService.java:165)에도 반복된다 — `PaperFill`(PaperOrder와는 다른 record)도 주문 id가 생긴 뒤 기존 fill의 orderId를 못 고쳐서, orderId만 박은 새 fill로 복제한다. (코틀린은 `copy(status = ...)`라는 전용 문법이 있고, 자바 record에도 비슷한 문법(withers)이 논의 중이지만 아직은 생성자 재호출이 정석.)

그래서 질문이 "이 복제 비용을 감당할 수 있나?"로 좁혀진다. 답은 다음 절 — **감당하고 말 것도 없이 거의 0이다.**

---

## 5. 비용 ①: 객체 '생성'은 포인터 한 번이다

JVM 힙에는 새 객체가 태어나는 **Eden**이라는 영역이 있고, 그 안에 "다음 빈 자리" 포인터가 하나 있다. `new PaperOrder(...)`가 하는 일은:

```text
   Eden ┌──────────────────────────────────────────┐
        │ ████ 이미 쓴 자리 ████ │ 빈 자리           │
        └────────────────────────▲─────────────────┘
                                 │
                       "다음 빈 자리" 포인터
                                 │
   new = 이 포인터를 객체 크기만큼 오른쪽으로 밀고, 그 자리에 필드 값 채우기. 끝.
```

빈 자리를 '찾아다니는' 과정이 없다 — 포인터 하나 더하기라 **수 나노초**다. 이걸 bump-the-pointer 할당이라 부르고, JVM은 초당 수백만~수천만 개의 작은 객체 할당을 전제로 설계돼 있다.

그리고 사실, 모르는 사이에 이미 똑같은 일을 훨씬 많이 하고 있었다:

- `BigDecimal`은 불변이라 [totalQuantity](../../src/main/java/com/example/futurespapertrading/paper/service/PaperOrderService.java:181)의 `sum = sum.add(f.quantity())`는 **루프 돌 때마다 새 BigDecimal**을 만든다.
- `String`도 불변이라 문자열 `+` 한 번이 새 String 한 개다.
- `List.of()`, `stream().toList()`, Reactor의 `Mono`/`Flux` 체인 — 전부 단계마다 새 객체.

주문 1건당 PaperOrder 세 개는 그 바다에 물방울 하나 얹는 정도다.

핵심 한 줄: **"객체 생성은 비싸다"는 20년 전 직감이다. 현대 JVM에서 작은 객체 할당은 사실상 공짜다.**

---

## 6. 비용 ②: GC — '죽는 시점'과 '치워지는 시점'은 다르다

"만든 후에 GC되는 건가?"라는 질문엔 두 시점을 갈라야 답이 된다.

### 죽는 시점 — 마지막 참조가 끊길 때 (unreachable)

GC는 객체에 수명 타이머를 달지 않는다. 기준은 단 하나, **도달 가능성(reachability)** — 살아있는 변수·필드에서 따라갈 수 있느냐다.

`placeOrder`가 끝나고 응답이 클라이언트로 나가면, `probe`·`toSave`·`saved`를 가리키던 지역변수들이 전부 사라진다. 그 순간 이 PaperOrder들은 unreachable — **"수거 대상" 자격**을 얻는다. 만든 직후가 아니라, 마지막 참조가 끊기는 순간이다.

### 치워지는 시점 — Eden이 꽉 찰 때 (minor GC)

자격을 얻었다고 바로 치워지는 것도 아니다. GC는 Eden이 꽉 차면 그제서야 한 번 돈다(minor GC). 그리고 여기에 이 노트에서 제일 중요한 반전이 있다 —

**GC는 죽은 객체를 찾아 지우지 않는다. 산 객체만 찾아 옮긴다.**

```text
minor GC 한 사이클:

   Eden ┌─────────────────────────────────┐
        │ 죽음 죽음 [생존] 죽음 죽음 [생존] 죽음 │   ① 살아있는 객체만 찾아서
        └──────────┬───────────────┬──────┘      Survivor 영역으로 복사
                   ▼               ▼
   Survivor  ┌──[생존]──[생존]──────────┐         ② Eden은 통째로 리셋
             └─────────────────────────┘            (빈 자리 포인터를 맨 앞으로)

   죽은 객체들은? → 아무도 안 건드림. 영역째 쓸려나감. 개당 비용 0.
```

즉 **GC 비용은 '살아남은 객체 수'에 비례하지, '죽은 객체 수'와 무관하다.** `probe`처럼 한 요청 안에서 태어나 응답 나가기 전에 죽는 객체는, minor GC가 돌 때 그냥 무시되고 Eden과 함께 사라진다. 만들 때 포인터 한 번, 치울 때 0 — 왕복 전부 공짜에 가깝다.

JVM이 이렇게 설계된 근거가 **세대 가설(generational hypothesis)** 이다: "대부분의 객체는 태어나자마자 죽는다." 수십 년의 실측으로 확인된 경험칙이고, 요청 하나 안에서 태어나 죽는 `probe`·`toSave`는 정확히 이 가설의 모범 사례다. record를 계속 만들고 버리는 지금 코드는 JVM의 설계 전제에 **올라타는** 패턴이지 거스르는 패턴이 아니다.

(반대로 JVM이 싫어하는 건 '어중간하게 오래 사는' 객체다 — minor GC를 여러 번 버티면 Old 영역으로 승격되고, Old가 차면 비용이 큰 full GC가 필요해진다. 요청 수명 객체는 그 근처에도 안 간다.)

핵심 한 줄: **일찍 죽는 객체는 GC가 '치우는' 게 아니라 '무시하고 영역째 쓸어버리는' 것이라 비용이 0이다.**

---

## 7. 그럼 setter(가변)가 맞는 경우는 없나

균형을 위해 — 있다. 다만 이 프로젝트와는 거리가 멀다.

- **거대 객체를 극한 빈도로 재사용할 때** — 게임 엔진의 좌표 벡터, 대규모 수치 연산 버퍼처럼 초당 수백만 번 도는 핫루프. 여기선 객체 풀(pool)을 만들어 가변 객체를 재사용하기도 한다. 단, 이건 **측정으로 병목이 증명된 뒤에** 하는 최적화다.
- **프레임워크가 요구할 때** — 전통 JPA는 기본 생성자 + setter 스타일 엔티티를 요구했다. 반면 이 프로젝트의 Spring Data R2DBC는 record를 그대로 매핑해주므로 해당 없음.

기본값은 불변, 가변은 증명된 예외 — 가 현대 자바의 합의다. Effective Java의 유명한 항목 제목이 그대로 "변경 가능성을 최소화하라(Minimize mutability)"다.

---

## 핵심 정리

```text
① PaperOrder는 '값'(사실의 기록)이지 '상태 기계'가 아니다 → 값의 문법인 record가 맞다.
② 불변이 사는 것: 엔진의 순수함 보장 / probe·toSave의 의미 고정 / 멀티스레드 무방비 공유.
③ 불변이 내는 값: 수정 대신 복제(생성자 재호출) → 객체가 더 생긴다.
④ 그런데 생성은 Eden 포인터 한 번(수 ns), BigDecimal·String으로 이미 하고 있던 일.
⑤ GC는 만든 직후가 아니라 참조가 끊긴 뒤(unreachable) + Eden이 꽉 찰 때(minor GC) 돈다.
⑥ 그마저도 산 객체만 복사하고 영역째 리셋 → 일찍 죽는 객체의 수거 비용은 0 (세대 가설).
∴ 대가 ≈ 0, 보장은 확실 → record로 매번 새로 만든다.
```
