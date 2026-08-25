# Expo 모바일 앱 초기 설정 설명

이 문서는 `mobile/`에 Expo 프로젝트를 처음 만들면서 **현재 실제로 생성된 폴더와 설정 파일만** 설명한다.

로그인, 시세, 주문처럼 아직 구현하지 않은 기능은 다루지 않는다. 새로운 기능은 실제 파일을 만들 때 학습 계획에 따라 하나씩 추가한다.

---

## 먼저: 웹 React와 모바일 React는 무엇이 다른가?

가장 먼저 기억할 핵심은 다음 문장이다.

> 모바일에서 React를 안 쓰는 것이 아니라, **React를 쓰되 브라우저용 `react-dom` 대신 React Native를 사용한다.**

React는 웹 전용 기술이 아니다. React의 중심 역할은 컴포넌트, 상태와 속성을 사용해 **현재 어떤 UI가 필요한지 계산하는 것**이다. 계산한 UI를 실제로 어느 화면에 표시할지는 플랫폼별 도구가 담당한다.

```text
웹     = React + react-dom
모바일 = React + React Native
```

### 웹과 모바일에서 공통으로 사용하는 React

두 환경 모두 React의 컴포넌트와 `useState` 같은 Hook을 사용한다.

웹 React 예시:

```tsx
import { useState } from 'react';

const [count, setCount] = useState(0);

<button onClick={() => setCount(count + 1)}>
  {count}
</button>
```

모바일 React 예시:

```tsx
import { useState } from 'react';
import { Pressable, Text } from 'react-native';

const [count, setCount] = useState(0);

<Pressable onPress={() => setCount(count + 1)}>
  <Text>{count}</Text>
</Pressable>
```

상태를 관리하는 React 코드는 거의 같다. 실제 화면 요소와 사용자 입력을 연결하는 플랫폼 도구가 다르다.

### React 웹과 React Native 모바일 비교

| 구분 | React 웹 | React Native 모바일 |
|---|---|---|
| 공통 UI 로직 | React | React |
| 화면에 반영하는 도구 | `react-dom` | `react-native` |
| 대표 상위 프레임워크(선택) | Next.js | Expo |
| 현재 프로젝트의 프레임워크 선택 | Next.js 사용 안 함 | Expo 사용 |
| 기본 화면 요소 | `div`, `button`, `input` | `View`, `Pressable`, `TextInput` |
| 글자 | `p`, `span` | `Text` |
| 스타일 | CSS | 스타일 객체, `StyleSheet` |
| 화면 구조 | 브라우저 DOM | Android/iOS 네이티브 UI |
| 대표 JavaScript 엔진 | Chrome의 V8 | Hermes |
| 현재 프로젝트의 변환·묶음 도구 | Vite | Metro |
| 실행 장소 | 웹 브라우저 | Expo Go, 개발 빌드 또는 출시 앱 |

Next.js와 Expo는 모두 필수는 아니다. React 웹은 Next.js 없이 만들 수 있고, React Native 앱도 Expo 없이 만들 수 있다. 현재 프로젝트의 웹은 **Next.js 없이 React + Vite**를 사용하고, 모바일은 **React + React Native + Expo**를 사용한다. Vite와 Metro는 코드를 변환·묶는 도구이며 Next.js와 Expo 같은 앱 프레임워크와 역할이 다르다.

### 웹 React 코드가 실행되어 화면에 나타나는 과정

이 프로젝트의 웹 개발 환경에서는 Vite가 TypeScript/TSX를 JavaScript로 변환한다. 브라우저의 JavaScript 엔진이 코드를 실행하고, React가 계산한 UI를 `react-dom`이 DOM에 반영하면 브라우저가 화면을 그린다.

```text
작성한 TypeScript/TSX
    ↓
Vite가 JavaScript로 변환·묶음
    ↓
Chrome의 V8 엔진이 JavaScript 실행
    ↓
React가 현재 상태에 필요한 UI 계산
    ↓
react-dom이 브라우저 DOM에 변경 반영
    ↓
브라우저가 HTML/CSS 화면 표시
```

### 모바일 React 코드가 실행되어 화면에 나타나는 과정

모바일에서는 `react-dom`과 브라우저 DOM을 사용하지 않는다. Metro가 TypeScript/TSX를 JavaScript로 변환하고, Hermes 엔진이 실행한다. React가 필요한 UI를 계산하면 React Native가 그 결과를 Android/iOS의 실제 화면 요소에 반영한다.

```text
작성한 TypeScript/TSX (`<View>`, `<Text>` 등)
    ↓
Metro가 JavaScript로 변환·묶음
    ↓
Hermes 엔진이 JavaScript 실행
    ↓
React가 현재 상태에 필요한 UI 계산
    ↓
React Native가 Android/iOS 네이티브 UI에 변경 반영
    ↓
Android/iOS 운영체제가 실제 화면 표시
```

여기서 “반영”은 서버로 보내는 HTTP 요청이 아니다. 한 앱 안에서 React가 계산한 화면 변경을 운영체제의 실제 화면 요소에 적용하는 과정이다.

따라서 **React Native 앱**은 React를 사용하면서 화면 출력 대상으로 브라우저가 아닌 Android/iOS를 선택한 앱이라고 이해하면 된다.

---

## React, React Native와 Expo의 역할

### 먼저 용어를 구분한다

React, Expo, Metro와 Hermes는 모두 같은 종류의 프로그램이 아니다. **화면을 계산하는 라이브러리**, **앱의 개발 구조를 제공하는 프레임워크**, **코드를 준비하는 도구**, **코드를 실제로 실행하는 엔진**으로 역할이 나뉜다.

| 종류 | 뜻 | 예시 |
|---|---|---|
| 라이브러리 | 코드에서 필요한 기능이나 컴포넌트를 가져다 사용한다. | React, `react-dom`, Expo Router |
| 프레임워크 | 앱을 만들 기본 구조, 규칙과 개발 흐름을 제공한다. | 웹의 Next.js, 모바일의 Expo |
| 렌더링·플랫폼 기반 | React가 계산한 UI를 특정 플랫폼의 실제 화면에 반영한다. | 웹의 `react-dom`, 모바일의 React Native |
| 변환·묶음 도구(번들러) | TypeScript/TSX와 여러 파일을 실행 가능한 JavaScript 코드로 준비한다. | 웹의 Vite, 모바일의 Metro |
| JavaScript 엔진 | 준비된 JavaScript 명령을 실제로 읽고 실행한다. | Chrome의 V8, React Native의 Hermes |
| 개발 도구 | 명령 실행, 개발 서버 시작과 테스트 등을 돕는다. | Expo CLI, Expo Go |
| 외부 서비스 | 인터넷의 다른 컴퓨터에서 빌드·제출 같은 작업을 대신 수행한다. | EAS Build·Submit |

라이브러리와 프레임워크의 경계가 항상 완벽하게 나뉘는 것은 아니다. 처음에는 **라이브러리는 필요한 기능을 가져다 쓰는 것**, **프레임워크는 앱 전체를 만드는 기본 틀과 개발 흐름을 제공하는 것**으로 이해하면 된다.

### 각각 무엇인가?

| 이름 | 이 문서에서의 구분 | 사용하는 곳 | 담당 역할 |
|---|---|---|---|
| React | UI 라이브러리 | 웹·모바일 공통 | 컴포넌트와 상태를 바탕으로 현재 어떤 UI가 필요한지 계산한다. |
| `react-dom` | 웹 렌더링 라이브러리 | 웹 | React의 계산 결과를 브라우저 DOM에 반영한다. |
| React Native | 모바일 렌더링·네이티브 플랫폼 기반 | 모바일 | React의 계산 결과를 Android/iOS 네이티브 UI에 반영하고, JavaScript 코드가 네이티브 기능을 사용할 기반을 제공한다. |
| Expo | React Native 프레임워크·도구 생태계 | 모바일 | React Native 위에 프로젝트 구성, 개발 흐름, 기기 기능, 화면 이동과 빌드 도구를 제공한다. |
| Next.js | React 웹 프레임워크 | 웹 | React 웹 앱의 라우팅, 빌드와 서버 기능 등을 하나의 구조로 제공한다. 현재 웹 프로젝트에서는 사용하지 않는다. |
| Expo Router | 화면 이동 라이브러리 | 모바일 앱 | `app/`의 파일 구조를 화면 경로로 사용하고, 화면 이동과 뒤로 가기를 관리한다. 개발용 도구가 아니라 출시 앱에서도 실행된다. |
| Vite | 웹 개발 서버·빌드 도구 | 웹 개발·빌드 | 웹의 TypeScript/TSX를 JavaScript로 변환하고 개발 중 브라우저에 제공한다. 현재 웹 프로젝트에서 사용한다. |
| Metro | React Native 번들러·로컬 개발 서버 | 모바일 개발·빌드 | 여러 TypeScript/TSX 파일과 의존성을 JavaScript 코드 묶음으로 만든다. 개발 중에는 그 묶음을 휴대폰에 전송하고, 배포 빌드 때는 앱에 넣을 묶음을 만든다. |
| V8 | JavaScript 엔진 | Chrome 등 | 브라우저가 받은 JavaScript를 실제로 실행한다. Vite가 코드를 실행하는 것이 아니다. |
| Hermes | JavaScript 엔진 | React Native 앱 | Metro가 준비한 JavaScript를 휴대폰에서 실제로 실행한다. 개발 중과 배포 후 모두 사용된다. |
| Expo CLI | 명령줄 개발 도구 | 개발자 PC | `expo start` 같은 명령을 처리하고 Metro 실행과 앱 설정 작업을 관리한다. |
| Expo Go | 개발·테스트용 모바일 앱 | 개발 중 휴대폰 | React Native와 Hermes 등이 이미 들어 있는 앱으로, 개발 중인 JavaScript 코드를 빠르게 실행해 본다. 최종 사용자에게 배포하는 앱은 아니다. |
| EAS Build·Submit | 선택형 클라우드 서비스 | Expo 서버 | 인터넷의 빌드 컴퓨터에서 앱을 빌드하거나 스토어 제출을 돕는다. 앱 실행에 필수인 서버는 아니다. |

React Native를 넓은 의미에서 프레임워크라고 부르는 자료도 있다. 하지만 React Native 자체는 화면 이동 방식, 앱 파일 구조와 여러 기기 기능의 사용 방법까지 하나의 개발 흐름으로 정하지 않는다. React Native 공식 문서도 새 앱에는 Expo 같은 프레임워크 사용을 권장한다. 이 문서에서는 역할을 명확히 구분하기 위해 **React Native는 모바일 화면과 네이티브 실행을 담당하는 기반**, **Expo는 그 기반 위에서 앱 전체 개발 과정을 구성하는 프레임워크**라고 표현한다.

### 핵심 결론

- 이 프로젝트는 **Expo 프레임워크와 도구를 사용하는 React Native 앱**이다. Expo가 React Native를 대신하는 것이 아니라 React Native 위에 개발 환경과 편의 기능을 더한다.
- 개발 중에는 **Metro가 코드를 준비·전송하고, Expo Go 안의 Hermes가 그 코드를 실행**한다.
- 출시 앱에는 미리 묶은 JavaScript, Hermes, React Native와 실제로 사용하는 Expo 모듈이 들어간다. 따라서 사용자는 Expo Go를 설치하지 않고, 앱도 개발자의 Metro 서버에 연결하지 않는다.
- Android/iOS 운영체제는 개발 서버가 아니라 설치된 앱을 시작하고 관리한다. 로그인·시세·주문 데이터가 필요하면 앱이 별도로 실행 중인 Spring API 서버를 호출한다.

---

## 현재 프로젝트 구조

```text
mobile/
├─ .expo/                    # Expo가 자동으로 만든 로컬 작업 폴더
├─ app/
│  ├─ _layout.tsx            # 전체 화면 이동의 최상위 파일
│  └─ index.tsx              # 앱의 첫 화면
├─ node_modules/             # npm이 설치한 패키지의 실제 코드
├─ .gitignore                # Git에서 제외할 파일과 폴더 목록
├─ app.json                  # 모바일 앱 자체의 설정
├─ eslint.config.js          # 코드 검사 설정
├─ package.json              # 실행 명령어와 필요한 패키지 목록
├─ package-lock.json         # 실제 설치된 패키지 버전 기록
└─ tsconfig.json             # TypeScript 검사 설정
```

`app/` 안의 TSX 파일은 각 코드에 작성된 한국어 주석으로 설명한다. 이 문서에서는 Expo, 자동 생성 폴더와 JSON 설정 파일을 중심으로 다룬다.

---

## `.expo/` 폴더를 만든 이유

`.expo/`는 우리가 직접 만든 앱 기능 폴더가 아니다. `expo start`, `expo export` 같은 명령을 실행했기 때문에 **Expo CLI가 자동으로 만든 폴더**다.

이름 앞의 점(`.`)은 일반적으로 숨김 폴더를 의미한다. `.expo/`에는 개발 서버 캐시처럼 이 컴퓨터에서 Expo가 작업하는 데 필요한 임시 정보가 들어간다.

```text
npm run start
    ↓
Expo CLI 실행
    ↓
Metro 번들러·로컬 개발 서버 실행
    ↓
Expo가 로컬 작업 정보를 .expo/에 저장
```

`.expo/`를 사용할 때는 다음 규칙만 기억하면 된다.

- 앱 화면이나 기능 코드를 작성하는 곳이 아니다.
- 내부 파일을 직접 수정하지 않는다.
- 개발자 컴퓨터마다 내용이 달라질 수 있으므로 Git에 올리지 않는다.
- 삭제해도 Expo를 다시 실행하면 자동으로 생성된다.
- 현재 `.gitignore`에 등록되어 있다.

다음 세 가지는 이름이 비슷하지만 서로 다르다.

```text
package.json의 "expo"  → 프로젝트가 사용하는 Expo 패키지
node_modules/expo/     → 설치된 Expo 패키지의 실제 코드
.expo/                 → Expo가 실행 중 자동으로 만든 로컬 작업 데이터
```

---

## 현재 만들어진 JSON 파일

프로젝트 루트에서 지금 확인할 JSON 설정 파일은 다음 네 개다.

```text
app.json
package.json
package-lock.json
tsconfig.json
```

JSON은 설정값을 일정한 구조로 기록하는 형식이다. 일반 JSON에서는 `// 주석`이나 `/* 주석 */`을 작성할 수 없기 때문에 핵심 설정을 이 문서에서 설명한다.

---

## `app.json`: 모바일 앱 자체의 설정

`app.json`에는 화면 코드가 아니라 앱 이름, 버전, 화면 방향과 Android 식별자처럼 **앱 자체에 적용할 설정**을 작성한다.

현재 핵심 부분:

```json
{
  "expo": {
    "name": "Futures Paper Trading",
    "slug": "futures-paper-trading-mobile",
    "version": "1.0.0",
    "orientation": "portrait",
    "scheme": "futurespapertrading",
    "userInterfaceStyle": "dark",
    "android": {
      "package": "com.futurespapertrading.mobile"
    },
    "plugins": [
      "expo-router"
    ]
  }
}
```

| 설정 | 의미 |
|---|---|
| `expo` | Expo 앱 설정을 담는 가장 바깥 객체다. |
| `name` | 휴대폰에서 사용자에게 표시할 앱 이름이다. |
| `slug` | Expo 서비스에서 프로젝트를 구분하는 이름이다. |
| `version` | 사용자에게 표시되는 앱 버전이다. |
| `orientation` | 앱 화면을 세로 방향으로 사용하게 한다. |
| `scheme` | 나중에 외부 링크로 앱을 열 때 사용할 앱 전용 주소 이름이다. |
| `userInterfaceStyle` | 앱의 기본 테마를 어둡게 설정한다. |
| `android.package` | Android가 이 앱을 구분하는 고유 식별자다. |
| `plugins` | Expo에 연결할 추가 기능이다. 현재 Expo Router를 등록했다. |

`newArchEnabled`, `edgeToEdgeEnabled`, `predictiveBackGestureEnabled`, `typedRoutes`는 React Native와 Expo Router의 세부 실행 환경 설정이다. 지금은 각 옵션을 외우기보다 Expo 프로젝트 실행을 위한 설정이라고 이해한다.

---

## `package.json`: 실행 명령어와 패키지 목록

`package.json`은 이 프로젝트를 어떻게 실행하는지, 어떤 npm 패키지가 필요한지를 기록한다.

### 앱 시작 위치

```json
"main": "expo-router/entry"
```

앱 실행을 Expo Router에서 시작한다는 뜻이다. Expo Router는 `app/`에서 화면 파일을 찾는다.

### 실행 명령어

```json
"scripts": {
  "start": "expo start",
  "android": "expo start --android",
  "web": "expo start --web",
  "typecheck": "tsc --noEmit",
  "lint": "expo lint"
}
```

| 입력하는 명령 | 실제 동작 |
|---|---|
| `npm run start` | Expo 개발 서버를 실행하고 QR 코드를 표시한다. |
| `npm run android` | 개발 서버를 실행하고 Android 앱 연결을 시도한다. |
| `npm run web` | 같은 코드를 웹 환경에서 실행한다. |
| `npm run typecheck` | TypeScript 타입 오류를 검사한다. |
| `npm run lint` | 코드 작성 규칙과 실수를 검사한다. |

### 앱 실행에 필요한 패키지

| 패키지 | 역할 |
|---|---|
| `expo` | Expo의 핵심 기능을 제공한다. |
| `react` | 컴포넌트와 상태 같은 React 기능을 제공한다. |
| `react-native` | `View`, `Text`, `Pressable` 같은 모바일 UI를 제공한다. |
| `expo-router` | `app/`의 화면 파일과 화면 이동을 관리한다. |
| `expo-status-bar` | 휴대폰 상단 상태 표시줄을 설정한다. |
| `react-native-safe-area-context` | 화면이 카메라 구멍이나 시스템 영역에 가리지 않게 한다. |
| `react-native-screens` | 화면 전환을 네이티브 화면 방식으로 처리한다. |

`devDependencies`에는 TypeScript와 ESLint처럼 앱 기능보다는 개발 중 코드 검사에 사용하는 패키지가 들어 있다.

---

## `package-lock.json`: 실제 설치 결과 기록

`package-lock.json`은 `npm install`을 실행하면 npm이 자동으로 만든다.

```text
package.json
→ 우리가 필요한 패키지와 허용할 버전 범위를 기록

package-lock.json
→ npm이 실제로 설치한 모든 패키지와 하위 패키지의 정확한 버전을 기록
```

React Native 프로젝트는 한 패키지가 다른 여러 패키지를 사용하므로 `package-lock.json`이 매우 길어진다. 정상적인 모습이다.

- 사람이 직접 수정하지 않는다.
- 패키지를 설치하거나 제거하면 npm이 자동으로 갱신한다.
- 다른 컴퓨터에서도 같은 패키지 조합을 설치할 수 있도록 Git에 올린다.

---

## `tsconfig.json`: TypeScript 검사 설정

`tsconfig.json`은 TypeScript가 어떤 기준으로 `.ts`, `.tsx` 파일을 검사할지 정한다.

현재 핵심 부분:

```json
{
  "extends": "expo/tsconfig.base",
  "compilerOptions": {
    "strict": true,
    "paths": {
      "@/*": [
        "./src/*"
      ]
    }
  }
}
```

| 설정 | 의미 |
|---|---|
| `extends` | Expo가 준비한 기본 TypeScript 설정을 사용한다. |
| `strict` | 잘못된 타입과 빠진 값을 엄격하게 검사한다. |
| `paths` | 나중에 `src/` 파일을 `@/경로` 형태로 불러올 수 있게 한다. |
| `include` | TypeScript가 검사할 파일 범위를 정한다. |

---

## `node_modules/`: 설치된 패키지 코드

`node_modules/`는 `npm install`이 `package.json`과 `package-lock.json`을 읽고 내려받은 실제 패키지 코드가 저장되는 폴더다.

- 파일이 많고 크기가 큰 것이 정상이다.
- 직접 수정하지 않는다.
- Git에 올리지 않는다.
- 삭제해도 `npm install`로 다시 만들 수 있다.

```text
package.json + package-lock.json
              ↓ npm install
         node_modules/
```

---

## JSON은 아니지만 함께 만들어진 설정 파일

| 파일 | 역할 |
|---|---|
| `.gitignore` | `.expo/`, `node_modules/`처럼 Git에 올리지 않을 항목을 정한다. |
| `eslint.config.js` | ESLint가 코드를 어떤 규칙으로 검사할지 정한다. |

`app/_layout.tsx`와 `app/index.tsx`의 설명은 각 파일 안의 한국어 주석을 읽는다.

---

## 현재 앱이 실행되는 순서

```text
1. npm run start 입력
       ↓
2. package.json의 "start": "expo start" 실행
       ↓
3. Expo CLI가 app.json을 읽고 Metro 번들러·로컬 개발 서버 실행
       ↓
4. Expo Go가 QR 코드를 통해 Metro에 연결
       ↓
5. Metro가 TypeScript/TSX를 JavaScript로 변환·묶어서 Expo Go에 전송
       ↓
6. Expo Go 안의 Hermes가 JavaScript 실행
       ↓
7. package.json의 expo-router/entry에서 Expo Router 시작
       ↓
8. app/_layout.tsx와 app/index.tsx 실행
       ↓
9. React가 필요한 UI를 계산하고 React Native가 네이티브 화면에 반영
```

---

## 공식 참고 문서

- [React Native 공식 소개](https://reactnative.dev/)
- [Expo와 React Native의 차이](https://docs.expo.dev/faq/)
- [Expo 핵심 개념](https://docs.expo.dev/core-concepts/)
- [Expo 앱의 개발·빌드·배포 흐름](https://docs.expo.dev/workflow/overview/)
- [Expo 개발 모드와 프로덕션 모드](https://docs.expo.dev/workflow/development-mode/)
