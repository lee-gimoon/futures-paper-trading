# Expo 모바일 앱 초기 설정과 프로젝트 파일 설명

이 문서는 Expo 모바일 앱 프로젝트를 처음 구성할 때 `mobile/`에 생성되는 설정 파일과 폴더가 왜 필요한지 설명한다.

처음에는 다음 관계만 기억하면 된다.

```text
app.json          → 휴대폰 앱 자체의 설정
package.json      → 프로젝트 실행 명령어와 필요한 패키지 목록
package-lock.json → 실제로 설치된 패키지 버전의 상세 기록
tsconfig.json     → TypeScript 코드 검사 규칙
app/              → 우리가 직접 만드는 화면 코드
.expo/            → Expo가 실행 중 자동으로 만드는 임시 작업 폴더
node_modules/     → npm이 내려받은 패키지의 실제 코드
```

---

## Expo란 무엇인가?

Expo는 React Native 앱을 더 쉽게 개발하고 실행하도록 도와주는 **개발 도구와 라이브러리 묶음**이다.

React Native만 사용하면 Android와 iOS의 네이티브 설정을 직접 다뤄야 하는 경우가 많다. Expo는 앱 실행, 개발 서버, 휴대폰 연결, 앱 설정, 빌드 같은 작업을 한 가지 방식으로 묶어 준다.

현재 프로젝트에서 Expo가 하는 주요 일은 다음과 같다.

- `npm run start` 명령으로 개발 서버를 실행한다.
- 작성한 TypeScript와 React Native 코드를 휴대폰에서 실행할 수 있게 준비한다.
- `app.json`을 읽어 앱 이름과 Android 설정을 적용한다.
- Expo Router를 실행해 `app/` 폴더의 화면을 찾는다.
- 나중에는 Android APK와 AAB 빌드도 도와준다.

Expo는 프로그래밍 언어가 아니다. JavaScript나 TypeScript로 React Native 코드를 작성하고, Expo는 그 코드를 개발·실행·빌드하도록 도와준다.

### Expo, Expo Go, Metro의 차이

| 이름 | 역할 |
|---|---|
| Expo | React Native 앱 개발을 도와주는 전체 도구와 라이브러리 생태계다. |
| Expo CLI | `expo start` 같은 명령을 실행하는 프로그램이다. |
| Expo Go | 개발 중인 앱을 휴대폰에서 빠르게 열어 보는 테스트용 앱이다. |
| Metro | 프로젝트의 여러 TypeScript·JavaScript 파일을 휴대폰이 실행할 수 있게 묶는 개발 서버다. |

실행 흐름은 다음과 같다.

```text
npm run start
    ↓
Expo CLI가 Metro 개발 서버 실행
    ↓
터미널에 QR 코드 표시
    ↓
휴대폰의 Expo Go로 QR 코드 스캔
    ↓
Expo Go가 Metro에서 앱 코드를 받음
    ↓
Expo Router가 app/_layout.tsx와 app/index.tsx 실행
    ↓
휴대폰에 첫 화면 표시
```

---

## `app.json`: 휴대폰 앱 설정

`app.json`은 TypeScript 코드가 아니라 **앱 자체의 정보와 네이티브 설정**을 기록하는 파일이다.

핵심 부분:

```json
{
  "expo": {
    "name": "Futures Paper Trading",
    "slug": "futures-paper-trading-mobile",
    "version": "1.0.0",
    "orientation": "portrait",
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

| 코드 | 의미 |
|---|---|
| `expo` | Expo 앱 설정은 이 객체 안에 작성한다. |
| `name` | 휴대폰과 앱 화면에 표시할 앱 이름이다. |
| `slug` | Expo 서비스에서 프로젝트를 구분하는 이름이다. 공백 없이 작성한다. |
| `version` | 사용자에게 표시되는 앱 버전이다. |
| `orientation` | 앱 화면을 세로 방향으로 고정한다. |
| `userInterfaceStyle` | 앱의 기본 밝기 테마다. 현재는 어두운 테마다. |
| `android.package` | Android가 앱을 구분하는 고유 식별자다. |
| `plugins` | Expo에 추가로 적용할 기능이다. 현재 Expo Router를 등록했다. |

`app.json`은 일반 JSON 파일이므로 `// 주석`을 작성할 수 없다. 설정 설명은 이 문서에서 확인한다.

---

## `package.json`: 패키지 목록과 실행 명령어

`package.json`은 이 프로젝트를 실행하는 방법과 필요한 npm 패키지를 기록한다.

핵심 부분:

```json
{
  "main": "expo-router/entry",
  "scripts": {
    "start": "expo start",
    "android": "expo start --android",
    "typecheck": "tsc --noEmit",
    "lint": "expo lint"
  },
  "dependencies": {
    "expo": "~54.0.36",
    "expo-router": "~6.0.24",
    "react": "19.1.0",
    "react-native": "0.81.5"
  }
}
```

### `main`

```json
"main": "expo-router/entry"
```

앱 실행을 Expo Router에서 시작한다는 뜻이다. Expo Router는 `app/` 폴더에서 화면 파일을 찾는다.

### `scripts`

긴 명령어에 짧은 이름을 붙이는 곳이다.

| 실행 명령 | 실제 동작 |
|---|---|
| `npm run start` | `expo start`를 실행해 개발 서버를 연다. |
| `npm run android` | 개발 서버를 열고 Android 앱 실행을 시도한다. |
| `npm run typecheck` | TypeScript 코드에 타입 오류가 있는지 검사한다. |
| `npm run lint` | 코드 작성 규칙과 실수를 검사한다. |

### `dependencies`

앱을 실행할 때 필요한 패키지 목록이다.

| 패키지 | 역할 |
|---|---|
| `expo` | Expo의 핵심 기능을 제공한다. |
| `react` | 컴포넌트와 상태 같은 React 기능을 제공한다. |
| `react-native` | `View`, `Text`, `Pressable` 같은 모바일 UI를 제공한다. |
| `expo-router` | 화면 파일과 화면 이동을 관리한다. |
| `expo-status-bar` | 휴대폰 상단 상태 표시줄을 설정한다. |
| `react-native-safe-area-context` | 카메라 구멍이나 시스템 영역에 UI가 가리지 않게 한다. |
| `react-native-screens` | 화면 이동을 네이티브 화면 방식으로 효율적으로 처리한다. |

### `devDependencies`

앱 기능 자체보다 개발과 코드 검사에 필요한 패키지다. TypeScript, 타입 정보, ESLint 등이 여기에 들어 있다.

---

## `package-lock.json`: 실제 설치 버전 기록

`package-lock.json`은 `npm install`을 실행하면 npm이 자동으로 만든다.

`package.json`이 필요한 패키지의 **요청 목록**이라면, `package-lock.json`은 설치된 모든 패키지와 하위 패키지의 **정확한 결과 목록**이다.

예를 들어 다음 설정은 패치 버전 범위 안에서 설치할 수 있다는 뜻이다.

```json
"expo": "~54.0.36"
```

실제로 어떤 버전이 설치됐는지는 `package-lock.json`에 정확히 기록된다. 다른 컴퓨터에서도 가능한 한 같은 패키지 조합을 설치하게 해 준다.

- 내용이 매우 긴 것이 정상이다.
- 사람이 직접 수정하지 않는다.
- Git에는 포함한다.
- 패키지를 설치하거나 제거하면 npm이 자동으로 갱신한다.

---

## `tsconfig.json`: TypeScript 검사 설정

`tsconfig.json`은 TypeScript가 어떤 파일을 검사하고 얼마나 엄격하게 검사할지 정한다.

핵심 부분:

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

| 코드 | 의미 |
|---|---|
| `extends` | Expo가 미리 준비한 기본 TypeScript 설정을 사용한다. |
| `strict` | 잘못된 타입과 빠진 값 등을 엄격하게 검사한다. |
| `paths` | 나중에 `src/` 안의 파일을 `@/파일경로` 형태로 가져올 수 있게 한다. |
| `include` | TypeScript가 검사할 파일의 범위를 지정한다. |

이 파일도 JSON이므로 주석을 작성할 수 없다.

---

## `.expo/`: Expo가 자동으로 만드는 로컬 작업 폴더

`.expo/`는 `expo start`, `expo export` 같은 명령을 실행하면 Expo가 자동으로 만든다. 이름 앞의 점(`.`)은 일반적으로 숨김 폴더라는 뜻이다.

이 폴더에는 개발 서버 캐시와 이 컴퓨터에서만 필요한 임시 정보가 들어갈 수 있다.

중요한 규칙:

- 우리가 앱 기능 코드를 작성하는 폴더가 아니다.
- 직접 파일을 만들거나 수정하지 않는다.
- Git에 올리지 않는다. 현재 `.gitignore`에 등록되어 있다.
- 문제가 생겨 삭제해도 Expo 명령을 다시 실행하면 생성된다.

`package.json`의 `expo` 패키지와 `.expo/` 폴더는 서로 다른 것이다.

```text
"expo" 패키지 → 앱 개발에 사용하는 프로그램 코드
.expo/ 폴더    → 그 프로그램이 실행 중 만든 로컬 임시 데이터
```

---

## `node_modules/`: 설치된 패키지 코드

`node_modules/`는 `npm install`이 `package.json`과 `package-lock.json`을 읽고 내려받은 실제 패키지 코드가 저장되는 폴더다.

- 크기가 매우 크고 파일이 많은 것이 정상이다.
- 직접 수정하지 않는다.
- Git에 올리지 않는다.
- 삭제해도 `npm install`로 다시 만들 수 있다.

```text
package.json + package-lock.json
              ↓ npm install
         node_modules/
```

---

## JSON이 아닌 나머지 주요 파일

| 파일 | 역할 |
|---|---|
| `app/_layout.tsx` | Expo Router가 가장 먼저 실행하며 전체 화면 전환 구조를 만든다. |
| `app/index.tsx` | 앱을 열었을 때 보이는 첫 화면 코드다. |
| `eslint.config.js` | ESLint가 코드를 어떤 규칙으로 검사할지 정한다. |
| `.gitignore` | Git에 포함하지 않을 자동 생성 파일과 폴더를 정한다. |

현재 직접 자주 수정할 파일은 `app/index.tsx`다. 설정을 바꿀 일이 있을 때 `app.json`과 `package.json`을 수정하고, `package-lock.json`, `.expo/`, `node_modules/`는 도구가 관리하게 둔다.
