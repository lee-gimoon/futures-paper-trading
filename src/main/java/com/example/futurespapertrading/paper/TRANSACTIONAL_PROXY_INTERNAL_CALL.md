## Spring `@Transactional`과 Proxy 내부 호출

### 1. `@Transactional`이 있으면 Spring은 어떻게 처리하는가?

예를 들어:
```java
@Service
public class PaperOrderService {

    public Mono<?> placeOrder() {
        return saveOrder();
    }

    @Transactional
    public Mono<?> saveOrder() {
        ...
    }
}
```

처럼 트랜잭션 대상 메서드가 있는 Bean을 Spring이 관리하고 트랜잭션 기능이 활성화되어 있다면, Spring은 **실제&#x20;****`PaperOrderService`****&#x20;객체(target)를 감싸는 Proxy 객체**를 만들어 외부에 노출한다.
```text
Spring Container

Proxy 객체
   │
   └── 실제 PaperOrderService 객체(target)
```

Controller나 다른 Bean에 `PaperOrderService`를 의존성 주입하면 일반적으로 실제 객체를 직접 사용하는 것이 아니라 **Proxy를 통해 사용하게 된다.**
```text
Controller의 paperOrderService
          ↓
        Proxy
          ↓
실제 PaperOrderService 객체
```

---

### 2. 외부에서 Service 메서드를 호출하면

Controller에서:
```java
paperOrderService.saveOrder();
```

를 호출하면 흐름은 다음과 같다.
```text
Controller
   ↓
Proxy.saveOrder()
   ↓
트랜잭션 대상인지 판단
   ↓
@Transactional 대상이면 트랜잭션 시작
   ↓
실제 객체.saveOrder()
   ↓
DB 작업 실행
   ↓
성공 → COMMIT
실패 → ROLLBACK
```

**트랜잭션**은 여러 DB 작업을 하나의 작업 단위로 묶는 것이다.

예를 들어:
```text
orderRepository.save(...)
fillRepository.saveAll(...)
```

두 작업을 하나의 트랜잭션으로 묶었다면:
```text
둘 다 성공
→ COMMIT
→ DB 변경사항 확정

중간에 하나라도 실패
→ ROLLBACK
→ 앞에서 성공한 DB 작업까지 취소
```

즉 `@Transactional`을 통해 **일부 DB 작업만 저장되어 데이터가 서로 맞지 않는 상태를 방지할 수 있다.**

---

## 왜 `saveOrder()`의 `@Transactional`을 다시 검사하지 않는가?

### `@Transactional`이 적용되는 조건

현재 프로젝트처럼 Spring의 기본 Proxy 방식으로 트랜잭션을 처리하는 경우,
`@Transactional`은 **메서드 호출이 Spring Proxy를 거칠 때만 적용된다.**

```text
외부 Bean
→ Spring Proxy
→ @Transactional 확인
→ 트랜잭션 시작
→ 실제 객체의 메서드 실행
```

반대로 Spring Proxy를 거치지 않고 실제 객체의 메서드를 직접 호출하면,
메서드에 `@Transactional`이 있어도 새로운 트랜잭션이 시작되지 않는다.

```text
실제 객체
→ @Transactional 메서드 직접 호출
→ Proxy를 거치지 않음
→ @Transactional을 처리할 주체가 없음
→ 트랜잭션 시작 안 함
```

같은 객체 내부에서 호출하는 `this.saveFills()`도 실제 객체를 직접 호출하는 경우다.

```text
실제 객체.outer()
→ this.saveFills()
→ 실제 객체.saveFills()
→ Proxy를 거치지 않음
→ saveFills()의 @Transactional이 적용되지 않음
```

그 이유는 `@Transactional`이 트랜잭션을 직접 시작하는 코드가 아니기 때문이다.

`@Transactional`은 단지
**“이 메서드를 트랜잭션 안에서 실행해야 한다”는 표시**이며,
이 표시를 읽고 실제로 트랜잭션을 시작하는 주체는 Spring Proxy다.

### 현재 `PaperOrderService`에 적용하면

예를 들어 `saveOrder()`에 `@Transactional`을 붙였다고 가정한다.

```java
public Mono<?> placeOrder() {
    return saveOrder();
}

@Transactional
private Mono<?> saveOrder() {
    // 주문과 체결 저장
}
```

여기서 `saveOrder()` 호출은 사실상 다음과 같다.

```java
this.saveOrder();
```

같은 객체 내부 호출만 떼어서 보면 원래 흐름은 다음과 같다.

```text
실제 객체.placeOrder()
   ↓
this.saveOrder()
   ↓
실제 객체.saveOrder()
```

`Controller`에 주입된 `paperOrderService` 변수에는 실제 객체가 아니라 Spring Proxy가 들어 있다. 따라서 Controller 코드에서 `paperOrderService.placeOrder()`를 호출하면, 실제 호출 대상은 처음부터 Proxy다.

```text
Controller의 paperOrderService = Spring Proxy

Controller가 paperOrderService.placeOrder() 호출
→ 실제로는 Spring Proxy.placeOrder() 호출
```

Proxy는 자신이 가로챈 현재 호출의 메서드인 `placeOrder()`에 트랜잭션을 적용해야 하는지 확인한다. 이때 `saveOrder()`까지 미리 찾아가서 모든 `@Transactional`을 검사하는 것은 아니다.

#### `placeOrder()`에 `@Transactional`이 있는 경우

`Controller`가 `placeOrder()`를 호출하면 Proxy가 `placeOrder()`의 `@Transactional`을 확인하고 트랜잭션을 시작한다. 이 상태에서 실제 객체가 내부 메서드인 `this.saveOrder()`를 호출한다.

이때 `saveOrder()` 자체의 트랜잭션 처리와 그 안의 DB 작업을 구분해야 한다.

```text
saveOrder()의 @Transactional
→ Proxy를 다시 거치지 않으므로 별도로 적용되지 않음

saveOrder() 안의 DB 작업
→ placeOrder()에서 이미 시작한 트랜잭션에 참여
```

전체 흐름은 다음과 같다.

```text
Controller
→ Spring Proxy.placeOrder()
→ Proxy가 placeOrder()의 @Transactional 확인
→ 트랜잭션 시작
┌──────────────────────────────────────────────┐
│ 실제 PaperOrderService.placeOrder() 실행    │
│ → 실제 객체 내부에서 this.saveOrder() 호출  │
│ → this.saveOrder()는 Proxy를 다시 거치지 않음 │
│ → saveOrder()의 주문 저장                    │
│ → saveOrder()의 체결 저장                    │
│ → 두 DB 작업 모두 기존 트랜잭션에 참여       │
└──────────────────────────────────────────────┘
→ 모두 성공하면 COMMIT
→ 중간에 에러가 발생하면 ROLLBACK
```

내부의 `this.saveOrder()` 호출에는 트랜잭션 AOP가 다시 적용되지 않는다. 하지만 `placeOrder()`를 시작할 때 이미 트랜잭션이 만들어졌기 때문에, 같은 리액티브 체인에 연결된 `saveOrder()`의 DB 작업은 그 기존 트랜잭션 안에서 실행된다.

#### `placeOrder()`에는 없고 `saveOrder()`에만 `@Transactional`이 있는 경우

`Controller`가 `placeOrder()`를 호출하면 Proxy는 자신이 가로챈 `placeOrder()`의 트랜잭션 정보만 확인한다. `placeOrder()`에는 `@Transactional`이 없으므로 트랜잭션을 시작하지 않고 실제 객체의 메서드를 호출한다. 이후 실제 객체가 내부 메서드인 `this.saveOrder()`를 호출한다.

이 경우에도 `saveOrder()` 자체의 트랜잭션 처리와 그 안의 DB 작업을 구분해서 봐야 한다.

```text
saveOrder()의 @Transactional
→ Proxy가 saveOrder() 호출을 가로채지 못함
→ 어노테이션이 검사되지 않으므로 적용되지 않음

saveOrder() 안의 DB 작업
→ placeOrder()에서 시작된 기존 트랜잭션이 없음
→ 주문 저장과 체결 저장이 하나의 트랜잭션으로 묶이지 않음
```

전체 흐름은 다음과 같다.

```text
Controller
→ Spring Proxy.placeOrder()
→ Proxy가 현재 호출인 placeOrder()의 트랜잭션 정보 확인
→ placeOrder()에는 @Transactional이 없으므로 트랜잭션을 시작하지 않음
┌──────────────────────────────────────────────────┐
│ 실제 PaperOrderService.placeOrder() 실행        │
│ → 실제 객체 내부에서 this.saveOrder() 호출      │
│ → this.saveOrder()는 Proxy를 다시 거치지 않음    │
│ → saveOrder()의 @Transactional은 검사되지 않음  │
│ → saveOrder()의 주문 저장                        │
│ → saveOrder()의 체결 저장                        │
│ → 두 DB 작업을 묶는 트랜잭션 없음                │
└──────────────────────────────────────────────────┘
→ 모두 성공하면 각각의 DB 변경 사항이 저장됨
→ 체결 저장 중 에러가 발생하면 먼저 끝난 주문 저장은 남을 수 있음
```

내부의 `this.saveOrder()` 호출에는 트랜잭션 AOP가 적용되지 않는다. `placeOrder()`에서도 트랜잭션을 시작하지 않았으므로 `saveOrder()`의 DB 작업이 참여할 기존 트랜잭션도 없다. 따라서 주문 저장과 체결 저장 중 하나가 실패했을 때 두 작업을 함께 ROLLBACK할 수 없다.

여기서 실제 객체가 `saveOrder()`의 `@Transactional`을 보고도 그냥 지나치는 것은 아니다. 실제 객체는 어노테이션을 해석해 트랜잭션을 시작하는 역할을 하지 않는다. `saveOrder()` 호출이 Proxy에 도착하지 않았기 때문에, Proxy의 트랜잭션 AOP가 그 어노테이션을 아예 확인하지 못한 것이다.

또한 예시처럼 `saveOrder()`가 `private`이면 Proxy가 해당 메서드를 직접 가로챌 수도 없다. `saveOrder()`를 `public`으로 바꾸더라도 같은 객체 내부에서 `this.saveOrder()`로 호출하면 Proxy를 우회한다는 점은 같다.

위 흐름은 개념을 설명하기 위해 단순화한 것이다. R2DBC의 리액티브 트랜잭션은 반환된 `Mono`가 구독될 때 시작되고, 해당 리액티브 작업이 정상 완료되거나 에러로 끝날 때 COMMIT 또는 ROLLBACK된다.

### 결론
```text
외부에서 Service 호출
→ Proxy를 거침
→ @Transactional 적용 가능
→ 성공하면 COMMIT / 실패하면 ROLLBACK

같은 Service 내부에서 this.xxx() 호출
→ this는 실제 target 객체
→ Proxy를 다시 거치지 않음
→ 내부 메서드의 @Transactional을 Proxy가 새로 처리하지 못함
```

> **핵심: 외부에서 주입받은&#x20;****`PaperOrderService`****는 Proxy를 통해 호출되지만, 실제 Service 내부의&#x20;****`this`****는 실제 target 객체 자신이다. 따라서&#x20;****`this.saveOrder()`****는 Proxy를 우회하므로&#x20;****`saveOrder()`****의&#x20;****`@Transactional`****이 별도로 적용되지 않는다.**
