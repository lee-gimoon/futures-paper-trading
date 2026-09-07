# Futures Paper Trading Mobile

> Binance USDⓈ-M 선물의 실시간 호가를 보면서 시장가·지정가 주문, 포지션, 손익을 확인할 수 있는 Expo/React Native 모바일 애플리케이션입니다.

웹 클라이언트와 동일한 Spring WebFlux 서버를 사용하며, 실제 자금이 오가지 않는 학습용 모의 선물거래 서비스입니다.

## 모바일 앱 확인하기

| 구분 | 주소 |
| --- | --- |
| 모바일 웹에서 체험 | [모바일 앱 실행하기](https://futures-paper-trading-production.up.railway.app/mobile/) |
| Android APK 다운로드 | 준비 중 |
| 모바일 화면 미리보기 | [실서비스 스크린샷 4장 보기](#앱-화면) |

> APK 설치가 어려운 환경에서는 모바일 웹과 아래 실서비스 스크린샷으로 주요 기능을 확인할 수 있습니다.

### APK 링크 입력 예시

```md
| Android APK 다운로드 | [Android 앱 설치하기](https://APK-다운로드-주소) |
```

## 앱 화면

Railway에 배포된 모바일 웹의 실제 비로그인 화면입니다. 실시간 BTCUSDT 가격·호가와 캔들 차트는 Spring WebFlux 서버에서 받은 데이터로 표시됩니다.

| 실시간 마켓·호가 | 선물 주문 |
| --- | --- |
| <img src="./docs/screenshots/market.png" width="280" alt="실시간 BTCUSDT 가격, 차트와 호가 화면"> | <img src="./docs/screenshots/trade.png" width="280" alt="시장가와 지정가 선물 주문 화면"> |

| 캔들 차트 상세 | 자산·포지션 |
| --- | --- |
| <img src="./docs/screenshots/chart.png" width="280" alt="BTCUSDT 캔들 차트 상세 화면"> | <img src="./docs/screenshots/account.png" width="280" alt="모의 계좌 자산과 포지션 화면"> |

## 주요 기능

- 실시간 BTCUSDT 호가 및 현재 가격 조회
- 과거 캔들과 실시간 가격을 결합한 차트
- 시장가·지정가 BUY/SELL 주문
- 지정가 대기 주문 조회 및 취소
- 1·3·5·10·20·50배 레버리지 선택
- 포지션, 증거금, 실현·미실현 손익 조회
- 주문 및 체결 내역 조회
- 회원가입, 로그인, 로그아웃

## 모바일에서 구현한 부분

- Expo Router 기반 화면 및 탭 이동
- 웹과 동일한 Spring WebFlux REST API 사용
- `expo/fetch`를 이용한 실시간 호가 스트림 수신
- `SESSION` 쿠키와 CSRF 토큰을 사용하는 세션 인증
- 공통 API 클라이언트에서 인증 만료와 네트워크 오류 처리
- 화면 포커스와 앱 활성 상태에 맞춘 데이터 재조회
- EAS 빌드 환경별 운영 API 주소 관리

## 기술 스택

| 분류 | 기술 |
| --- | --- |
| Mobile | Expo, React Native, Expo Router |
| Language | TypeScript |
| UI | React Native, React Native SVG |
| Network | Expo Fetch, REST, streaming response |
| Backend | Java 21, Spring Boot, Spring WebFlux, Reactor |
| Database | PostgreSQL, Spring Data R2DBC |
| Deployment | Railway, Expo Application Services |

## 연결 구조

```text
Binance WebSocket / REST
          ↓
Spring WebFlux 서버 ─── PostgreSQL
          ↓
Expo / React Native 모바일 앱
```

- 운영 API: `https://futures-paper-trading-production.up.railway.app`
- 모바일 앱은 Binance에 직접 주문하지 않습니다.
- 모든 주문은 Spring 서버 안에서만 처리되는 모의 주문입니다.

## 모바일 웹 배포

별도의 Railway 프로젝트나 서비스를 만들지 않습니다. 저장소의 `Dockerfile`이 Expo 웹 빌드 결과를 Spring Boot 정적 리소스에 포함하므로, Railway에 연결된 브랜치로 이 변경 사항을 push하면 기존 서비스가 함께 다시 배포됩니다.

배포가 끝난 뒤 아래 주소에서 확인합니다.

```text
https://futures-paper-trading-production.up.railway.app/mobile/
```

모바일 웹은 같은 출처의 Spring API를 사용하므로 별도의 API 서버 주소나 CORS 설정이 필요하지 않습니다.

## Android 포트폴리오 빌드

`preview` 프로필은 Railway 운영 API를 사용하는 설치 가능한 APK를 생성합니다.

```powershell
cd mobile
npx eas-cli@latest login
npx eas-cli@latest init
npx eas-cli@latest build --platform android --profile preview
```

첫 프로젝트 연결이 완료된 이후에는 `eas init`을 다시 실행하지 않아도 됩니다. 빌드가 끝나면 EAS 빌드 페이지의 설치 주소를 이 문서 상단의 `Android APK 다운로드` 항목에 넣습니다.

## 로컬 실행

```powershell
cd mobile
npm install
npm run start
```

개발용 Spring 서버를 사용하려면 `mobile/.env.local`에 API 주소를 지정합니다.

```dotenv
EXPO_PUBLIC_API_BASE_URL=http://로컬-PC-IP:8080
```

실제 휴대폰에서 실행할 때 `localhost`는 휴대폰 자신을 의미하므로, 같은 네트워크에 연결된 개발 PC의 IP 주소를 사용해야 합니다.

## 확인 명령어

```powershell
npm run typecheck
npm test
npm run lint
npm run doctor
```

## 시연 영상 촬영 순서

약 40~60초 분량으로 다음 순서만 보여주는 것을 권장합니다.

1. 실시간 BTC 가격과 호가 변화
2. 캔들 차트와 시간 간격 변경
3. 시장가 또는 지정가 주문
4. 주문·체결 내역
5. 포지션과 PnL 변화

로그인 화면 입력 과정은 짧게 처리하고, 핵심 거래 흐름이 영상 대부분을 차지하도록 구성합니다.

## 배포 전 체크리스트

- [ ] Railway 서버에서 회원가입·로그인 확인
- [ ] 실시간 호가와 차트 갱신 확인
- [ ] 주문 생성·취소·체결 확인
- [ ] 포지션과 손익 계산 확인
- [ ] Android 실기기에서 APK 설치 및 실행 확인
- [x] `/mobile/` 모바일 웹 동작 확인
- [ ] APK 다운로드 주소 입력
- [ ] 시연 영상 주소 입력
- [x] 화면 스크린샷 4장 추가
- [ ] README의 모든 링크를 시크릿 브라우저에서 확인
