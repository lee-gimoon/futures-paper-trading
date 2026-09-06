# Futures Paper Trading 모바일 앱 구현형 학습 계획

이 문서는 기존 Spring Boot 서버와 PostgreSQL을 그대로 사용하면서 `mobile/`에 React Native + Expo 모바일 앱을 만드는 구현 계획이다.

개념을 모두 외운 뒤 개발을 시작하지 않는다. 각 단계에서 실제 파일을 만들고 Android 기기에서 확인하면서, 그 단계에 필요한 React Native와 Expo Router 개념을 함께 익힌다.

## 현재 진행 상태

- 1단계 코드 구현 완료
- 2단계 코드 구현 완료
- Expo SDK 57 업그레이드 완료
- 다음 구현 대상: 3단계 화면 이동과 하단 탭
- 실제 Android 기기에서는 2단계 화면의 버튼·입력·키보드 대응을 확인한다.
- `ScrollView`와 `FlatList`는 내용이 표시 영역보다 길 때만 스크롤되는 것이 정상이다.

---

## 프로젝트 전체 연결 구조

```text
React 웹 ─────────┐
                  ├─ Railway Spring Boot ─ PostgreSQL
React Native 앱 ──┘          │
                              └─ Binance 시세
```

운영 API 기본 주소:

```text
https://futures-paper-trading-production.up.railway.app
```

기존 React 웹, Spring Controller/Service/Repository, PostgreSQL과 거래 엔진은 모바일 화면을 만들기 위해 임의로 변경하지 않는다. 모바일 앱은 기존 서버가 제공하는 API를 사용한다.

---

## 모든 단계에 적용하는 파일 분리 기준

### `app/`은 화면 경로를 연결한다

Expo Router는 `app/` 폴더의 파일과 폴더 이름을 경로로 읽는다. 따라서 `app/`에는 경로 연결과 내비게이션 설정에 필요한 최소한의 코드만 둔다.

예를 들어 `app/(tabs)/trade.tsx`는 거래 화면 전체를 직접 구현하지 않고 실제 화면 컴포넌트를 불러와 반환한다.

```tsx
import { TradeScreen } from '@/features/trade/TradeScreen';

export default function TradeRoute() {
  return <TradeScreen />;
}
```

### `src/features/`는 실제 화면과 기능을 구현한다

기능별 화면 JSX, 그 화면에서 함께 사용하는 상태와 기능 전용 Hook을 둔다.

```text
src/features/
├─ home/       # 첫 진입 화면
├─ auth/       # 로그인·회원가입·인증 상태
├─ market/     # 시세와 실시간 호가
├─ trade/      # 주문 입력과 전송
├─ orders/     # 주문·체결 내역
└─ account/    # 계좌와 포지션
```

`TradeScreen`처럼 한 화면 전체를 담당하는 컴포넌트가 화면 상태를 가질 수 있다. 화면이 커지면 상태 로직을 같은 기능 폴더의 Hook으로 나누고, 화면 일부를 기능 전용 컴포넌트로 나눈다.

### `src/components/`는 여러 기능에서 재사용하는 UI를 구현한다

`AppButton`, `PriceRow`, 로딩·오류 표시처럼 둘 이상의 화면에서 공통으로 사용할 수 있는 UI를 둔다. 특정 기능에서만 사용하는 컴포넌트는 해당 `src/features/<기능>/components/`에 둔다.

### 그 밖의 `src/` 폴더 책임

```text
src/
├─ api/          # Spring API 요청과 공통 HTTP 처리
├─ components/   # 여러 기능에서 재사용하는 UI
├─ features/     # 기능별 화면·상태·Hook·전용 컴포넌트
├─ hooks/        # 여러 기능에서 공통으로 사용하는 Hook만 배치
├─ theme/        # 색상·간격·글자 크기 같은 디자인 값
├─ types/        # 여러 기능에서 공유하는 API 데이터 타입
└─ utils/        # 가격·수량 포맷 같은 순수 공통 함수
```

기능 하나에서만 사용하는 타입이나 Hook은 가능한 한 그 기능 폴더 가까이에 둔다. 실제로 여러 기능에서 함께 사용하게 되었을 때 `src/types`, `src/hooks` 또는 `src/components`로 옮긴다.

### 화면, 경로 파일과 재사용 컴포넌트의 차이

- 경로 파일: Expo Router가 주소와 연결하는 `app/*.tsx` 파일이다.
- 화면 컴포넌트: 한 화면의 내용과 상태를 담당하는 `src/features/*Screen.tsx` 파일이다.
- 재사용 컴포넌트: 화면이 전달한 props에 따라 작은 UI를 표시하는 파일이다.
- API 파일: 서버에 요청하는 방법만 담당하며 화면 JSX를 작성하지 않는다.
- Hook: 여러 상태와 부수 효과를 화면에서 사용하기 쉬운 형태로 묶는다.

---

## 목표 구조

아래 구조를 처음부터 한꺼번에 만들지 않는다. 각 단계에서 실제로 필요한 폴더와 파일만 추가한다.

```text
mobile/
├─ app/                              # Expo Router 경로와 레이아웃
│  ├─ _layout.tsx                    # 앱 전체 Stack 레이아웃
│  ├─ index.tsx                      # `/` 경로 연결
│  ├─ login.tsx                      # `/login` 경로 연결
│  ├─ signup.tsx                     # `/signup` 경로 연결
│  └─ (tabs)/                        # 주소에는 나타나지 않는 탭 그룹
│     ├─ _layout.tsx                 # 하단 Tabs 레이아웃
│     ├─ market.tsx                  # 시세 화면 연결
│     ├─ trade.tsx                   # 거래 화면 연결
│     ├─ orders.tsx                  # 주문·체결 화면 연결
│     └─ account.tsx                 # 계좌 화면 연결
├─ src/
│  ├─ api/
│  ├─ components/
│  ├─ features/
│  ├─ hooks/
│  ├─ theme/
│  ├─ types/
│  └─ utils/
├─ assets/
├─ app.json
├─ package.json
├─ tsconfig.json
└─ REACT_NATIVE_STUDY_PLAN.md
```

`app/_layout.tsx`와 `app/(tabs)/_layout.tsx`는 이름이 같지만 위치와 역할이 다르다.

- `app/_layout.tsx`의 `RootLayout`: 앱 전체 경로를 Stack으로 관리한다.
- `app/(tabs)/_layout.tsx`의 `TabsLayout`: 탭 그룹 안의 네 화면을 Tabs로 관리한다.

Expo Router가 레이아웃 파일로 인식하려면 두 파일 모두 이름이 `_layout.tsx`여야 한다.

---

# 1단계. Expo 프로젝트와 첫 경로를 실행한다 — 코드 완료

Expo + TypeScript + Expo Router 프로젝트를 만들고, 앱 실행 진입점과 첫 경로가 연결되는 과정을 확인한다.

## 만든 파일

```text
mobile/
├─ app/
│  ├─ _layout.tsx
│  └─ index.tsx
├─ assets/
├─ app.json
├─ package.json
└─ tsconfig.json
```

## 파일별 역할

| 파일 | 역할 |
|---|---|
| `package.json` | Expo와 React Native 패키지, `start`, `android`, `lint`, `typecheck` 명령을 기록한다. |
| `app.json` | 앱 이름, Android 패키지 이름, Expo Router 플러그인 같은 앱 설정을 기록한다. |
| `app/_layout.tsx` | 앱 전체에 적용되는 RootLayout과 Stack, StatusBar를 렌더링한다. |
| `app/index.tsx` | 기본 경로 `/`를 첫 화면 컴포넌트에 연결한다. |
| `tsconfig.json` | TypeScript 검사 기준과 `@/` 경로 별칭을 설정한다. |

## 구현한 내용

- `package.json`의 `main`을 `expo-router/entry`로 설정했다.
- 앱을 일반 실행하면 Expo Router가 기본 경로 `/`와 `app/index.tsx`를 연결한다.
- Expo Router가 먼저 공통 `app/_layout.tsx`의 `RootLayout`을 사용하고, 그 안의 Stack이 현재 경로 화면을 렌더링한다.
- StatusBar와 안전 영역을 포함한 기본 Android 화면을 확인했다.
- Expo SDK를 현재 Expo Go와 호환되는 SDK 57로 업그레이드했다.

## 코드로 익힌 내용

- `View`, `Text`, `Pressable`은 각각 영역, 글자, 누를 수 있는 UI를 만든다.
- React Native는 HTML의 `div`, `button` 대신 네이티브 UI 컴포넌트를 사용한다.
- `_layout.tsx`가 `index.tsx`를 직접 import하지 않아도 Expo Router가 파일 경로를 읽어 활성 화면을 Stack에 넣는다.
- `app/index.tsx`는 첫 경로 파일이며, 실제 화면을 다른 컴포넌트에 위임할 수 있다.

## 확인 기준

- Android에서 기본 경로 `/` 화면이 열린다.
- 코드를 수정하면 Fast Refresh로 변경 사항이 반영된다.
- `app/_layout.tsx`와 `app/index.tsx`의 역할 차이를 설명할 수 있다.

---

# 2단계. 거래 화면과 재사용 UI를 분리한다 — 코드 완료

서버에 연결하지 않고 거래 화면의 입력 상태와 로컬 고정 호가를 만든다. 경로 파일, 실제 화면 컴포넌트, 재사용 컴포넌트와 테마를 분리한다.

## 만든 파일

```text
mobile/
├─ app/
│  └─ index.tsx
└─ src/
   ├─ components/
   │  ├─ AppButton.tsx
   │  └─ PriceRow.tsx
   ├─ features/
   │  └─ trade/
   │     └─ TradeScreen.tsx
   └─ theme/
      └─ colors.ts
```

## 파일별 역할

| 파일 | 역할 |
|---|---|
| `app/index.tsx` | `/` 경로에서 `TradeScreen`을 반환하는 얇은 경로 파일이다. |
| `src/features/trade/TradeScreen.tsx` | 거래 화면 전체 JSX와 주문 방식·수량·결과 메시지 상태를 담당한다. |
| `src/components/AppButton.tsx` | label, variant, selected, onPress props에 따라 공통 버튼을 표시한다. |
| `src/components/PriceRow.tsx` | FlatList의 각 호가 항목을 가격·수량·매수/매도 구분에 맞춰 표시한다. |
| `src/theme/colors.ts` | 여러 화면과 컴포넌트에서 반복하는 색상을 한곳에 둔다. |

## 구현한 내용

```text
BTCUSDT
현재가: 00,000.00 USDT

[시장가] [지정가]
[수량 입력] BTC
[매수] [매도]

호가 목록
```

- `useState`로 시장가/지정가 선택, 수량 입력과 결과 메시지를 관리한다.
- 두 주문 방식 버튼은 항상 렌더링하고, 현재 `orderType`과 일치하는 버튼만 선택된 모양으로 표시한다.
- 매수/매도 버튼은 입력값을 검사한 뒤 로컬 결과 메시지만 표시한다.
- `FlatList`가 고정 호가 배열의 각 항목을 `PriceRow`에 전달한다.
- `StyleSheet.create()`와 Flexbox로 세로·가로 배치를 만든다.
- `SafeAreaView`로 상태 표시줄과 화면 내용이 겹치지 않게 한다.
- 키보드가 입력 영역을 가리는 상황에 대비해 `KeyboardAvoidingView`와 `ScrollView`를 사용한다.

## 코드로 익힌 내용

- `useState`는 화면에서 바뀌는 값을 기억하고, 상태가 바뀌면 React가 화면을 다시 렌더링한다.
- props는 부모 컴포넌트가 자식 컴포넌트에 값과 콜백 함수를 전달하는 방법이다.
- `AppButton` 자체는 주문 상태를 관리하지 않고 부모가 전달한 props에 따라 모양과 동작이 달라진다.
- `Pressable`의 style 함수가 받는 `pressed`는 버튼을 누르는 동안 `true`가 되는 기본 상태값이다.
- `FlatList`는 `data`의 각 항목을 내부적으로 `renderItem`의 `item`에 전달한다.
- 한 `PriceRow` 정의를 데이터 개수만큼 반복 사용하므로 호가 행마다 파일을 만들지 않는다.

## 확인 기준

- 수량을 입력할 수 있다.
- 시장가/지정가 버튼을 누르면 선택된 버튼의 모양이 바뀐다.
- 매수/매도 버튼을 누르면 입력 검사 결과가 화면에 표시된다.
- 작은 화면 또는 키보드가 열린 상태에서 위쪽 내용이 영역보다 길어지면 주문 입력 영역을 스크롤할 수 있다.
- 호가 데이터가 목록 영역보다 많아지면 FlatList를 스크롤할 수 있다.
- 현재처럼 모든 내용이 표시 영역 안에 들어오면 스크롤되지 않는 것이 정상임을 이해한다.

---

# 3단계. Expo Router Stack과 하단 Tabs로 화면을 이동한다 — 다음 구현

현재 `/`에 임시로 연결한 거래 화면을 거래 탭으로 옮기고, 첫 화면·인증 화면·네 개의 탭 화면을 경로로 나눈다. 서버 인증은 아직 연결하지 않는다.

## 만들거나 수정할 파일

```text
mobile/
├─ app/
│  ├─ _layout.tsx                    # 기존 RootLayout 수정
│  ├─ index.tsx                      # HomeScreen 연결
│  ├─ login.tsx                      # LoginScreen 연결
│  ├─ signup.tsx                     # SignupScreen 연결
│  └─ (tabs)/
│     ├─ _layout.tsx                 # 새 TabsLayout
│     ├─ market.tsx                  # MarketScreen 연결
│     ├─ trade.tsx                   # 기존 TradeScreen 연결
│     ├─ orders.tsx                  # OrdersScreen 연결
│     └─ account.tsx                 # AccountScreen 연결
└─ src/
   └─ features/
      ├─ home/
      │  └─ HomeScreen.tsx
      ├─ auth/
      │  ├─ LoginScreen.tsx
      │  └─ SignupScreen.tsx
      ├─ market/
      │  └─ MarketScreen.tsx
      ├─ trade/
      │  └─ TradeScreen.tsx          # 2단계 파일 재사용
      ├─ orders/
      │  └─ OrdersScreen.tsx
      └─ account/
         └─ AccountScreen.tsx
```

## 파일별 역할

| 파일 | 역할 |
|---|---|
| `app/_layout.tsx` | 앱 전체 Stack과 StatusBar를 관리한다. 기존 파일을 다시 만들지 않는다. |
| `app/index.tsx` | `/` 경로와 `HomeScreen`을 연결한다. |
| `app/login.tsx`, `app/signup.tsx` | 인증 경로와 실제 인증 화면 컴포넌트를 연결한다. |
| `app/(tabs)/_layout.tsx` | 시세·거래·주문내역·계정 화면을 하단 Tabs로 묶는 새 레이아웃 파일이다. |
| `app/(tabs)/*.tsx` | 각 탭 경로와 `src/features`의 실제 화면을 연결한다. |
| `src/features/*/*Screen.tsx` | 사용자가 보는 실제 화면 JSX와 해당 화면의 로컬 상태를 담당한다. |

## 레이아웃 실행 관계

앱을 처음 열면:

```text
app/_layout.tsx의 RootLayout
→ Stack
→ app/index.tsx
→ src/features/home/HomeScreen.tsx
```

시세 탭으로 들어가면:

```text
app/_layout.tsx의 RootLayout
→ Stack
→ app/(tabs)/_layout.tsx의 TabsLayout
→ Tabs
→ app/(tabs)/market.tsx
→ src/features/market/MarketScreen.tsx
```

`app/_layout.tsx`는 항상 전체 화면을 감싸고, `app/(tabs)/_layout.tsx`는 사용자가 탭 그룹에 들어왔을 때만 그 안의 화면들을 감싼다.

## 구현할 내용

- 첫 화면 버튼에서 `router.push('/login')`으로 로그인 화면을 연다.
- 로그인 화면에서 `router.push('/signup')`으로 회원가입 화면을 연다.
- 로그인 화면의 임시 진입 버튼으로 탭 그룹을 연다.
- 실제 로그인 성공 여부와 서버 요청은 아직 구현하지 않는다.
- 시세·주문내역·계정 화면에는 제목과 현재 역할을 알 수 있는 임시 내용을 표시한다.
- 거래 탭은 2단계에서 만든 `TradeScreen`을 그대로 재사용한다.
- Android 시스템 뒤로가기로 Stack의 이전 화면에 돌아가는지 확인한다.
- 하단 탭을 누르면 네 화면 사이를 이동하는지 확인한다.

## 코드로 익힐 내용

- `router.push()`는 새 화면을 Stack 기록에 추가한다.
- `router.replace()`는 현재 화면을 다른 화면으로 바꾸어 이전 화면 기록을 남기지 않을 때 사용한다.
- `(tabs)`처럼 괄호로 묶은 Route Group 이름은 주소에 나타나지 않는다.
- `<Stack />`과 `<Tabs />`는 현재 경로에 맞는 자식 경로 화면을 Expo Router로부터 받아 렌더링한다.
- 같은 이름의 `_layout.tsx`라도 어느 폴더에 있는지에 따라 적용 범위가 달라진다.

## 완료 기준

- 첫 화면, 로그인, 회원가입과 네 개의 탭 화면 사이를 이동할 수 있다.
- 거래 탭에서 기존 `TradeScreen`이 그대로 동작한다.
- RootLayout과 TabsLayout의 차이를 설명할 수 있다.
- `app` 경로 파일과 `src/features` 화면 파일의 책임을 설명할 수 있다.

---

# 4단계. Spring 공개 시세 API를 시세 화면에 연결한다

화면 컴포넌트 안에 API 주소와 `fetch()`를 직접 흩어 쓰지 않는다. 인증이 필요 없는 최신 호가 API부터 연결해 모바일 앱과 Railway 서버의 통신을 확인한다.

## 만들거나 수정할 파일

```text
mobile/
├─ app/(tabs)/market.tsx              # 경로 연결 유지
└─ src/
   ├─ api/
   │  ├─ client.ts
   │  └─ marketApi.ts
   ├─ features/
   │  └─ market/
   │     ├─ MarketScreen.tsx
   │     └─ useLatestDepth.ts
   └─ types/
      └─ market.ts
```

## 파일별 역할

| 파일 | 역할 |
|---|---|
| `app/(tabs)/market.tsx` | 계속 `MarketScreen`을 반환하는 경로 파일로만 사용한다. |
| `src/api/client.ts` | 서버 기본 주소, 공통 요청 옵션과 공통 오류 변환을 담당한다. |
| `src/api/marketApi.ts` | 시세 관련 HTTP 요청 함수를 제공한다. |
| `src/types/market.ts` | 서버 호가 응답과 화면에서 사용할 호가 타입을 정의한다. |
| `src/features/market/useLatestDepth.ts` | 로딩·성공·오류·새로고침 상태를 묶어 `MarketScreen`에 제공한다. |
| `src/features/market/MarketScreen.tsx` | Hook이 제공한 상태에 따라 시세 UI를 렌더링한다. |

## 연결할 API

```http
GET /api/binance-futures/btcusdt/depth/latest
```

## 구현할 내용

- Railway 기본 주소는 `client.ts` 한곳에서 관리한다.
- `marketApi.ts`에 최신 호가 요청 함수를 만든다.
- 응답 JSON과 일치하는 TypeScript 타입을 만든다.
- `useLatestDepth`가 요청과 로딩·성공·오류 상태를 관리한다.
- `MarketScreen`은 받은 상태를 화면에 표시한다.
- 당겨서 새로고침 또는 새로고침 버튼을 추가한다.

## 코드로 익힐 내용

- `async`/`await`는 서버 응답을 기다리는 비동기 코드다.
- TypeScript 타입은 서버 응답의 필드 구조를 검사하고 문서화한다.
- 서버 상태는 API에서 다시 받아야 하는 데이터이며 입력창의 로컬 상태와 다르다.
- HTTP 오류 응답과 인터넷 연결 실패를 서로 구분해야 한다.

## 완료 기준

- Android 앱에서 Railway의 최신 호가를 받아 표시한다.
- API 주소와 `fetch()`가 화면 파일에 중복되지 않는다.
- 로딩, 성공, 서버 오류와 네트워크 오류 상태가 구분된다.

---

# 5단계. 로그인·회원가입과 앱 전체 인증 상태를 연결한다

3단계에서 모양과 이동만 만든 인증 화면에 실제 Spring SESSION 쿠키 인증을 연결한다. 인증 상태는 개별 경로 파일이 아니라 AuthProvider에서 앱 전체에 제공한다.

## 만들거나 수정할 파일

```text
mobile/
├─ app/
│  ├─ _layout.tsx
│  ├─ login.tsx                       # LoginScreen 연결 유지
│  └─ signup.tsx                      # SignupScreen 연결 유지
└─ src/
   ├─ api/
   │  ├─ client.ts
   │  ├─ csrf.ts
   │  └─ authApi.ts
   ├─ features/
   │  └─ auth/
   │     ├─ AuthProvider.tsx
   │     ├─ useAuth.ts
   │     ├─ LoginScreen.tsx
   │     └─ SignupScreen.tsx
   └─ types/
      └─ auth.ts
```

## 파일별 역할

| 파일 | 역할 |
|---|---|
| `src/api/csrf.ts` | CSRF 토큰 조회·보관·제거와 요청 헤더 구성을 담당한다. |
| `src/api/authApi.ts` | 회원가입, 로그인, 현재 사용자 조회와 로그아웃 요청을 제공한다. |
| `src/features/auth/AuthProvider.tsx` | 현재 사용자, 인증 확인 중 상태와 인증 동작을 앱 전체에 제공한다. |
| `src/features/auth/useAuth.ts` | 화면이 AuthProvider 값과 함수를 읽게 하는 Hook이다. |
| `LoginScreen.tsx`, `SignupScreen.tsx` | 입력 상태·검증·요청 결과를 관리하고 인증 UI를 렌더링한다. |
| `app/_layout.tsx` | AuthProvider로 앱을 감싸고 인증 상태에 맞는 경로 이동이 가능하게 한다. |

## 연결할 API

| 기능 | 요청 |
|---|---|
| CSRF 토큰 | `GET /api/auth/csrf` |
| 회원가입 | `POST /api/auth/signup` |
| 로그인 | `POST /api/auth/login` |
| 현재 사용자 | `GET /api/auth/me` |
| 로그아웃 | `POST /api/auth/logout` |

## 세션과 CSRF 처리

- 앱 시작 시 CSRF 토큰을 조회한다.
- 로그인·회원가입·로그아웃 요청에 서버가 알려 준 CSRF 헤더를 추가한다.
- 요청에는 세션 쿠키를 사용할 수 있도록 공통 client 설정을 적용한다.
- 로그인 후 같은 서버 세션의 CSRF 토큰을 다시 조회해 메모리 값과 맞춘다.
- 로그아웃 또는 세션 만료 뒤 사용자 상태와 CSRF 토큰을 함께 정리한다.
- SESSION 쿠키와 CSRF 토큰이 같은 세션으로 유지되는지 실제 Android 기기에서 확인한다.

## 구현할 내용

- 회원가입·로그인 입력값과 오류 문구를 만든다.
- 앱 시작 시 `/api/auth/me`로 현재 로그인 상태를 확인한다.
- 로그인 성공 후 탭 화면으로 이동한다.
- 로그인하지 않은 사용자가 보호 화면에 접근하면 로그인 화면으로 보낸다.
- 401 응답이면 사용자 정보를 비우고 로그인 화면으로 이동한다.
- 로그아웃하면 서버 세션과 모바일 인증 상태를 모두 정리한다.

React Native 환경에서 SESSION 쿠키 유지가 불안정하면 실제 기기에서 원인을 확인한 뒤 쿠키 관리 라이브러리와 Development Build 사용을 검토한다. 이 문제를 피하려고 Spring 인증 방식을 먼저 임의 변경하지 않는다.

## 완료 기준

- 회원가입, 로그인, 현재 사용자 조회와 로그아웃이 동작한다.
- 앱을 다시 열었을 때 세션 상태를 올바르게 확인한다.
- 인증이 필요한 화면을 로그인 상태에 따라 보호한다.
- 세션 만료 시 무한 요청 없이 로그인 화면을 표시한다.

---

# 6단계. 계좌·주문·체결 데이터를 조회한다

경로 파일은 그대로 두고 AccountScreen과 OrdersScreen에 실제 서버 데이터를 연결한다.

## 만들거나 수정할 파일

```text
mobile/
├─ app/(tabs)/
│  ├─ account.tsx                     # AccountScreen 연결 유지
│  └─ orders.tsx                      # OrdersScreen 연결 유지
└─ src/
   ├─ api/
   │  └─ paperApi.ts
   ├─ components/
   │  ├─ EmptyState.tsx
   │  ├─ ErrorState.tsx
   │  └─ LoadingState.tsx
   ├─ features/
   │  ├─ account/
   │  │  ├─ AccountScreen.tsx
   │  │  ├─ usePaperAccount.ts
   │  │  └─ components/
   │  │     └─ PositionCard.tsx
   │  └─ orders/
   │     ├─ OrdersScreen.tsx
   │     ├─ usePaperOrders.ts
   │     └─ components/
   │        └─ OrderRow.tsx
   ├─ types/
   │  └─ paper.ts
   └─ utils/
      └─ format.ts
```

## 파일별 역할

| 파일 | 역할 |
|---|---|
| `src/api/paperApi.ts` | 계좌, 포지션, 주문과 체결 관련 서버 요청을 제공한다. |
| `AccountScreen.tsx`, `OrdersScreen.tsx` | 각 탭의 서버 상태를 화면으로 조합한다. |
| `usePaperAccount.ts`, `usePaperOrders.ts` | 조회·새로고침·오류 상태를 기능별로 관리한다. |
| `PositionCard.tsx`, `OrderRow.tsx` | 해당 기능에서만 사용하는 복잡한 표시 UI를 나눈다. |
| `EmptyState`, `ErrorState`, `LoadingState` | 여러 화면에서 공통으로 사용하는 상태 UI다. |
| `format.ts` | 가격, 수량과 손익을 일관된 형식으로 변환한다. |

## 연결할 API

| 기능 | 요청 |
|---|---|
| 계좌·포지션 | `GET /api/paper/account` |
| 체결 내역 | `GET /api/paper/fills` |
| 주문 목록 | `GET /api/paper/orders` |
| 레버리지 변경 | `PUT /api/paper/account/leverage` |

## 구현할 내용

- 현금 잔액, 실현·미실현 손익, 평가자산과 사용 증거금을 표시한다.
- 포지션이 있으면 진입가와 청산 예상 가격을 표시한다.
- 대기 주문과 체결 내역을 FlatList로 표시한다.
- 데이터가 없을 때는 빈 상태 UI를 표시한다.
- 로딩·오류·새로고침 상태를 구분한다.
- 레버리지 변경 UI와 요청을 연결한다.

## 완료 기준

- 웹과 모바일에서 같은 계정으로 로그인하면 같은 잔액과 주문이 보인다.
- 포지션이나 주문이 없어도 오류 없이 빈 상태가 표시된다.
- 로딩, 빈 상태, 오류와 정상 데이터가 구분된다.

---

# 7단계. 실시간 호가를 SSE로 연결한다

4단계의 한 번 조회를 기존 Spring SSE 스트림의 지속적인 갱신으로 확장한다. 경로 파일이 아니라 MarketScreen과 TradeScreen이 공통 호가 UI와 실시간 상태를 사용한다.

## 만들거나 수정할 파일

```text
mobile/
├─ app/(tabs)/
│  ├─ market.tsx                     # 경로 연결 유지
│  └─ trade.tsx                      # 경로 연결 유지
└─ src/
   ├─ api/
   │  └─ marketStream.ts
   ├─ components/
   │  └─ OrderBook.tsx               # 두 화면에서 공통 사용
   ├─ features/
   │  ├─ market/
   │  │  ├─ MarketScreen.tsx
   │  │  ├─ useDepthStream.ts
   │  │  └─ depthReducer.ts
   │  └─ trade/
   │     └─ TradeScreen.tsx
   └─ types/
      └─ market.ts
```

## 파일별 역할

| 파일 | 역할 |
|---|---|
| `src/api/marketStream.ts` | React Native 환경에서 SSE 연결을 시작하고 종료한다. |
| `src/features/market/useDepthStream.ts` | 연결 상태, 최신 호가, 재연결과 정리를 관리한다. |
| `src/features/market/depthReducer.ts` | 수신한 데이터를 화면에서 사용할 호가 상태로 갱신한다. |
| `src/components/OrderBook.tsx` | MarketScreen과 TradeScreen에서 함께 사용할 호가 목록 UI다. |

## 연결할 API

```http
GET /api/binance-futures/btcusdt/depth/stream
Accept: text/event-stream
```

웹 브라우저의 `EventSource`를 그대로 사용할 수 있다고 가정하지 않고, React Native와 Expo 환경에서 동작하는 SSE 방식을 선택한 뒤 실제 Android 기기에서 검증한다. Spring SSE 엔드포인트는 그대로 사용한다.

## 구현할 내용

- SSE 연결 시작과 종료를 구현한다.
- 호가 JSON을 매수·매도 목록과 중간 가격으로 변환한다.
- 연결 중, 연결됨, 끊김과 재연결 상태를 UI로 표시한다.
- 화면을 떠나거나 앱이 백그라운드로 가면 연결을 정리한다.
- 앱이 다시 활성화되면 연결을 복구한다.
- 재연결 간격을 점차 늘려 짧은 시간에 반복 요청하지 않게 한다.

## 완료 기준

- MarketScreen과 TradeScreen의 호가가 실시간으로 바뀐다.
- 화면 이동과 백그라운드 전환 뒤 불필요한 연결이 남지 않는다.
- 네트워크를 껐다 켜도 앱이 종료되지 않고 연결을 복구한다.

---

# 8단계. 거래 화면의 주문을 서버에 전송한다

2단계 TradeScreen의 로컬 상태와 결과 메시지를 실제 주문 요청으로 확장한다. 경로 파일은 계속 TradeScreen 연결만 담당한다.

## 만들거나 수정할 파일

```text
mobile/
├─ app/(tabs)/trade.tsx              # TradeScreen 연결 유지
└─ src/
   ├─ api/
   │  └─ paperApi.ts
   ├─ features/
   │  └─ trade/
   │     ├─ TradeScreen.tsx
   │     ├─ useOrderForm.ts
   │     ├─ useSubmitOrder.ts
   │     └─ components/
   │        ├─ OrderForm.tsx
   │        └─ OrderConfirmModal.tsx
   └─ types/
      └─ paper.ts
```

## 파일별 역할

| 파일 | 역할 |
|---|---|
| `src/features/trade/useOrderForm.ts` | 주문 방식·방향·수량·가격 입력과 로컬 검증을 관리한다. |
| `src/features/trade/useSubmitOrder.ts` | 주문 요청, 진행 중 상태와 성공·실패 처리를 관리한다. |
| `src/features/trade/components/OrderForm.tsx` | 거래 화면에서만 사용하는 주문 입력 UI다. |
| `OrderConfirmModal.tsx` | 서버 전송 전에 주문 내용을 한 번 더 확인한다. |
| `src/api/paperApi.ts` | 주문 생성·취소 같은 HTTP 요청을 제공한다. |

## 연결할 API

| 기능 | 요청 |
|---|---|
| 주문 생성 | `POST /api/paper/orders` |
| 주문 취소 | `DELETE /api/paper/orders/{id}` |

## 구현할 내용

- 시장가/지정가, 매수/매도, 수량과 지정가 입력을 서버 요청 형식에 맞춘다.
- 잘못된 입력은 서버에 전송하지 않는다.
- 호가를 누르면 지정가 입력값에 반영한다.
- 주문 확인 모달을 표시한다.
- 요청 중에는 버튼을 비활성화해 중복 터치를 막는다.
- 성공 후 계좌·주문·체결 상태를 다시 조회한다.
- 대기 주문 취소와 현재 포지션 종료 흐름을 추가한다.
- 서버 오류를 사용자가 다음 행동을 알 수 있는 문장으로 표시한다.

## 안전 규칙

- 모바일 앱에서 계산한 값만 신뢰하지 않는다.
- 최종 주문 검증, 체결과 잔액 계산은 Spring 서버가 담당한다.
- 연속 터치로 같은 주문이 중복 전송되지 않게 한다.
- 실제 자산 거래가 아닌 모의투자임을 화면에 명확하게 표시한다.

## 완료 기준

- 시장가 주문, 지정가 주문, 주문 취소와 포지션 종료가 동작한다.
- 주문 결과가 기존 웹에서도 동일하게 확인된다.
- 잘못된 수량과 잔액 부족 오류가 이해 가능한 문장으로 표시된다.

---

# 9단계. 시세 화면에 모바일 차트를 추가한다

인증·호가·거래 흐름이 안정된 뒤 차트를 추가한다. 웹의 `lightweight-charts`는 브라우저 DOM 기반이므로 React Native 화면에 그대로 import하지 않는다.

## 만들거나 수정할 파일

```text
mobile/
├─ app/(tabs)/market.tsx             # MarketScreen 연결 유지
└─ src/
   ├─ api/
   │  └─ binanceApi.ts
   └─ features/
      └─ market/
         ├─ MarketScreen.tsx
         └─ chart/
            ├─ PriceChart.tsx
            ├─ useKlines.ts
            └─ useKlineStream.ts
```

## 파일별 역할

| 파일 | 역할 |
|---|---|
| `src/api/binanceApi.ts` | 공개 과거 캔들 데이터를 가져오는 요청을 제공한다. |
| `src/features/market/chart/useKlines.ts` | 최초 과거 캔들 로딩 상태를 관리한다. |
| `useKlineStream.ts` | 현재 캔들 값을 실시간으로 갱신하고 연결을 정리한다. |
| `PriceChart.tsx` | 선택한 React Native 차트 또는 WebView 기반 차트를 감싼다. |

## 구현 순서

1. 먼저 현재 가격이나 단순 가격선을 표시한다.
2. Expo SDK 57과 호환되는 React Native 차트 라이브러리와 WebView 방식 중 하나를 선택한다.
3. 과거 캔들은 Binance Futures Kline REST API에서 받는다.
4. 현재 캔들은 Binance Futures Kline WebSocket으로 갱신한다.
5. 화면을 떠나거나 앱이 백그라운드로 가면 WebSocket을 정리한다.

공개 시세만 사용하며 앱 번들에 Binance 비밀키를 넣지 않는다.

## 완료 기준

- 과거 가격 또는 캔들이 표시된다.
- 현재 가격 또는 현재 캔들이 실시간으로 갱신된다.
- 화면을 떠난 뒤 불필요한 WebSocket 연결이 남지 않는다.

---

# 10단계. 실제 Android 사용 흐름과 파일 책임을 점검한다

기능을 더 추가하기 전에 실제 사용 흐름에서 깨지는 부분과 중복된 책임을 정리한다.

## 점검할 기능

- 첫 화면부터 회원가입·로그인·로그아웃까지의 이동
- 앱 재실행 후 로그인 상태 확인
- 시세 SSE 연결과 재연결
- 계좌·포지션·주문·체결 조회
- 시장가·지정가 주문, 주문 취소와 포지션 종료
- 레버리지 변경
- 세션 만료 처리

## 점검할 모바일 환경

- 작은 화면과 큰 화면
- Android 시스템 뒤로가기
- 키보드 열림·닫힘과 입력창 가림
- 내용이 짧을 때와 길 때의 ScrollView·FlatList
- 느린 네트워크와 비행기 모드
- 앱 백그라운드 전환 후 복귀
- 긴 이메일, 큰 가격과 수량
- 로딩 중 연속 터치

## 파일 구조 점검 기준

- `app/*.tsx`에는 경로 연결과 화면 이동 설정만 둔다.
- 실제 화면 JSX와 화면 상태는 `src/features`에 둔다.
- 한 기능에서만 쓰는 Hook과 컴포넌트는 해당 기능 폴더에 둔다.
- 둘 이상의 기능에서 쓰는 UI만 `src/components`에 둔다.
- API 주소와 HTTP 요청은 `src/api`에 둔다.
- 공통 색상과 디자인 값은 `src/theme`에 둔다.
- 공유 API 타입은 `src/types`, 공통 변환 함수는 `src/utils`에 둔다.
- `any` 사용을 최소화한다.
- 비밀번호, 세션 값과 토큰을 로그에 출력하지 않는다.

## 완료 기준

- 실제 Android 기기에서 첫 실행부터 모의 주문까지 완료할 수 있다.
- 네트워크가 끊겨도 앱이 종료되지 않는다.
- 오류가 발생하면 사용자가 다음 행동을 알 수 있다.
- 경로 파일, 화면, Hook, 재사용 UI와 API 코드의 책임이 겹치지 않는다.

---

# 11단계. Android 내부 테스트와 Google Play 배포를 준비한다

기능 구현이 끝난 뒤 다른 사용자가 설치할 수 있는 빌드와 배포 정보를 준비한다.

## 만들거나 수정할 파일·설정

- `app.json`: 앱 이름, 아이콘, Android 패키지 이름과 버전을 확정한다.
- EAS 설정 파일: Development, Preview와 Production 빌드 설정을 둔다.
- 개인정보처리방침과 이용약관: 배포 페이지에서 사용할 공개 URL을 준비한다.

## 구현할 내용

- Expo 계정과 EAS 설정
- Android 패키지 이름 확정
- 앱 아이콘과 스플래시 화면
- 앱 버전과 Android `versionCode` 관리
- Preview APK를 실제 기기에 설치해 테스트
- Production AAB 생성
- Google Play 내부 테스트 트랙 등록

## 출시 전에 별도로 확인할 내용

- Binance 시세 데이터의 공개 앱 사용·재배포 허용 범위
- 광고를 추가할 경우 상업적 사용과 광고 정책
- 개인정보 수집·보관·삭제 설명
- 현금 입출금, 실제 주문 중계와 현금성 보상을 제공하지 않는지

첫 공개 테스트는 광고 없이 진행한다.

## 완료 기준

- 내부 테스트 사용자가 Google Play 링크로 앱을 설치할 수 있다.
- 설치된 앱이 Railway Spring 서버와 HTTPS로 통신한다.
- 치명적인 오류 없이 핵심 모의투자 흐름을 완료한다.

---

# 첫 번째 MVP 완료 조건

- [x] Expo Router 기반 `mobile/` 프로젝트 생성
- [x] Expo SDK 57 적용
- [x] RootLayout과 첫 경로 연결
- [x] 로컬 거래 화면과 재사용 버튼·호가 행 구현
- [ ] 첫 화면·로그인·회원가입·하단 탭 이동
- [ ] Spring 공개 최신 호가 조회
- [ ] 회원가입
- [ ] 로그인과 세션 유지
- [ ] 로그아웃
- [ ] BTCUSDT 실시간 호가
- [ ] 계좌와 포지션 조회
- [ ] 시장가 주문
- [ ] 지정가 주문
- [ ] 주문 취소
- [ ] 포지션 종료
- [ ] 체결 내역 조회
- [ ] 세션 만료 처리
- [ ] 네트워크 재연결
- [ ] Android Preview APK 테스트
- [ ] Google Play 내부 테스트 AAB 등록

---

# 첫 버전 이후에 검토할 기능

- 모바일 인증을 Access Token + Refresh Token 방식으로 전환할 필요가 있는지 검토
- 토큰 방식을 선택한 경우 Refresh Token을 Expo SecureStore에 저장
- 푸시 알림
- 광고
- 다중 코인
- 관심 종목과 가격 알림
- 소셜 로그인과 생체 인증
- iOS 출시
- 오류 수집과 사용 통계

---

# 바로 시작할 작업

다음 실제 구현은 3단계만 진행한다.

1. 기존 `app/_layout.tsx`는 RootLayout으로 유지한다.
2. `app/(tabs)/_layout.tsx`를 새로 만들고 TabsLayout을 구현한다.
3. `app` 아래에 얇은 경로 파일들을 만든다.
4. `src/features` 아래에 실제 첫 화면·인증·시세·주문내역·계좌 화면을 만든다.
5. 기존 `src/features/trade/TradeScreen.tsx`를 거래 탭 경로에서 재사용한다.
6. 실제 서버 인증 없이 첫 화면 → 로그인 → 회원가입 또는 탭 화면 이동을 확인한다.
7. 네 개의 하단 탭과 Android 시스템 뒤로가기를 실제 기기에서 확인한다.

3단계에서는 화면 이동 구조만 완성한다. Spring API, 실제 로그인 상태와 서버 주문은 각각 이후 단계에서 연결한다.
