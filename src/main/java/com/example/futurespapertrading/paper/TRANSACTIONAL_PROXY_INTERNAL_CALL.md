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

따라서 Proxy를 거치지 않는 내부 호출에서는 다음과 같은 흐름이 만들어지지 않는다.

```text
Proxy.saveOrder()
   ↓
@Transactional 검사
   ↓
트랜잭션 시작
   ↓
실제 객체.saveOrder()
```

예를 들어:
```java
public Mono<?> placeOrder() {
    return saveOrder();
}
```

여기서:
```java
saveOrder();
```

는 사실상:
```java
this.saveOrder();
```

와 같다.

그리고 이때 `this`는 Proxy가 아니라 **현재 실행 중인 실제&#x20;****`PaperOrderService`****&#x20;객체 자신**을 가리킨다.
```text
Controller
   │
   │ paperOrderService = Proxy
   ▼
Proxy
   │
   │ 실제 객체.placeOrder()
   ▼
실제 PaperOrderService
   │
   │ this = 실제 객체
   │
   │ this.saveOrder()
   ▼
실제 PaperOrderService.saveOrder()
```

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
