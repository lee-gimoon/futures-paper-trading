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

이 절에서는 `placeOrder()`가 같은 객체의 `saveOrder()`를 호출할 때, `@Transactional`을 어느 메서드에 붙이느냐에 따라 트랜잭션 적용 결과가 어떻게 달라지는지 비교한다.

두 경우 모두 메서드의 호출 관계는 다음과 같다.

```java
public Mono<?> placeOrder() {
    return saveOrder();
}

private Mono<?> saveOrder() {
    // 주문과 체결 저장
}
```

`placeOrder()` 안의 `saveOrder()` 호출은 `this.saveOrder()`와 같다. 여기서 `this`는 Spring Proxy가 아니라 현재 `placeOrder()`를 실행하고 있는 실제 `PaperOrderService` 객체다.

한편 `Controller`에 주입된 `paperOrderService`는 실제 객체가 아니라 Spring Proxy다. 따라서 Controller가 `paperOrderService.placeOrder()`를 호출하면 처음에는 Proxy로 들어가지만, Proxy가 실제 객체의 `placeOrder()`를 호출한 이후의 `this.saveOrder()`는 실제 객체 내부 호출이 된다.

```text
Controller의 paperOrderService = Spring Proxy
→ Controller가 paperOrderService.placeOrder() 호출
→ Spring Proxy.placeOrder()가 호출을 받음
→ Proxy가 현재 호출된 placeOrder()의 트랜잭션 정보 확인
→ 실제 PaperOrderService.placeOrder() 실행
   → this.saveOrder() 호출
   → 실제 PaperOrderService.saveOrder() 실행
```

Proxy는 자신이 직접 받은 `placeOrder()` 호출의 트랜잭션 정보만 확인한다. `placeOrder()`가 나중에 어떤 내부 메서드를 호출할지 미리 찾아가서 그 메서드들의 `@Transactional`까지 모두 검사하지는 않는다.

따라서 다음 두 경우를 나누어 봐야 한다.

1. Proxy가 직접 호출받는 `placeOrder()`에 `@Transactional`이 있는 경우
2. 내부에서 직접 호출되는 `saveOrder()`에만 `@Transactional`이 있는 경우

먼저 `placeOrder()`에 `@Transactional`이 있는 경우부터 살펴보자.

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
→ 실제 객체 내부에서 this.saveOrder()로 직접 호출됨
→ 호출이 Proxy를 거치지 않음
(`this.saveOrder()`의 호출 대상이 Proxy가 아니라 실제 객체이므로)
→ @Transactional을 검사하는 트랜잭션 AOP가 실행되지 않음
→ saveOrder()의 @Transactional이 적용되지 않음

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

##### 참고: 트랜잭션 AOP란?

AOP(Aspect-Oriented Programming)는 여러 메서드에 공통으로 필요한 처리를 실제 비즈니스 코드와 분리해, 메서드 실행 전후에 적용하는 방식이다. 트랜잭션 AOP는 이 구조를 이용해 대상 메서드 실행 전에는 트랜잭션을 준비하고, 실행 결과에 따라 COMMIT 또는 ROLLBACK을 처리한다.

`@Transactional`은 트랜잭션을 직접 시작하는 코드가 아니라 트랜잭션 설정을 담은 메타데이터다. Spring의 트랜잭션 AOP는 다음 구성 요소를 통해 이 정보를 처리한다.

- `TransactionInterceptor`: Proxy로 들어온 메서드 호출에서 트랜잭션 처리를 실행하는 AOP 인터셉터
- `TransactionAttributeSource`: 현재 호출된 메서드와 대상 클래스에서 트랜잭션 설정을 조회하는 역할
- `AnnotationTransactionAttributeSource`: `@Transactional`을 읽어 전파 방식, 격리 수준, 읽기 전용 여부, ROLLBACK 규칙 등의 트랜잭션 설정으로 변환하는 구현체
- `TransactionManager`: 읽어 낸 설정에 따라 실제 트랜잭션을 시작하고 COMMIT 또는 ROLLBACK하는 역할

개념적인 처리 순서는 다음과 같다.

```text
외부 Bean에서 메서드 호출
→ Spring Proxy가 호출을 받음
→ TransactionInterceptor 실행
→ TransactionAttributeSource가 현재 메서드와 대상 클래스의 트랜잭션 정보 조회
→ AnnotationTransactionAttributeSource가 @Transactional을 읽음
   ├─ @Transactional 없음: 트랜잭션 없이 실제 메서드 실행
   └─ @Transactional 있음: TransactionManager를 이용해 트랜잭션 경계 생성
                              → 실제 메서드의 DB 작업 실행
                              → 성공 시 COMMIT / 실패 시 ROLLBACK
```

따라서 실제 객체 내부의 `this.saveOrder()`처럼 Proxy를 거치지 않는 호출에서는 `TransactionInterceptor`가 실행되지 않는다. `TransactionAttributeSource`에 트랜잭션 정보를 조회하는 단계에도 도달하지 않으므로 `saveOrder()`의 `@Transactional`은 읽히지 않는다.

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
