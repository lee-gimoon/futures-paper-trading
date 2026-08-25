# Expo 모바일 앱 초기 설정 설명

이 문서는 `mobile/`에 Expo 프로젝트를 처음 만들면서 **현재 실제로 생성된 폴더와 설정 파일만** 설명한다.

로그인, 시세, 주문처럼 아직 구현하지 않은 기능은 다루지 않는다. 새로운 기능은 실제 파일을 만들 때 학습 계획에 따라 하나씩 추가한다.

---

## 먼저: React Native와 Expo는 무엇이고 왜 함께 쓰나?

### “React Native 앱”이라는 말의 뜻

네. **React Native를 사용해 Android 또는 iOS 화면을 만드는 앱**을 React Native 앱이라고 부른다.

웹 React에서는 `div`, `button` 같은 HTML 요소를 화면에 그린다. React Native에서는 `View`, `Text`, `Pressable` 같은 컴포넌트를 사용한다.

큰 실행 흐름은 다음과 같다.

```text
React 웹
내 JavaScript 코드 → Chrome의 V8 엔진이 실행 → react-dom이 브라우저 화면 반영 → HTML 화면 표시

React Native 앱
내 JavaScript 코드 → Hermes 엔진이 실행 → React Native가 네이티브 UI 반영 → 운영체제가 화면 표시
```

현재 프로젝트의 개발 환경에서는 TypeScript와 TSX를 바로 실행하지 않는다. Metro가 이를 JavaScript로 변환하고 묶은 다음, 휴대폰 안의 Hermes JavaScript 엔진이 실행한다.

```text
작성한 TypeScript/TSX 코드 (`<View>`, `<Text>` 등)
    ↓
Metro가 JavaScript로 변환하고 앱 코드 묶음 생성
    ↓
Hermes JavaScript 엔진이 그 JavaScript 코드 실행
    ↓
React가 현재 상태를 기준으로 어떤 UI가 필요한지 계산
    ↓
React Native의 네이티브 코드가 Android/iOS 화면 시스템에 UI 변경 반영
    ↓
Android: Android 화면 시스템으로 표시
iPhone: iOS 화면 시스템으로 표시
```

여기서 “반영” 또는 “표시 요청”은 서버로 보내는 HTTP 요청이 아니다. 휴대폰 앱 내부에서 JavaScript 쪽의 화면 변경 결과를 Android/iOS의 실제 화면 요소에 적용하는 과정이다.

개발 중에는 Expo Go 앱 안에 Hermes와 React Native의 네이티브 실행 환경이 들어 있다. Metro가 우리 앱의 JavaScript 코드를 Expo Go에 전달하면 그 안에서 실행된다. 나중에 APK/AAB로 빌드하면 Expo Go 대신 우리가 만든 앱 안에 같은 실행 환경이 포함된다.

따라서 이 프로젝트는 Expo를 사용하더라도 React Native 위에서 실행되므로, 정확히는 **“Expo 도구를 사용하는 React Native 앱”**이다.

### 라이브러리와 프레임워크의 차이

경계가 항상 딱 나뉘지는 않지만, 처음에는 아래처럼 이해하면 된다.

| 구분 | 중심 역할 | 누가 전체 흐름을 정하는가? |
|---|---|---|
| 라이브러리 | 필요한 기능을 가져다 쓴다. | 개발자가 앱 구조와 사용 시점을 주로 정한다. |
| 프레임워크 | 앱을 만들 기본 구조와 실행 흐름을 제공한다. | 프레임워크가 정한 규칙과 구조 안에서 개발한다. |

### React Native는 라이브러리인가, 프레임워크인가?

React Native 공식 문서는 React Native를 **네이티브 사용자 인터페이스를 만들기 위한 JavaScript 라이브러리**라고 설명한다. `View`, `Text`, `Image` 같은 네이티브 UI 컴포넌트와 Android/iOS에서 React 코드를 실행하는 기반을 제공한다.

다만 React Native만으로는 실제 앱에 필요한 여러 결정을 직접 해야 한다. 예를 들어 화면 이동 방식, 파일 구조, 카메라·위치 같은 기기 기능 접근, 개발 서버·빌드 설정을 어떤 도구로 할지 정해야 한다. 그래서 React Native 공식 문서도 새 앱에는 Expo 같은 프레임워크 사용을 권장한다.

### Expo는 라이브러리인가, 프레임워크인가?

Expo 전체는 **React Native 프레임워크**다. React Native 앱을 만드는 기본 구조와 개발 흐름을 제공한다.

하지만 Expo 안에는 여러 종류가 함께 있다.

| Expo 구성 | 종류 | 예시 역할 |
|---|---|---|
| Expo 프레임워크 | 프레임워크 | 프로젝트 구조와 React Native 개발 흐름을 제공한다. |
| Expo SDK | 라이브러리 모음 | 카메라, 위치, 알림 등 기기 기능을 가져다 쓴다. |
| Expo Router | 라이브러리 | `app/` 파일을 기준으로 화면 이동을 관리한다. |
| Expo CLI | 개발 도구 | `expo start`로 개발 서버를 실행한다. |
| Expo Go | 테스트용 앱 | 개발 중인 코드를 휴대폰에서 연다. |
| EAS | 선택 가능한 클라우드 서비스 | 앱 빌드·업데이트·스토어 제출을 돕는다. |

즉 `package.json`의 `expo`는 설치하는 **npm 패키지** 이름이고, Expo는 그 패키지·SDK·CLI·Router 등을 포함한 **프레임워크와 도구 생태계 전체 이름**이기도 하다.

### 왜 React Native와 Expo를 둘 다 쓰나?

둘 중 하나를 고르는 것이 아니다. 역할이 다르다.

```text
TypeScript로 화면 코드 작성
        ↓
React Native
→ View, Text, Pressable을 Android/iOS 화면 시스템에 표시하도록 요청
        ↓
Expo
→ 프로젝트 생성, 개발 서버, Expo Go, Router, 기기 기능 라이브러리,
  나중의 앱 빌드와 배포를 더 쉽게 처리
```

이 프로젝트는 React Native만으로 모든 도구를 직접 고르기보다, Expo가 미리 잘 맞춰 둔 개발 환경을 사용한다. 그래서 처음에는 화면과 기능 코드에 더 집중할 수 있다.

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

## Expo가 현재 프로젝트에서 하는 일

Expo는 프로그래밍 언어가 아니다. 화면과 동작은 TypeScript로 작성하고, React Native가 휴대폰 UI를 만든다. Expo는 그 React Native 앱을 쉽게 개발하고 실행하도록 돕는다.

현재 프로젝트에서 Expo가 하는 일은 다음과 같다.

- 개발 서버를 실행한다.
- 작성한 TypeScript 코드를 휴대폰에서 실행할 수 있게 준비한다.
- `app.json`을 읽어 앱 이름과 Android 설정을 적용한다.
- Expo Router를 실행해 `app/`에서 화면 파일을 찾는다.
- Expo Go를 통해 개발 중인 앱을 실제 휴대폰에서 확인하게 한다.
- 나중에 Android APK와 Google Play용 AAB 빌드를 만드는 일을 도와준다.

### Expo, Expo Go, Metro의 차이

| 이름 | 역할 |
|---|---|
| Expo | React Native 앱 개발을 도와주는 전체 도구와 생태계다. |
| Expo CLI | `expo start` 같은 Expo 명령을 실행하는 프로그램이다. |
| Expo Go | 개발 중인 앱을 휴대폰에서 빠르게 열어 보는 테스트용 앱이다. |
| Metro | 여러 TypeScript 파일을 휴대폰이 실행할 수 있게 묶어 전달하는 개발 서버다. |

---

## `.expo/` 폴더를 만든 이유

`.expo/`는 우리가 직접 만든 앱 기능 폴더가 아니다. `expo start`, `expo export` 같은 명령을 실행했기 때문에 **Expo CLI가 자동으로 만든 폴더**다.

이름 앞의 점(`.`)은 일반적으로 숨김 폴더를 의미한다. `.expo/`에는 개발 서버 캐시처럼 이 컴퓨터에서 Expo가 작업하는 데 필요한 임시 정보가 들어간다.

```text
npm run start
    ↓
Expo CLI 실행
    ↓
Metro 개발 서버 실행
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
3. Expo CLI가 app.json을 읽고 Metro 개발 서버 실행
       ↓
4. Expo Go가 QR 코드를 통해 Metro에 연결
       ↓
5. package.json의 expo-router/entry에서 앱 시작
       ↓
6. app/_layout.tsx 실행
       ↓
7. app/index.tsx 첫 화면 표시
```

---

## 공식 참고 문서

- [React Native 공식 소개](https://reactnative.dev/)
- [Expo와 React Native의 차이](https://docs.expo.dev/faq/)
- [Expo 핵심 개념](https://docs.expo.dev/core-concepts/)
