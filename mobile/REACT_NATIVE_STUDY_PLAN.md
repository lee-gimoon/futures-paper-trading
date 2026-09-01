# Futures Paper Trading 모바일 앱 구현형 학습 계획

이 문서는 기존 Spring Boot 서버와 PostgreSQL을 그대로 사용해 `mobile/`에 React Native + Expo 모바일 앱을 만드는 구현 계획이다.

이 계획의 목적은 개념을 모두 외운 뒤 개발을 시작하는 것이 아니다. **1단계부터 실제 파일을 만들고 실행한다.** 각 단계에서 필요한 만큼만 공부한다. 즉, “이 파일은 왜 필요한가?”, “이 코드는 어떤 역할을 하는가?”를 직접 수정하고 화면에서 확인하며 익힌다.

작업 원칙은 다음과 같다.

- 한 단계는 작게 끝낸다. 만든 뒤 Android 기기 또는 에뮬레이터에서 바로 확인한다.
- 새 파일을 만들 때마다 파일의 책임을 한 문장으로 설명할 수 있어야 한다.
- API 연결, 인증, 주문처럼 위험도가 있는 기능은 화면과 로컬 상태를 먼저 만든 후 서버와 연결한다.
- 기존 React 웹, Spring Controller/Service/Repository, PostgreSQL, 거래 엔진은 1차 모바일 개발에서 변경하지 않는다.
- 막히는 개념은 그 기능을 구현하는 데 필요한 범위에서만 확인한다. 별도의 이론 단계로 미루지 않는다.

운영 API 기본 주소:

```text
https://futures-paper-trading-production.up.railway.app
```

전체 구조:

```text
React 웹 ─────────┐
                  ├─ Railway Spring Boot ─ PostgreSQL
React Native 앱 ──┘          │
                              └─ Binance 시세
```

---

## 프로젝트에서 사용할 기본 구조

Expo Router를 쓰므로 화면 파일은 `app/` 폴더에 둔다. `app/` 안의 파일 경로가 화면 경로가 된다.

```text
mobile/
├─ app/                         # 화면과 화면 이동 구조
│  ├─ _layout.tsx                # 앱 전체의 화면 전환 규칙
│  ├─ index.tsx                  # 첫 진입 화면
│  ├─ login.tsx                  # 로그인 화면
│  ├─ signup.tsx                 # 회원가입 화면
│  └─ (tabs)/                    # 하단 탭으로 묶이는 화면
│     ├─ _layout.tsx             # 하단 탭 구성
│     ├─ market.tsx              # 시세 화면
│     ├─ trade.tsx               # 주문 화면
│     ├─ orders.tsx              # 주문·체결 내역 화면
│     └─ account.tsx             # 계좌 화면
├─ src/
│  ├─ api/                       # Spring API를 호출하는 코드
│  ├─ components/                # 여러 화면에서 재사용하는 UI
│  ├─ features/                  # 인증·시세·주문 기능별 상태와 로직
│  ├─ hooks/                     # 화면 로직을 묶은 React Hook
│  ├─ theme/                     # 색상·간격·글자 크기
│  ├─ types/                     # API 데이터 TypeScript 타입
│  └─ utils/                     # 숫자·가격 포맷 같은 공통 함수
├─ assets/                       # 아이콘·스플래시 이미지
├─ app.json                      # 앱 이름, 아이콘, Android 설정
├─ package.json                  # 패키지와 실행 명령어
├─ tsconfig.json                 # TypeScript 설정
└─ REACT_NATIVE_STUDY_PLAN.md    # 이 문서
```

이 구조는 처음부터 모든 폴더를 만들라는 뜻은 아니다. **해당 단계에서 필요한 폴더와 파일만 추가한다.**

---

# 1단계. Expo 프로젝트를 만들고 첫 화면을 실행한다

첫 단계부터 구현을 시작한다. 프로젝트 생성 도구가 만든 파일을 하나씩 살펴보고, 기본 화면을 직접 수정한다.

## 만들거나 수정할 파일

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
| `package.json` | Expo와 React Native 패키지, `start` 같은 실행 명령어를 기록한다. |
| `app.json` | 앱 이름, 아이콘, Android 패키지 설정 등 앱 자체의 정보를 둔다. |
| `app/_layout.tsx` | 앱 안에서 화면이 어떻게 전환되는지 정하는 최상위 껍데기다. |
| `app/index.tsx` | 앱을 처음 열었을 때 보이는 홈 화면이다. |
| `tsconfig.json` | TypeScript가 코드를 검사하는 기준을 정한다. |

## 구현할 내용

1. `mobile/`에 Expo + TypeScript + Expo Router 프로젝트를 생성한다.
2. 자동 생성된 예제 화면을 지우거나 단순화한다.
3. `app/index.tsx`에 `Futures Paper Trading` 제목과 버튼 하나를 만든다.
4. `npx expo start`로 개발 서버를 실행한다.
5. Expo Go가 설치된 Android 휴대폰 또는 Android 에뮬레이터에서 앱을 연다.
6. 제목·버튼 문구·색상을 수정하고 Fast Refresh로 즉시 바뀌는지 확인한다.

## 이 단계에서 코드로 익힐 것

- `View`, `Text`, `Pressable`은 각각 화면 영역, 글자, 누를 수 있는 UI다.
- React Native는 HTML의 `div`, `button`을 쓰지 않고 네이티브 UI 컴포넌트를 쓴다.
- `app/index.tsx`에서 반환하는 JSX가 첫 화면의 모양을 결정한다.
- Expo는 개발 중 휴대폰에서 이 앱을 실행하고 새 버전을 빠르게 반영하게 해 준다.

## 완료 기준

- Android에서 첫 화면이 열린다.
- `app/index.tsx`를 수정하면 변경이 바로 보인다.
- `package.json`, `app.json`, `app/index.tsx`가 각각 무슨 일을 하는지 설명할 수 있다.

---

# 2단계. 첫 화면을 작은 거래 화면으로 바꾸며 UI 파일을 나눈다

서버에 연결하지 않는다. 먼저 주문 화면의 모양과 입력 상태를 만든다. 한 파일이 너무 커지기 시작하면 공통 UI를 분리한다.

## 만들거나 수정할 파일

```text
mobile/
├─ app/
│  └─ index.tsx
└─ src/
   ├─ components/
   │  ├─ AppButton.tsx
   │  └─ PriceRow.tsx
   └─ theme/
      └─ colors.ts
```

## 파일별 역할

| 파일 | 역할 |
|---|---|
| `app/index.tsx` | 화면 전체를 조합하고, 이 단계의 입력 상태를 가진다. |
| `src/components/AppButton.tsx` | 여러 화면에서 재사용할 버튼의 공통 모양을 만든다. |
| `src/components/PriceRow.tsx` | 가격 한 줄을 표시하는 작은 UI를 맡는다. |
| `src/theme/colors.ts` | 매수·매도 색상, 배경색처럼 반복되는 색상을 한곳에 둔다. |

## 구현할 내용

```text
BTCUSDT
현재가: 00,000.00

[시장가] [지정가]
[수량 입력]
[매수] [매도]
```

- `TextInput`으로 수량을 입력한다.
- 시장가/지정가 버튼을 누르면 선택 상태가 바뀐다.
- 매수/매도 버튼을 누르면 우선 화면 아래에 “매수 주문을 준비했습니다” 같은 로컬 메시지를 보여 준다.
- `StyleSheet.create()`와 Flexbox로 세로·가로 배치를 만든다.
- `SafeAreaView`, `ScrollView`, 키보드 처리로 작은 화면에서도 입력창과 버튼이 가려지지 않게 한다.
- 연습용 호가 목록은 `FlatList`로 표시한다.

## 이 단계에서 코드로 익힐 것

- 컴포넌트는 화면을 구성하는 재사용 가능한 조각이다.
- `useState`는 입력값이나 선택된 주문 방식처럼 화면 안에서 바뀌는 값을 보관한다.
- `props`는 부모 화면이 버튼이나 가격 행에 데이터를 전달하는 방법이다.
- `StyleSheet`와 Flexbox는 React Native UI 배치 방법이다.

## 완료 기준

- 수량 입력, 시장가/지정가 선택, 매수/매도 클릭이 화면에 반영된다.
- 작은 Android 화면에서 내용이 잘리지 않는다.
- 버튼과 가격 행을 별도 컴포넌트로 나눈 이유를 설명할 수 있다.

---

# 3단계. 화면 파일을 늘리고 Expo Router로 이동시킨다

여러 컴포넌트를 조건문으로 직접 교체하는 대신, 화면 파일과 이동 규칙을 Expo Router에 맡긴다. 이 단계에서 Router는 “어떤 경로가 어떤 화면 파일을 보여 주는지”와 “뒤로 가기 기록”을 관리한다.

## 만들거나 수정할 파일

```text
mobile/app/
├─ _layout.tsx
├─ index.tsx
├─ login.tsx
├─ signup.tsx
└─ (tabs)/
   ├─ _layout.tsx
   ├─ market.tsx
   ├─ trade.tsx
   ├─ orders.tsx
   └─ account.tsx
```

## 파일별 역할

| 파일 | 역할 |
|---|---|
| `app/_layout.tsx` | 로그인 화면과 탭 화면을 쌓아서 이동시키는 Stack 구조를 둔다. |
| `app/login.tsx`, `app/signup.tsx` | 탭 밖에서 여는 인증 화면이다. |
| `app/(tabs)/_layout.tsx` | 시세·거래·주문내역·계정 탭을 아래쪽에 배치한다. |
| `app/(tabs)/*.tsx` | 탭을 눌렀을 때 보이는 각 화면이다. |

## 구현할 내용

- 첫 화면의 버튼으로 로그인 화면을 연다.
- 로그인 화면에서 회원가입 화면을 연다.
- 로그인 성공 여부와 관계없이, 이 단계에서는 임시 버튼으로 탭 화면에 진입하게 한다.
- 시세, 거래, 주문내역, 계정 탭에 화면 제목과 임시 내용을 넣는다.
- Android의 시스템 뒤로가기 버튼으로 이전 화면에 돌아가는지 확인한다.

## 이 단계에서 코드로 익힐 것

- `router.push('/login')`은 로그인 화면으로 이동하라는 요청이다.
- `app/login.tsx` 파일은 `/login` 화면에 대응한다.
- `(tabs)`는 파일 경로 주소에는 드러나지 않지만 탭 UI로 화면들을 묶는 폴더다.
- 화면은 Router가 직접 열고 닫는 큰 단위이고, 컴포넌트는 한 화면 안에서 조립하는 작은 단위다.

## 완료 기준

- 로그인·회원가입·네 개의 탭 화면을 이동할 수 있다.
- URL을 직접 다루는 웹이 아니어도, 모바일 Router가 이전 화면 기록과 Android 뒤로가기를 처리하는 이유를 설명할 수 있다.
- 각 화면 파일과 재사용 컴포넌트의 역할을 구분할 수 있다.

---

# 4단계. Spring API를 한곳에서 호출하고 시세 화면에 연결한다

화면 컴포넌트 안에 API 주소와 `fetch()`를 흩어 쓰지 않는다. 먼저 공개 호가 API 한 개만 연결한다.

## 만들거나 수정할 파일

```text
mobile/
├─ app/(tabs)/market.tsx
└─ src/
   ├─ api/
   │  ├─ client.ts
   │  └─ marketApi.ts
   ├─ hooks/
   │  └─ useLatestDepth.ts
   └─ types/
      └─ market.ts
```

## 파일별 역할

| 파일 | 역할 |
|---|---|
| `src/api/client.ts` | 서버 기본 주소, 공통 요청과 공통 오류 처리를 둔다. |
| `src/api/marketApi.ts` | 시세 API 요청만 모은다. |
| `src/types/market.ts` | 서버가 주는 호가 JSON의 TypeScript 모양을 정의한다. |
| `src/hooks/useLatestDepth.ts` | API 호출, 로딩·성공·오류 상태를 화면용으로 묶는다. |
| `app/(tabs)/market.tsx` | Hook이 준 상태를 사용해 로딩·오류·호가 UI를 보여 준다. |

## 가장 먼저 연결할 API

```http
GET /api/binance-futures/btcusdt/depth/latest
```

이 API는 공개 API이므로 로그인·쿠키를 다루기 전에 앱과 Railway 서버의 통신부터 확인할 수 있다.

## 구현할 내용

- Railway 기본 주소를 `client.ts` 한곳에 둔다.
- `marketApi.ts`에서 최신 호가 요청 함수를 만든다.
- 응답 JSON에 맞는 타입을 만든다.
- 시세 탭에서 로딩, 성공, 오류 상태를 각각 표시한다.
- 당겨서 새로고침 또는 새로고침 버튼을 추가한다.

## 이 단계에서 코드로 익힐 것

- `async`/`await`는 서버 응답을 기다리는 코드다.
- TypeScript 타입은 서버 응답에 어떤 필드가 있어야 하는지 문서이자 검사 기준이다.
- 화면은 “보여 주기”에 집중하고 API 파일은 “요청 보내기”에 집중하게 나누는 이유를 확인한다.
- 200, 400, 401, 500과 네트워크 연결 실패는 서로 다른 상황이다.

## 완료 기준

- Android 앱에서 Railway API 응답을 받아 호가를 표시한다.
- API 주소가 화면 파일에 직접 중복되어 있지 않다.
- 네트워크 오류와 서버 응답 오류를 구분해 표시한다.

---

# 5단계. 로그인·회원가입과 인증 상태를 구현한다

기존 Spring SESSION 쿠키 인증을 우선 사용한다. 인증 화면을 만든 뒤 인증 상태를 앱 전체에서 공유한다.

### 세션 CSRF도 함께 구현한다

- 앱 시작 시 `GET /api/auth/csrf`를 먼저 호출한다.
- 응답의 CSRF 토큰은 React Native JavaScript 메모리에 저장한다.
- 로그인·회원가입·로그아웃 요청에 서버가 알려 준 CSRF 헤더를 추가한다.
- 로그인 뒤 현재 세션 기준으로 CSRF 토큰을 다시 조회해 메모리와 동기화하고, 로그아웃·세션 만료 뒤에는 제거한다.
- `SESSION` 쿠키와 CSRF 토큰이 같은 서버 세션에 연결되는지 실기기에서 확인한다.

예정 파일:

```text
mobile/src/api/csrf.ts
mobile/src/api/client.ts
mobile/src/api/authApi.ts
mobile/src/features/auth/AuthProvider.tsx
mobile/src/api/paperApi.ts
```

현재 모바일은 1단계이므로 파일은 미리 만들지 않고 모바일 5단계에서 구현한다.

## 만들거나 수정할 파일

```text
mobile/
├─ app/
│  ├─ _layout.tsx
│  ├─ login.tsx
│  └─ signup.tsx
└─ src/
   ├─ api/authApi.ts
   ├─ features/auth/
   │  ├─ AuthProvider.tsx
   │  └─ useAuth.ts
   └─ types/auth.ts
```

## 파일별 역할

| 파일 | 역할 |
|---|---|
| `src/api/authApi.ts` | 회원가입, 로그인, 현재 사용자, 로그아웃 요청을 모은다. |
| `AuthProvider.tsx` | 현재 로그인 사용자와 로그인 여부를 앱 전체에 제공한다. |
| `useAuth.ts` | 화면이 인증 상태와 로그인·로그아웃 함수를 쉽게 쓰게 한다. |
| `login.tsx`, `signup.tsx` | 이메일·비밀번호 입력과 결과 표시를 맡는다. |
| `app/_layout.tsx` | 로그인 상태에 따라 인증 화면 또는 탭 화면으로 보낼지 결정한다. |

## 연결할 API

| 기능 | 요청 |
|---|---|
| 회원가입 | `POST /api/auth/signup` |
| 로그인 | `POST /api/auth/login` |
| 현재 사용자 | `GET /api/auth/me` |
| 로그아웃 | `POST /api/auth/logout` |

## 구현할 내용

- 회원가입·로그인 폼 검증을 만든다.
- 앱이 시작될 때 `/api/auth/me`로 로그인 상태를 확인한다.
- 로그인 후 탭 화면으로 이동한다.
- 로그인하지 않은 사용자는 거래 탭을 사용할 수 없게 한다.
- 401 응답이면 사용자 정보를 비우고 로그인 화면으로 이동한다.
- 로그아웃하면 서버 세션과 화면의 인증 상태를 모두 정리한다.

## 실제 Android 기기에서 반드시 확인할 것

1. 로그인 직후 `/api/auth/me`가 성공하는가?
2. 보호 API에도 SESSION 쿠키가 전달되는가?
3. 앱을 종료했다 다시 열어도 세션 상태를 알맞게 확인하는가?
4. 로그아웃 후 보호 API에 접근할 수 없는가?

React Native에서 쿠키 유지가 불안정하면 모바일용 쿠키 관리 라이브러리와 Development Build 도입을 검토한다. 이때도 먼저 모바일 쪽에서 해결하며 Spring Java 코드는 변경하지 않는다.

## 완료 기준

- 회원가입, 로그인, 현재 사용자 조회, 로그아웃이 동작한다.
- 인증이 필요한 화면은 로그인 상태에 따라 올바르게 보인다.
- 세션 만료 시 무한 요청하지 않고 로그인 화면을 보여 준다.

---

# 6단계. 계좌·주문 내역을 조회하는 기능을 만든다

거래를 보내기 전에 서버가 이미 가지고 있는 계좌 상태와 주문 정보를 정확히 보여 준다.

## 만들거나 수정할 파일

```text
mobile/
├─ app/(tabs)/
│  ├─ account.tsx
│  └─ orders.tsx
└─ src/
   ├─ api/paperApi.ts
   ├─ components/
   │  ├─ EmptyState.tsx
   │  ├─ ErrorState.tsx
   │  ├─ LoadingState.tsx
   │  ├─ PositionCard.tsx
   │  └─ OrderRow.tsx
   ├─ hooks/
   │  ├─ usePaperAccount.ts
   │  └─ usePaperOrders.ts
   ├─ types/paper.ts
   └─ utils/format.ts
```

## 파일별 역할

| 파일 | 역할 |
|---|---|
| `paperApi.ts` | 모의 계좌, 포지션, 주문, 체결 관련 서버 요청을 모은다. |
| `usePaperAccount.ts`, `usePaperOrders.ts` | 조회·새로고침·오류 상태를 화면에서 재사용한다. |
| `PositionCard.tsx`, `OrderRow.tsx` | 복잡한 계좌·주문 표시를 작은 UI 단위로 나눈다. |
| `format.ts` | 가격, 수량, 손익을 같은 형식으로 표시한다. |
| 상태 컴포넌트 세 개 | 로딩·빈 목록·오류를 모든 화면에서 일관되게 표시한다. |

## 연결할 API

| 기능 | 요청 |
|---|---|
| 계좌·포지션 | `GET /api/paper/account` |
| 체결 내역 | `GET /api/paper/fills` |
| 주문 목록 | `GET /api/paper/orders` |
| 레버리지 변경 | `PUT /api/paper/account/leverage` |

## 구현할 내용

- 현금 잔액, 실현·미실현 손익, 평가자산, 사용 증거금을 표시한다.
- 포지션이 있으면 진입가와 청산 예상 가격을 표시한다.
- 대기 주문과 체결 내역을 `FlatList`로 표시한다.
- 포지션·주문이 없을 때는 빈 상태 UI를 표시한다.
- 당겨서 새로고침을 구현한다.
- 레버리지 변경 UI와 요청을 연결한다.

## 이 단계에서 코드로 익힐 것

- 서버 상태는 서버에서 다시 가져와야 하는 데이터이고, 입력값 같은 화면 상태와 다르다.
- `FlatList`는 긴 목록을 효율적으로 그린다.
- 서버 응답의 `null`, 빈 배열, 오류를 각기 다른 UI로 처리해야 한다.

## 완료 기준

- 웹과 모바일에서 같은 계정으로 로그인했을 때 같은 잔액과 주문이 보인다.
- 포지션이나 주문이 없어도 오류 없이 표시된다.
- 로딩·빈 상태·오류 상태가 구분된다.

---

# 7단계. 실시간 호가를 SSE로 연결한다

화면에 최신 호가를 한 번 받는 것에서 끝내지 않고, 기존 Spring SSE 스트림으로 계속 갱신한다.

## 만들거나 수정할 파일

```text
mobile/
├─ app/(tabs)/
│  ├─ market.tsx
│  └─ trade.tsx
└─ src/
   ├─ api/marketStream.ts
   ├─ features/market/
   │  ├─ useDepthStream.ts
   │  └─ depthReducer.ts
   ├─ components/OrderBook.tsx
   └─ types/market.ts
```

## 파일별 역할

| 파일 | 역할 |
|---|---|
| `marketStream.ts` | React Native에서 호환되는 SSE 연결을 만들고 끊는다. |
| `useDepthStream.ts` | 화면이 열릴 때 연결하고, 닫힐 때 정리하며 최신 값을 제공한다. |
| `depthReducer.ts` | 들어온 호가 데이터를 화면에 쓸 형태로 갱신한다. |
| `OrderBook.tsx` | 매수·매도 호가 목록과 중간 가격을 표시한다. |

## 연결할 API

```http
GET /api/binance-futures/btcusdt/depth/stream
Accept: text/event-stream
```

웹의 `EventSource`는 브라우저 전용이므로 React Native 호환 SSE 클라이언트를 사용한다. Spring SSE 코드는 그대로 둔다.

## 구현할 내용

- SSE 연결 시작·종료를 구현한다.
- 호가 JSON을 파싱해 매수·매도 목록과 중간 가격을 표시한다.
- 연결 중, 끊김, 재연결 상태를 UI로 보여 준다.
- 앱이 백그라운드로 가거나 시세 화면을 떠나면 연결을 정리한다.
- 앱이 다시 활성화되면 재연결한다.
- 재연결은 지수 백오프를 사용해 너무 자주 요청하지 않게 한다.

## 이 단계에서 코드로 익힐 것

- 일반 HTTP는 한 번 요청하고 끝나지만 SSE는 서버와 연결을 유지한다.
- `useEffect`의 반환 함수는 화면을 떠날 때 연결 같은 자원을 정리한다.
- 앱 생명주기는 화면이 보이는지와 앱이 백그라운드인지에 따라 다르게 고려해야 한다.

## 완료 기준

- 호가가 실시간으로 바뀐다.
- 화면 이동·백그라운드 전환 뒤 불필요한 연결이 남지 않는다.
- 네트워크를 껐다 켜도 자동으로 복구한다.

---

# 8단계. 모의 주문을 서버에 전송한다

2단계에서 만든 주문 화면의 임시 상태를 실제 API 요청으로 바꾼다. 최종 검증과 잔액 계산은 계속 Spring 서버가 한다.

## 만들거나 수정할 파일

```text
mobile/
├─ app/(tabs)/trade.tsx
└─ src/
   ├─ api/paperApi.ts
   ├─ features/trade/
   │  ├─ useOrderForm.ts
   │  └─ useSubmitOrder.ts
   ├─ components/
   │  ├─ OrderForm.tsx
   │  └─ OrderConfirmModal.tsx
   └─ types/paper.ts
```

## 파일별 역할

| 파일 | 역할 |
|---|---|
| `useOrderForm.ts` | 주문 방식·방향·수량·가격 같은 입력 상태와 검증을 관리한다. |
| `useSubmitOrder.ts` | 주문 요청, 진행 상태, 성공·실패 처리를 관리한다. |
| `OrderForm.tsx` | 주문 입력 UI만 담당한다. |
| `OrderConfirmModal.tsx` | 서버 전송 전에 주문 내용을 한 번 더 확인한다. |
| `paperApi.ts` | 주문 생성·취소 같은 HTTP 요청을 제공한다. |

## 연결할 API

| 기능 | 요청 |
|---|---|
| 주문 생성 | `POST /api/paper/orders` |
| 주문 취소 | `DELETE /api/paper/orders/{id}` |

## 구현할 내용

- 시장가/지정가, 매수/매도, 수량·지정가 입력을 구현한다.
- 입력값을 검증하고 잘못된 값은 전송하지 않는다.
- 호가를 누르면 주문 가격에 넣는다.
- 주문 전 확인 창을 연다.
- 요청 중에는 버튼을 비활성화해 중복 터치를 막는다.
- 주문 성공 후 계좌·주문·체결 데이터를 새로고침한다.
- 대기 주문 취소와 현재 포지션 종료를 추가한다.
- 서버가 준 오류를 사용자가 이해할 수 있는 문장으로 표시한다.

## 안전 규칙

- 앱에서 계산한 값만 믿지 않는다.
- 최종 주문 검증과 잔액 계산은 기존 Spring 서버가 담당한다.
- 버튼을 연속으로 눌러 같은 주문이 여러 번 나가지 않게 한다.
- 실제 자산 거래가 아닌 모의투자임을 화면에 명확하게 표시한다.

## 완료 기준

- 시장가 주문, 지정가 주문, 주문 취소, 포지션 종료가 동작한다.
- 주문 결과가 기존 웹사이트에서도 동일하게 확인된다.
- 잘못된 수량과 잔액 부족 오류가 이해 가능한 문장으로 보인다.

---

# 9단계. 모바일 캔들 차트를 추가한다

거래·인증·호가가 안정된 뒤 차트를 붙인다. 웹의 `lightweight-charts`는 브라우저 DOM 기반이므로 React Native 화면에서 그대로 사용하지 않는다.

## 만들거나 수정할 파일

```text
mobile/
├─ app/(tabs)/market.tsx
└─ src/
   ├─ api/binanceApi.ts
   ├─ features/chart/
   │  ├─ useKlines.ts
   │  └─ useKlineStream.ts
   └─ components/PriceChart.tsx
```

## 파일별 역할

| 파일 | 역할 |
|---|---|
| `binanceApi.ts` | 과거 캔들 데이터를 받는 요청을 모은다. |
| `useKlines.ts` | 최초 차트 데이터를 불러온다. |
| `useKlineStream.ts` | 현재 캔들 값을 실시간으로 갱신한다. |
| `PriceChart.tsx` | 선택한 모바일용 차트 라이브러리 또는 WebView 차트를 감싼다. |

## 구현 순서

1. 먼저 간단한 가격선 또는 현재가 표시를 만든다.
2. React Native용 차트 라이브러리와 WebView 방식 중 하나를 선택한다.
3. 과거 봉은 Binance Futures Kline REST로 받는다.
4. 실시간 봉은 Binance Futures Kline WebSocket으로 갱신한다.
5. 화면을 떠나거나 앱이 백그라운드로 가면 WebSocket을 정리한다.

앱 번들에는 Binance 비밀키를 넣지 않는다.

## 완료 기준

- 과거 가격 또는 캔들이 표시된다.
- 현재 가격 또는 현재 캔들이 실시간으로 갱신된다.
- 불필요한 WebSocket 연결이 남지 않는다.

---

# 10단계. 실제 Android 기기에서 흐름을 점검하고 구조를 정리한다

기능을 더 넣기 전에 실제 사용 흐름에서 깨지는 부분을 고친다. 이 단계에서는 새 기능보다 파일 책임과 사용자 경험을 정리한다.

## 점검할 기능

- 신규 회원가입, 로그인 성공·실패, 로그아웃
- 앱 재실행 후 로그인 상태 확인
- 시세 SSE 연결·재연결
- 계좌·포지션·주문·체결 조회
- 시장가·지정가 주문, 주문 취소, 포지션 종료
- 레버리지 변경
- 세션 만료

## 점검할 모바일 환경

- 작은 화면과 큰 화면
- Android 시스템 뒤로가기
- 키보드 열림·닫힘
- 느린 네트워크와 비행기 모드
- 앱 백그라운드 전환 후 복귀
- 긴 이메일, 큰 가격·수량
- 로딩 중 연속 터치

## 파일 구조 정리 기준

- 화면 파일은 화면 조합과 화면 이동에 집중한다.
- API 주소와 `fetch()`는 `src/api/`에만 둔다.
- 색상과 간격은 `src/theme/`에 모은다.
- `any`를 최소화하고 API 응답 타입을 `src/types/`에 둔다.
- 인증 상태와 거래 상태를 분리한다.
- 비밀번호, 세션 값, 토큰을 로그에 출력하지 않는다.

## 완료 기준

- 실제 Android 기기에서 첫 실행부터 모의 주문까지 완료할 수 있다.
- 네트워크가 끊겨도 앱이 종료되지 않는다.
- 오류가 발생했을 때 사용자가 다음 행동을 알 수 있다.
- 각 주요 폴더와 파일의 책임이 겹치지 않는다.

---

# 11단계. Android 내부 테스트와 Google Play 준비

기능 구현이 끝난 뒤 빌드·배포 파일을 준비한다. 이 단계는 앱 코드를 처음 배우는 단계가 아니라, 만든 앱을 다른 사람이 설치할 수 있게 만드는 단계다.

## 만들거나 수정할 파일·설정

- `app.json`: 앱 이름, 아이콘, Android 패키지 이름, 버전을 확정한다.
- EAS 설정 파일: Development/Preview/Production 빌드 설정을 둔다.
- 개인정보처리방침과 이용약관 문서: 배포 페이지에서 연결할 URL을 준비한다.

## 구현할 내용

- Expo 계정과 EAS 설정
- Android 패키지 이름 확정
- 앱 아이콘과 스플래시 화면
- 버전과 Android `versionCode` 관리
- Preview APK로 실제 기기 설치 테스트
- Production AAB 생성
- Google Play 내부 테스트 트랙 등록

## 출시 전에 별도로 확인할 것

- Binance 시세 데이터의 공개 앱 사용·재배포 허용 범위
- 광고를 넣는 경우 상업적 사용 허용 범위와 광고 정책
- 개인정보 수집·보관·삭제 설명
- 현금 입출금, 실제 주문 중계, 현금성 보상을 제공하지 않는지

첫 공개 테스트는 광고 없이 진행한다.

## 완료 기준

- 내부 테스트 사용자가 Google Play 링크로 앱을 설치할 수 있다.
- 설치된 앱이 Railway Spring 서버와 HTTPS로 통신한다.
- 치명적인 오류 없이 핵심 모의투자 흐름을 완료할 수 있다.

---

# 첫 번째 MVP 완료 조건

- [ ] `mobile/` Expo 프로젝트가 실제 Android에서 실행된다.
- [ ] 파일을 만들고 수정하면서 `app/`, `src/api/`, `src/components/`, `src/features/`, `src/types/`의 역할을 이해한다.
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

# 첫 버전 이후에 진행할 기능

- 모바일 인증을 Access Token + Refresh Token 방식으로 전환
- Refresh Token을 Expo SecureStore에 저장
- 푸시 알림
- 광고
- 다중 코인
- 관심 종목과 가격 알림
- 소셜 로그인과 생체 인증
- iOS 출시
- 오류 수집과 사용 통계

---

# 바로 시작할 작업

다음 실제 구현은 **1단계만** 진행한다.

1. `mobile/`에 Expo + TypeScript + Expo Router 프로젝트를 만든다.
2. 생성된 `package.json`, `app.json`, `app/_layout.tsx`, `app/index.tsx`를 함께 읽는다.
3. 기본 예제 대신 Futures Paper Trading 첫 화면을 만든다.
4. Android 휴대폰 또는 에뮬레이터에서 실행한다.
5. 화면 문구와 스타일을 직접 바꿔 Fast Refresh를 확인한다.

첫 화면이 실제 기기에서 열리고, 생성된 파일의 역할을 이해한 뒤에 2단계 UI 구현으로 넘어간다.
