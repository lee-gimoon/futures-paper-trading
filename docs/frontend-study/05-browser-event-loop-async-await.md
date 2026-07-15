# 브라우저 JavaScript의 이벤트 루프와 `async` / `await`

로그인처럼 서버 응답을 기다리는 작업을 할 때, 브라우저의 JavaScript는 일반적인 Spring MVC + Tomcat 요청 처리와 다르게 동작한다.

## 핵심 결론

브라우저에서 JavaScript를 실행하는 기본 모델은 **이벤트 루프 기반**이다. `fetch` 같은 네트워크 요청은 브라우저의 네트워크 계층에 등록되고, JavaScript 메인 스레드는 서버 응답을 기다리며 멈춰 있지 않는다.

`await`는 스레드를 블로킹하는 명령이 아니다. 현재 `async` 함수의 나머지 실행을 잠시 미루고, 제어권을 이벤트 루프에 돌려준다.

## 브라우저에서 JavaScript가 실행되는 구조

일반적인 웹 페이지의 JavaScript는 브라우저 렌더러 프로세스에 있는 **메인 OS 스레드** 하나에서 실행된다. 이 스레드를 보통 **JavaScript 메인 스레드**라고 부른다.

각 구성 요소의 역할은 다음과 같이 나뉜다.

```text
OS 스케줄러
  → 브라우저 렌더러 프로세스의 메인 OS 스레드를 CPU에 배정
    → 브라우저가 이벤트 루프를 실행
      → JavaScript 엔진(V8 등)이 선택된 JavaScript 코드를 실행
        → CPU가 최종적으로 기계어 명령어를 실행
```

- **OS**는 어떤 스레드가 CPU를 사용할지 스케줄링한다.
- **브라우저**는 메인 스레드 위에서 이벤트 루프를 돌리고, 네트워크·타이머·렌더링 같은 기능을 제공한다.
- **JavaScript 엔진**은 이벤트 루프가 선택한 JavaScript 함수의 코드를 실행한다.
- **CPU**는 그 코드가 변환된 기계어 명령어를 실제로 실행한다.

따라서 OS가 `onClick` 같은 JavaScript 함수를 직접 호출하는 것은 아니다. OS가 브라우저의 메인 스레드에 CPU 시간을 주면, 브라우저가 이벤트 루프를 통해 다음에 실행할 작업을 고르고 JavaScript 엔진이 그 작업을 실행한다.

### 이벤트 루프는 별도 스레드가 아니다

이벤트 루프 자체는 스레드의 이름이 아니라, **메인 OS 스레드에서 반복 실행되는 브라우저의 작업 방식**이다. 단순화하면 다음과 같다.

```ts
while (브라우저가 열려 있음) {
  큐에서 다음 작업을 꺼낸다;
  해당 JavaScript 코드를 끝까지 실행한다;
  Promise 마이크로태스크를 처리한다;
  필요하면 화면을 렌더링한다;
}
```

한 작업의 JavaScript가 실행되는 동안에는 같은 메인 스레드에서 다른 JavaScript 작업이 동시에 실행되지 않는다. 현재 함수의 호출 스택이 비워진 뒤에야 다음 클릭 이벤트나 Promise 후속 코드가 실행될 수 있다. 이를 JavaScript의 **run-to-completion** 특성이라고 한다.

### 메인 스레드에서 실행되는 코드

React 코드도 모두 일반 JavaScript이므로, 아래 작업은 보통 같은 메인 스레드에서 하나씩 실행된다.

- 클릭·키보드 입력 이벤트 핸들러
- React의 `onClick`, `onSubmit`
- `setTimeout` 콜백
- Promise의 `then`, `catch`, `await` 뒤에 이어지는 코드
- `fetch` 응답 뒤 실행되는 JavaScript 코드
- React state 변경과 그에 따른 렌더링 JavaScript

따라서 메인 스레드를 오래 점유하는 반복문이나 무거운 계산이 있으면, 다음 클릭 이벤트와 화면 갱신도 처리하지 못해 화면이 멈춘 것처럼 보인다.

### 네트워크 I/O는 메인 스레드가 기다리지 않는다

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

### 별도 JavaScript 스레드가 필요한 경우

긴 계산처럼 메인 스레드를 점유할 수 있는 작업은 **Web Worker**로 옮길 수 있다. Worker는 별도 OS 스레드와 별도 이벤트 루프에서 JavaScript를 실행한다. 다만 Worker는 DOM이나 React 화면을 직접 변경할 수 없으며, 메인 스레드와 메시지를 주고받아야 한다.

## Spring MVC + Tomcat과 비교

| 구분 | Spring MVC + Tomcat | 브라우저 JavaScript + `fetch` |
|---|---|---|
| 요청 처리 | 보통 요청마다 Tomcat 작업 스레드가 배정된다. | JavaScript 메인 스레드가 이벤트를 하나씩 처리한다. |
| 외부 I/O 대기 | JDBC나 외부 HTTP를 동기로 호출하면 해당 스레드가 대기한다. | 요청을 브라우저 네트워크 계층에 등록하고 즉시 `Promise`를 받는다. |
| 응답 도착 후 | 대기 중이던 스레드가 이어서 실행한다. | 이벤트 루프가 후속 JavaScript 코드를 큐에서 꺼내 실행한다. |
| 스레드 점유 | 일반적인 동기 MVC 코드에서는 스레드가 점유될 수 있다. | 네트워크 대기 동안 JavaScript 메인 스레드는 점유하지 않는다. |

백엔드가 Spring MVC + Tomcat으로 처리되어 서버 스레드가 대기하더라도, 그것은 서버 측의 일이다. 브라우저의 JavaScript 메인 스레드는 서버 응답을 기다리며 멈추지 않는다.

## `await onLogin(...)`은 어떻게 동작하는가

`LoginForm.tsx`의 코드는 다음과 같다.

```tsx
async function handleSubmit() {
  await onLogin(email, password);
  onClose();
}
```

흐름은 다음과 같다.

```text
1. 메인 스레드가 로그인 버튼 클릭 이벤트를 처리한다.
2. `handleSubmit()`이 호출되고, 그 안에서 `onLogin(email, password)`를 호출한다.
3. `onLogin` 내부에서 `fetch`가 서버 요청을 브라우저 네트워크 계층에 등록한다.
4. `fetch`는 “나중에 완료될 작업”을 뜻하는 Promise를 즉시 반환하고, `onLogin`도 그 완료를 나타내는 Promise를 반환한다.
5. `await`가 그 Promise를 받는다. Promise가 아직 완료되지 않았다면, 성공 시 `onClose()`부터 이어서 실행할 후속 작업을 Promise에 연결한다.
6. `handleSubmit`의 현재 실행은 `await` 위치에서 잠시 중단되고, 호출 스택에서 빠진다. 메인 스레드는 이벤트 루프로 돌아가 다른 클릭·타이머·렌더링 작업을 처리한다.
7. 서버 응답이 도착하면 브라우저 네트워크 계층이 `fetch`의 Promise를 완료한다. `onLogin`의 나머지 비동기 작업까지 성공하면 `onLogin`의 Promise도 성공 완료된다.
8. JavaScript는 `await` 뒤의 코드를 재개할 작업을 마이크로태스크 큐에 넣는다.
9. 현재 실행 중인 JavaScript 작업이 끝나면 이벤트 루프가 마이크로태스크를 실행한다. 중단했던 **같은 `handleSubmit` 호출**이 `await` 다음 줄부터 재개되어 `onClose()`를 실행한다.
```

중요한 점은 로그인 성공 후 `handleSubmit()`이 새로 호출되는 것이 아니라는 점이다.

```text
잘못된 이해: 로그인 성공 → handleSubmit()을 새로 호출
정확한 이해: handleSubmit() 실행 중 → await에서 일시 중단 → 로그인 성공 → 같은 호출을 재개
```

`await`는 Promise가 완료되었을 때 실행할 다음 작업을 Promise에 연결한다. 따라서 `await`를 만나자마자 `onClose()`가 실행되는 것이 아니라, Promise가 성공 완료된 뒤 마이크로태스크로 재개될 때 실행된다. 개념적으로 아래 코드와 비슷하다. 실제 코드에는 `try` / `catch` / `finally`도 있으므로 완전히 같은 코드는 아니다.

```tsx
onLogin(email, password).then(() => {
  onClose();
});
```

실제 `LoginForm.tsx`의 흐름을 단순화하면 다음과 같이 볼 수 있다.

```tsx
onLogin(email, password)
  .then(() => onClose())          // 로그인 성공 시
  .catch((err) => setError(...))  // 로그인 실패 시
  .finally(() => setSubmitting(false));
```

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
