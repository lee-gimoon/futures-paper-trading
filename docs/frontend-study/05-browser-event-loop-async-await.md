# 브라우저 JavaScript의 이벤트 루프와 `async` / `await`

로그인처럼 서버 응답을 기다리는 작업을 할 때, 브라우저의 JavaScript는 일반적인 Spring MVC + Tomcat 요청 처리와 다르게 동작한다.

## 핵심 결론

브라우저에서 JavaScript를 실행하는 기본 모델은 **이벤트 루프 기반**이다. `fetch` 같은 네트워크 요청은 브라우저의 네트워크 계층에 등록되고, JavaScript 메인 스레드는 서버 응답을 기다리며 멈춰 있지 않는다.

`await`는 스레드를 블로킹하는 명령이 아니다. 현재 `async` 함수의 나머지 실행을 잠시 미루고, 제어권을 이벤트 루프에 돌려준다.

## 메인 스레드와 이벤트 루프

일반적인 웹 페이지의 JavaScript는 브라우저 렌더러 프로세스에 있는 **메인 OS 스레드** 하나에서 실행된다. 이 스레드를 보통 **JavaScript 메인 스레드**라고 부른다.

이벤트 루프는 별도의 스레드가 아니라, 이 메인 스레드에서 다음 JavaScript 작업을 하나씩 꺼내 실행하는 브라우저의 작업 방식이다. 클릭·키보드 입력 이벤트, React의 `onClick`·`onSubmit`, 타이머 콜백, Promise의 후속 코드는 모두 이 메인 스레드에서 실행된다.

한 JavaScript 작업이 실행되는 동안에는 같은 메인 스레드에서 다른 JavaScript 작업이 동시에 실행되지 않는다. 현재 호출 스택이 비워진 뒤에 다음 작업이 실행되는 특성을 **run-to-completion**이라고 한다. 따라서 긴 반복문이나 무거운 계산은 클릭 처리와 화면 갱신까지 막을 수 있다.

## 네트워크 I/O는 메인 스레드가 기다리지 않는다

`fetch`를 호출하는 JavaScript 코드는 메인 스레드에서 실행되지만, 실제 HTTP 연결·패킷 송수신·응답 대기는 브라우저의 네트워크 계층과 OS가 처리한다.

```text
메인 스레드
  fetch('/api/login') 호출 → 네트워크 요청 등록 → Promise를 즉시 받음

브라우저 네트워크 계층 / OS
  HTTP 연결·송수신·응답 대기 수행

메인 스레드
  응답 도착 후 Promise 후속 JavaScript 코드 실행
```

즉, `fetch`의 요청 시작 코드와 응답 뒤 실행할 코드는 메인 스레드에서 실행하지만, 네트워크 응답을 기다리는 동안 메인 스레드가 대기 상태로 점유되지는 않는다.

서버 응답이 돌아온 뒤에는 다음 역할이 순서대로 이어진다.

```text
OS
  → 브라우저의 네트워크 계층에 수신 가능한 데이터가 있음을 알림
  → 브라우저의 메인 스레드에 CPU 실행 시간을 배정

브라우저 네트워크 계층
  → 소켓에서 응답 바이트 수신
  → HTTP 응답으로 해석
  → fetch Promise 완료

브라우저 이벤트 루프
  → await 뒤 코드를 재개할 마이크로태스크를 등록
  → 현재 작업이 끝난 뒤 마이크로태스크를 선택

JavaScript 엔진
  → 선택된 코드, 즉 await 다음 줄부터 실행
```

### 예시: 로그인 응답 후 `await` 다음 줄이 실행되는 과정

```tsx
async function handleSubmit() {
  console.log('1. 로그인 요청 시작');

  const response = await fetch('/api/login');
  if (!response.ok) {
    throw new Error('로그인 실패');
  }

  console.log('2. 서버 응답 수신 후 실행');
  onClose();
}
```

```text
1. 로그인 버튼 클릭으로 handleSubmit()이 호출된다.
2. fetch가 Spring Boot 서버로 HTTP 요청을 등록하고 Promise를 반환한다.
3. await는 현재 handleSubmit 호출을 잠시 중단한다. 메인 스레드는 다른 작업을 처리할 수 있다.
4. Spring Boot가 HTTP 응답을 전송한다.
5. 브라우저 네트워크 계층이 응답을 해석하고 fetch Promise를 완료한다.
6. await 다음 코드를 재개할 마이크로태스크가 큐에 등록된다.
7. 현재 JavaScript 작업이 끝나면, 같은 handleSubmit 호출이 await 다음 줄부터 재개된다.
8. response.ok가 true이면 '2. 서버 응답 수신 후 실행'을 출력하고 onClose()를 호출한다.
```

`await`는 잠금(lock)을 잡고 있는 것이 아니다. 로그인 성공 후 `handleSubmit()`을 새로 호출하는 것도 아니다. Promise가 완료되면 이미 실행 중이었다가 중단된 **같은 `handleSubmit` 호출**이 이어서 실행된다.

`fetch`는 HTTP `401`, `404`, `500` 응답을 받았을 때도 네트워크 통신 자체는 성공했으므로 `Response`를 반환할 수 있다. 따라서 로그인 성공 여부는 `response.ok`를 확인해 판단한다.

## Spring MVC + Tomcat과 비교

| 구분 | Spring MVC + Tomcat | 브라우저 JavaScript + `fetch` |
|---|---|---|
| 요청 처리 | 보통 요청마다 Tomcat 작업 스레드가 배정된다. | JavaScript 메인 스레드가 이벤트를 하나씩 처리한다. |
| 외부 I/O 대기 | JDBC나 외부 HTTP를 동기로 호출하면 해당 스레드가 대기한다. | 요청을 브라우저 네트워크 계층에 등록하고 즉시 `Promise`를 받는다. |
| 응답 도착 후 | 대기 중이던 스레드가 이어서 실행한다. | 이벤트 루프가 후속 JavaScript 코드를 큐에서 꺼내 실행한다. |
| 스레드 점유 | 일반적인 동기 MVC 코드에서는 스레드가 점유될 수 있다. | 네트워크 대기 동안 JavaScript 메인 스레드는 점유하지 않는다. |

백엔드가 Spring MVC + Tomcat으로 처리되어 서버 스레드가 대기하더라도, 그것은 서버 측의 일이다. 브라우저의 JavaScript 메인 스레드는 서버 응답을 기다리며 멈추지 않는다.

## `Promise<void>`의 의미

```ts
Promise<void>
```

- `Promise`: 성공 또는 실패로 나중에 한 번 완료될 비동기 작업이다.
- `<void>`: Java의 제네릭처럼, 작업이 성공한 뒤 제공할 결과값의 타입을 적는 자리다.
- `void`: 성공해도 호출자에게 돌려줄 의미 있는 값이 없다는 뜻이다.

따라서 `Promise<void>`는 “로그인 작업이 끝날 때까지는 기다릴 수 있지만, 완료 후 `LoginForm`이 받을 결과값은 없다”는 뜻이다. Java에서는 `CompletableFuture<Void>`와 가장 비슷하다.

로그인 성공 후 사용자 정보와 로그인 상태는 보통 부모의 `useAuth`가 관리한다. `LoginForm`은 로그인 완료만 기다린 뒤 `onClose()`를 호출하면 된다.

## WebFlux + Netty와의 관계

브라우저의 비동기 I/O 흐름은 WebFlux + Netty의 이벤트 루프와 비슷하게 이해할 수 있다.

```text
Netty 이벤트 루프
  I/O 등록 → 이벤트 루프 스레드는 다른 작업 처리 → I/O 준비됨 → 후속 처리 실행

브라우저 이벤트 루프
  fetch 등록 → JavaScript 메인 스레드는 다른 이벤트 처리 → 응답 도착 → Promise 후속 코드 실행
```

둘 다 “I/O 응답을 기다리는 동안 처리 스레드를 붙잡아 두지 않는다”는 공통점이 있다. 다만 `Promise`는 한 번 성공 또는 실패로 완료되는 객체이고, `Mono`와 `Flux`는 Reactive Streams의 Publisher라는 점에서 정확히 같은 개념은 아니다.

- `Promise`: 보통 호출 즉시 작업이 시작되며, 한 번만 완료 또는 실패한다.
- `Mono`: 최대 한 개의 값을 발행한다. 일반적으로 구독되었을 때 실행되는 cold publisher로 사용할 수 있다.
- `Flux`: 여러 값을 시간에 따라 계속 발행한다. 이 프로젝트의 SSE 호가 스트림이 이에 더 가깝다.

## 주의: 모든 JavaScript가 자동으로 논블로킹인 것은 아니다

`fetch`는 브라우저가 제공하는 비동기 네트워크 I/O이므로 메인 스레드를 막지 않는다. 하지만 JavaScript에서 오래 걸리는 계산을 직접 수행하면 메인 스레드가 점유되어 화면도 멈춘다.

```ts
while (true) {
  // 메인 스레드를 계속 점유하므로 클릭·렌더링도 처리할 수 없다.
}
```

즉, `async` / `await`가 자동으로 새 스레드를 만드는 것은 아니다. 비동기 I/O 작업의 완료를 나중에 이어서 처리하도록 만드는 문법이며, 긴 CPU 작업은 별도로 나누거나 Web Worker 같은 방법을 고려해야 한다.
