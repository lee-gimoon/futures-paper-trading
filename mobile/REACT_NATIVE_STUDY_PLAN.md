# Futures Paper Trading 모바일 앱 학습·개발 계획

이 문서는 기존 Spring Boot 서버와 PostgreSQL을 그대로 사용하면서, `mobile/`에 React Native + Expo 앱을 새로 만드는 순서를 정리한 학습용 로드맵이다.

처음부터 모든 기능을 한꺼번에 옮기지 않는다. 각 단계에서 하나의 개념을 공부하고, 아주 작은 기능을 실제 휴대폰에서 확인한 뒤 다음 단계로 넘어간다.

---

## 0. 이번 모바일 앱의 기본 원칙

- 1차 대상은 Android이다.
- React Native는 Expo와 TypeScript를 사용한다.
- 화면 이동은 Expo Router를 사용한다.
- 기존 React 웹사이트는 그대로 유지한다.
- 기존 Spring Controller, Service, Repository, DB와 거래 엔진은 우선 변경하지 않는다.
- 모바일 앱은 Railway의 기존 Spring 서버와 JSON으로 통신한다.
- 실시간 호가는 기존 Spring SSE API를 사용한다.
- 인증은 우선 현재의 Spring `SESSION` 쿠키를 사용한다.
- JWT, 광고, 푸시 알림, iOS는 첫 번째 완성본 이후로 미룬다.

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

# 1단계. React Native와 Expo 기본 개념 익히기

## 공부할 내용

- React와 React Native의 차이
- 웹 DOM과 네이티브 UI의 차이
- Expo의 역할
- Metro 개발 서버의 역할
- Expo Go와 Development Build의 차이
- Android 앱의 개발 빌드(APK)와 스토어 빌드(AAB)의 차이

## React 웹과 비교해서 익힐 것

| React 웹 | React Native |
|---|---|
| `div` | `View` |
| `p`, `span` | `Text` |
| `input` | `TextInput` |
| `button` | `Button`, `Pressable` |
| CSS 파일 | `StyleSheet`, 스타일 객체 |
| 브라우저 | Android/iOS 네이티브 앱 |

## 완료 기준

- React Native 앱 화면이 왜 Spring 정적 파일로 배포되지 않는지 설명할 수 있다.
- `View`, `Text`, `TextInput`, `Pressable`의 역할을 설명할 수 있다.
- Expo가 개발·빌드·배포 과정에서 무슨 일을 하는지 설명할 수 있다.

---

# 2단계. `mobile/`에 Expo 프로젝트 구성하기

현재는 이 계획 파일만 만들고, 다음 작업에서 Expo 프로젝트 파일을 추가한다.

예상 구조:

```text
mobile/
├─ src/
│  ├─ app/                 # Expo Router 화면 파일
│  ├─ api/                 # Spring/Binance 통신 코드
│  ├─ components/          # 공통 UI 컴포넌트
│  ├─ features/            # auth, market, paper 기능별 코드
│  ├─ hooks/               # 상태와 외부 통신을 묶은 Hook
│  ├─ theme/               # 색상, 간격, 글꼴 크기
│  └─ types/               # API 요청·응답 TypeScript 타입
├─ assets/                 # 앱 아이콘, 스플래시 이미지
├─ app.json                # Expo 앱 설정
├─ package.json            # 모바일 전용 의존성과 명령어
├─ tsconfig.json
└─ REACT_NATIVE_STUDY_PLAN.md
```

## 구현할 내용

- Expo + TypeScript 프로젝트 설정
- Android 패키지 이름 임시 결정
- 앱 이름과 기본 테마 설정
- `npx expo start`로 개발 서버 실행
- Android 실제 휴대폰 또는 에뮬레이터에서 첫 화면 실행

## 공부할 내용

- `package.json`의 역할
- npm 패키지와 의존성
- `app.json`의 역할
- Fast Refresh
- React Native 앱이 실행되는 과정

## 완료 기준

- 휴대폰에서 앱의 첫 화면이 열린다.
- 텍스트와 버튼을 수정하면 Fast Refresh로 바로 반영된다.
- Spring과 React 웹을 실행하지 않아도 기본 앱 화면 자체는 열린다.

---

# 3단계. React Native 화면과 스타일 연습하기

API를 연결하기 전에 작은 연습 화면을 만든다.

## 구현할 내용

- `View`, `Text`, `TextInput`, `Pressable` 사용
- `StyleSheet.create()`로 스타일 작성
- Flexbox로 세로·가로 배치
- `ScrollView`, `FlatList` 사용
- `SafeAreaView`로 상태 표시줄 영역 처리
- 키보드가 입력창을 가리지 않도록 처리
- 로딩, 오류, 빈 데이터 UI 만들기

## 연습 화면

실제 주문은 보내지 않고 다음 모양만 만든다.

```text
BTCUSDT
현재가: 00,000.00

[시장가] [지정가]
[수량 입력]
[매수] [매도]
```

## 완료 기준

- 작은 Android 화면에서도 내용이 잘리지 않는다.
- 입력창에 숫자를 입력하고 버튼을 누르면 로컬 상태가 바뀐다.
- 목록은 `FlatList`로 표시할 수 있다.

---

# 4단계. Expo Router로 화면 이동 구성하기

웹의 3열 레이아웃을 모바일 화면에 그대로 축소하지 않고 하단 탭으로 나눈다.

## 목표 화면

```text
하단 탭
├─ 시세
├─ 거래
├─ 주문내역
└─ 계정
```

예상 라우트 구조:

```text
src/app/
├─ _layout.tsx
├─ login.tsx
├─ signup.tsx
└─ (tabs)/
   ├─ _layout.tsx
   ├─ market.tsx
   ├─ trade.tsx
   ├─ orders.tsx
   └─ account.tsx
```

## 공부할 내용

- 화면과 컴포넌트의 차이
- 파일 기반 라우팅
- Stack과 Tabs
- 화면 이동과 뒤로 가기
- 로그인하지 않은 사용자의 거래 화면 접근 처리

## 완료 기준

- 네 개의 하단 탭을 이동할 수 있다.
- 로그인과 회원가입 화면을 열고 닫을 수 있다.
- Android 뒤로 가기 버튼이 예상대로 동작한다.

---

# 5단계. Spring API 공통 통신 계층 만들기

모든 화면에서 직접 `fetch()`를 작성하지 않고 API 호출을 한곳에 모은다.

예상 파일:

```text
src/api/
├─ client.ts
├─ authApi.ts
├─ marketApi.ts
└─ paperApi.ts
```

환경별 API 주소 예시:

```text
개발/초기 테스트:
https://futures-paper-trading-production.up.railway.app

Android 에뮬레이터에서 로컬 Spring 사용:
http://10.0.2.2:8080

실제 휴대폰에서 로컬 Spring 사용:
http://PC의-LAN-IP:8080
```

처음에는 HTTPS가 이미 적용된 Railway 주소로 연결한다.

## 가장 먼저 연결할 API

```http
GET /api/binance-futures/btcusdt/depth/latest
```

이 API는 공개 API이므로 로그인과 쿠키 처리 없이 서버 연결부터 확인할 수 있다.

## 공부할 내용

- HTTP 요청과 응답
- GET, POST, PUT, DELETE
- 상태 코드 200, 201, 400, 401, 409, 500
- JSON 직렬화·역직렬화
- `async`, `await`, `Promise`
- TypeScript 요청·응답 타입
- 네트워크 오류와 서버 오류의 차이

## 완료 기준

- 모바일 앱에서 Railway API를 호출한다.
- 받은 호가 JSON을 TypeScript 객체로 변환한다.
- 로딩·성공·오류 상태를 각각 화면에 표시한다.
- API 기본 주소가 화면 컴포넌트 여기저기에 중복되지 않는다.

---

# 6단계. 기존 Spring 세션 쿠키로 인증 연결하기

1차 버전에서는 Java 인증 코드를 바꾸지 않고 아래 API를 그대로 사용한다.

| 기능 | 요청 |
|---|---|
| 회원가입 | `POST /api/auth/signup` |
| 로그인 | `POST /api/auth/login` |
| 현재 사용자 | `GET /api/auth/me` |
| 로그아웃 | `POST /api/auth/logout` |

로그인 흐름:

```text
이메일/비밀번호 입력
→ POST /api/auth/login
→ Spring이 SESSION 쿠키 발급
→ GET /api/auth/me
→ 사용자 정보 화면 표시
```

## 구현할 내용

- 회원가입 화면
- 로그인 화면
- 인증 상태를 관리하는 `AuthProvider` 또는 인증 Hook
- 모든 인증 요청에 쿠키 포함
- 앱 시작 시 `/api/auth/me`로 로그인 상태 확인
- 401 응답 시 로그인 만료 처리
- 로그아웃 후 계정 정보 초기화

## 반드시 실제 기기에서 시험할 항목

1. 로그인 직후 `/me`가 성공하는가?
2. 다른 보호 API에도 SESSION 쿠키가 전달되는가?
3. 앱을 완전히 종료했다가 실행해도 로그인 상태가 유지되는가?
4. 로그아웃하면 세션이 실제로 무효화되는가?
5. Android와 iOS에서 결과가 같은가? iOS는 나중에 별도로 확인한다.

React Native 기본 네트워크 계층에서 쿠키 유지가 불안정하면 모바일용 쿠키 관리 라이브러리와 Development Build를 사용한다. 이 단계에서도 먼저 모바일 쪽 해결을 시도하고 Spring Java 코드는 그대로 둔다.

## 완료 기준

- 회원가입, 로그인, 현재 사용자 조회, 로그아웃이 모두 동작한다.
- 로그인하지 않은 상태에서는 거래 기능을 실행할 수 없다.
- 세션 만료 시 앱이 무한 요청하지 않고 로그인 화면을 보여준다.

---

# 7단계. 실시간 호가 SSE 연결하기

기존 Spring API:

```http
GET /api/binance-futures/btcusdt/depth/stream
Accept: text/event-stream
```

웹의 `EventSource`는 브라우저 전용이므로 React Native 호환 SSE 클라이언트를 사용한다. 이렇게 하면 Spring SSE 코드는 그대로 둘 수 있다.

## 구현할 내용

- SSE 연결 시작과 종료
- 호가 JSON 파싱
- 매수·매도 호가 목록 표시
- 현재 중간 가격 계산
- 연결 끊김 표시
- 앱이 백그라운드로 가면 연결 정리
- 앱이 다시 활성화되면 재연결
- 지수 백오프 방식의 재연결

## 공부할 내용

- 일반 HTTP 요청과 스트리밍 연결의 차이
- SSE 이벤트 형식
- React `useEffect`의 생성·정리 함수
- 앱 foreground/background 생명주기
- 너무 잦은 렌더링을 줄이는 방법

## 완료 기준

- 호가가 실시간으로 변경된다.
- 화면을 이동하거나 앱을 백그라운드로 보냈을 때 불필요한 연결이 남지 않는다.
- 네트워크를 껐다 켜도 자동 복구된다.

---

# 8단계. 계좌·포지션 조회 화면 만들기

기존 Spring API:

| 기능 | 요청 |
|---|---|
| 계좌·포지션 | `GET /api/paper/account` |
| 체결 내역 | `GET /api/paper/fills` |
| 주문 목록 | `GET /api/paper/orders` |
| 레버리지 변경 | `PUT /api/paper/account/leverage` |

## 구현할 내용

- 현금 잔액
- 실현·미실현 손익
- 평가자산
- 사용 증거금과 주문 가능 금액
- 현재 포지션과 진입가
- 청산 예상 가격
- 레버리지 변경
- 대기 주문과 체결 내역 목록
- 당겨서 새로고침

## 공부할 내용

- 서버 상태와 화면 상태의 차이
- 여러 API를 동시에 요청하는 방법
- 목록의 안정적인 key
- 숫자와 가격 포맷팅
- null 포지션 처리

## 완료 기준

- 웹과 모바일에서 같은 계정으로 로그인했을 때 동일한 잔액과 주문이 보인다.
- 포지션이 없어도 오류 없이 빈 상태가 표시된다.
- 401, 빈 목록, 서버 오류가 서로 다른 UI로 표시된다.

---

# 9단계. 모의 주문 기능 구현하기

기존 Spring API:

| 기능 | 요청 |
|---|---|
| 주문 생성 | `POST /api/paper/orders` |
| 주문 취소 | `DELETE /api/paper/orders/{id}` |

## 구현할 내용

- 시장가·지정가 선택
- 매수·매도 선택
- 수량 입력과 검증
- 지정가 입력
- 호가 가격을 누르면 주문 가격에 반영
- 주문 전 확인 창
- 중복 터치 방지
- 주문 성공 후 계좌·주문·체결 내역 새로고침
- 대기 주문 취소
- 현재 포지션 종료
- 서버 오류 메시지 표시

## 안전 규칙

- 앱에서 계산한 값만 믿지 않는다.
- 최종 주문 검증과 잔액 계산은 기존 Spring 서버가 담당한다.
- 버튼을 연속으로 눌러 같은 주문이 여러 번 나가지 않도록 한다.
- 실제 자산이 아닌 모의투자임을 화면에 명확하게 표시한다.

## 완료 기준

- 시장가 주문, 지정가 주문, 주문 취소, 포지션 종료가 동작한다.
- 주문 결과가 웹사이트에서도 동일하게 확인된다.
- 잘못된 수량과 잔액 부족 오류가 사용자에게 이해 가능한 문장으로 표시된다.

---

# 10단계. 모바일 캔들 차트 구현하기

현재 웹의 `lightweight-charts`는 브라우저 DOM을 사용하므로 React Native 화면에서 그대로 사용할 수 없다. 차트 UI는 모바일용으로 다시 구현한다.

## 선택 가능한 방향

1. React Native용 차트 라이브러리 사용
2. `react-native-webview` 안에 차트 전용 HTML을 포함
3. 처음에는 간단한 가격선만 구현하고 캔들 차트는 후속 버전으로 미루기

첫 MVP에서는 거래·인증 기능을 먼저 완성한 뒤 차트를 추가한다.

## 데이터 연결

- 과거 봉: 기존 웹과 같은 Binance Futures Kline REST
- 실시간 봉: 기존 웹과 같은 Binance Futures Kline WebSocket
- 앱 코드에는 Binance 비밀키를 넣지 않는다.

## 공부할 내용

- 캔들 데이터의 OHLC 구조
- 차트 좌표와 확대·이동
- WebSocket 연결과 재연결
- 앱 번들 안에 비밀키를 넣으면 안 되는 이유

## 완료 기준

- 과거 캔들이 표시된다.
- 현재 캔들이 실시간으로 갱신된다.
- 화면 이동과 앱 백그라운드 전환 시 WebSocket이 정리된다.

---

# 11단계. 실제 기기 테스트와 품질 보완하기

## 기능 테스트

- 신규 회원가입
- 로그인 성공·실패
- 앱 재실행 후 로그인 유지
- 로그아웃
- 실시간 호가 연결·재연결
- 시장가·지정가 주문
- 주문 취소
- 포지션 종료
- 레버리지 변경
- 세션 만료

## 모바일 사용성 테스트

- 작은 화면과 큰 화면
- 세로 모드
- Android 뒤로 가기
- 키보드 열림과 닫힘
- 느린 네트워크
- 비행기 모드
- 앱 백그라운드 복귀
- 긴 이메일과 큰 숫자
- 로딩 중 연속 터치

## 코드 품질

- 화면 컴포넌트에 API 주소를 직접 적지 않는다.
- API 오류 변환을 공통 처리한다.
- 색상과 간격을 theme 파일에 모은다.
- TypeScript의 `any` 사용을 최소화한다.
- 인증 상태와 거래 상태를 분리한다.
- 비밀번호, 세션 값, 토큰을 로그에 출력하지 않는다.

## 완료 기준

- 주요 흐름을 실제 Android 기기에서 처음부터 끝까지 수행할 수 있다.
- 네트워크가 끊겨도 앱이 종료되지 않는다.
- 오류가 발생했을 때 사용자가 다음 행동을 알 수 있다.

---

# 12단계. Android 내부 테스트와 Google Play 출시 준비

## 앱 빌드 준비

- Expo 계정과 EAS 설정
- Android 패키지 이름 확정
- 앱 이름, 아이콘, 스플래시 화면
- 버전과 Android versionCode 관리
- Development/Preview/Production 환경 분리
- Preview APK로 실제 기기 설치 테스트
- Production AAB 생성

## Google Play 준비

- Google Play 개발자 계정
- 앱 설명과 스크린샷
- 개인정보처리방침 URL
- 이용약관
- 모의투자이며 실제 자산 거래가 아니라는 고지
- 데이터 수집·보관·삭제 설명
- 콘텐츠 등급과 데이터 보안 설문
- 내부 테스트 트랙 등록

## 공개 출시 전에 별도로 확인할 것

- Binance 시세 데이터의 공개 앱 사용 및 재배포 허용 범위
- 광고를 넣는 경우 상업적 사용 허용 범위
- Google 광고 정책과 동의 화면
- 현금 입출금, 실제 주문 중계, 현금성 보상을 제공하지 않는지 확인

첫 공개 테스트는 광고 없이 진행하고, 시세 사용 권한과 광고 정책을 확인한 뒤 광고를 추가한다.

## 완료 기준

- 내부 테스트 사용자가 Google Play 링크로 앱을 설치할 수 있다.
- 설치된 앱이 Railway Spring 서버와 HTTPS로 통신한다.
- 치명적인 오류 없이 핵심 모의투자 흐름을 완료할 수 있다.

---

# 첫 번째 MVP 완료 조건

아래 항목이 모두 되면 첫 번째 모바일 앱 완성본으로 본다.

- [ ] 앱 실행
- [ ] 회원가입
- [ ] 로그인과 세션 유지
- [ ] 로그아웃
- [ ] BTCUSDT 실시간 호가
- [ ] 계좌 및 포지션 조회
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

아래 기능은 MVP가 안정된 뒤 하나씩 추가한다.

- 모바일 인증을 Access Token + Refresh Token 방식으로 전환
- Refresh Token을 Expo SecureStore에 저장
- 푸시 알림
- 광고
- 다중 코인
- 관심 종목
- 가격 알림
- 소셜 로그인
- 생체 인증
- iOS 출시
- 오류 수집과 사용 통계

---

# 바로 다음 작업

다음 작업에서는 **2단계만 진행**한다.

1. 이 계획 파일을 보존한다.
2. `mobile/`에 Expo + TypeScript 프로젝트를 구성한다.
3. 기본 예제 코드를 최소화한다.
4. Android 휴대폰 또는 에뮬레이터에서 첫 화면을 실행한다.
5. 실행 과정에서 등장하는 `package.json`, Metro, Expo Go의 역할을 함께 공부한다.

첫 화면 실행이 확인되기 전에는 인증, 주문, 차트 코드를 추가하지 않는다.
