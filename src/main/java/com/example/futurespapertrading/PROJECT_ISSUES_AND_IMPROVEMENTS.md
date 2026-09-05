# 실제 서비스 운영성 검토

**실서비스 기준으로는 아직 보완이 필요합니다.** 특히 모의계좌의 잔고 초과, 주문·체결 기록 불일치, 자동 청산 중단으로 이어지는 문제가 있습니다. 주석은 평가에서 제외했고, 코드는 수정하지 않았습니다.

우선순위가 높은 문제부터 설명드리겠습니다. **P1은 서비스 공개 전에 해결할 문제**, P2는 운영 안정성과 성능을 위해 개선할 문제입니다.

1. **[P1] 주문과 체결 내역이 하나의 트랜잭션으로 저장되지 않습니다.**
   [PaperOrderService.java:286](C:/JavaSpring/futures-paper-trading/src/main/java/com/example/futurespapertrading/paper/service/PaperOrderService.java:286)

   주문을 저장한 다음 체결 내역을 별도로 저장합니다. 체결 저장 중 DB 오류나 서버 종료가 발생하면 **주문에는 체결 완료라고 표시되지만 실제 포지션을 계산하는 체결 기록은 없는 상태**가 남습니다. 대기 주문 체결과 강제청산에도 같은 문제가 있습니다.

   주문 상태 변경과 모든 체결 기록 저장을 하나의 리액티브 트랜잭션으로 묶어야 합니다.

2. **[P1] 여러 주문이 같은 가용잔고를 중복 사용할 수 있습니다.**
   [PortfolioService.java:153](C:/JavaSpring/futures-paper-trading/src/main/java/com/example/futurespapertrading/paper/service/PortfolioService.java:153), [PaperOrderService.java:74](C:/JavaSpring/futures-paper-trading/src/main/java/com/example/futurespapertrading/paper/service/PaperOrderService.java:74)

   가용잔고에서 현재 포지션 증거금만 차감하고, **대기 중인 지정가 주문의 증거금은 예약하지 않습니다.** 따라서 잔고가 10,000인데 각각 8,000의 증거금이 필요한 지정가 주문 두 개를 받아둘 수 있습니다. 나중에 체결할 때도 잔고를 다시 검사하지 않습니다.

   동시에 들어온 시장가 주문도 같은 잔고를 읽고 각각 통과할 수 있습니다. 예약 증거금과 계좌 단위 잠금 또는 버전 검사가 함께 필요합니다.

3. **[P1] 증거금 검증에 사용하는 가격·레버리지가 실제 포지션과 다릅니다. 실제 계산 코드로 재현했습니다.**
   [PaperOrderService.java:98](C:/JavaSpring/futures-paper-trading/src/main/java/com/example/futurespapertrading/paper/service/PaperOrderService.java:98)

   테스트 호가를 60,000으로 두고, 10배 레버리지에서 `매도 지정가 1 / 수량 2 BTC`를 입력하면 필요 증거금을 **0.2**로 계산합니다. 실제로는 60,000에 매도 체결되므로 필요한 증거금은 **12,000**입니다.

   또한 기존 1배 포지션을 유지하면서 계좌 설정을 50배로 바꾸면, 추가 주문은 50배 기준으로 검사하지만 합쳐진 포지션은 계속 1배로 계산됩니다.

   **체결 예정 가격과 체결 후 포지션에 실제 적용될 레버리지**를 기준으로 증거금 증가분을 검증해야 합니다.

4. **[P1] 아주 작은 주문 수량 하나로 이후 계좌 계산이 실패할 수 있습니다.**
   [CreateOrderRequest.java:29](C:/JavaSpring/futures-paper-trading/src/main/java/com/example/futurespapertrading/paper/dto/CreateOrderRequest.java:29), [PositionCalculator.java:51](C:/JavaSpring/futures-paper-trading/src/main/java/com/example/futurespapertrading/paper/domain/PositionCalculator.java:51)

   수량은 양수인지 확인하지만 소수 자릿수와 최소 주문 단위를 검사하지 않습니다. 첫 주문 수량이 `0.000000001`이면 검증을 통과하지만, `NUMERIC(38,8)` 저장 과정에서 0이 됩니다. PostgreSQL도 선언된 소수 자릿수에 맞춰 반올림합니다. [공식 문서](https://www.postgresql.org/docs/16/datatype-numeric.html)

   이렇게 저장된 체결을 읽으면 평균 진입가 계산에서 **0으로 나누는 예외**가 발생합니다. H2의 동일 컬럼 타입과 실제 `PositionCalculator`로 재현했습니다. 최소 수량·자릿수 검증과 DB의 `CHECK(quantity > 0)` 제약이 필요합니다.

5. **[P1] 시세 연결이 끊기면 복구하지 않고, 마지막 가격으로 주문은 계속 체결합니다.**
   [BinanceFuturesRawDepthStreamer.java:94](C:/JavaSpring/futures-paper-trading/src/main/java/com/example/futurespapertrading/market/stream/BinanceFuturesRawDepthStreamer.java:94), [PaperOrderService.java:136](C:/JavaSpring/futures-paper-trading/src/main/java/com/example/futurespapertrading/paper/service/PaperOrderService.java:136)

   WebSocket은 부팅 시 한 번 연결하며, 종료·오류 이후 재연결이 없습니다. 연결이 끊기면 호가 갱신, 지정가 매칭, 청산 트리거가 중단됩니다.

   그런데 마지막 호가는 무기한 보관하고, 주문에서는 호가의 존재만 검사합니다. 따라서 **시세는 멈췄는데 과거 가격으로 시장가 주문을 받는 상태**가 됩니다. 재연결 처리와 별도로, 수신 시각을 검사해 오래된 호가로는 체결하지 않도록 해야 합니다.

6. **[P1] 청산 검사가 늦어지면 자동 청산 구독 자체가 종료됩니다. 재현했습니다.**
   [LiquidationMonitor.java:35](C:/JavaSpring/futures-paper-trading/src/main/java/com/example/futurespapertrading/paper/service/LiquidationMonitor.java:35)

   `sample(1초) → concatMap(runOnce)` 구조에서 이전 검사가 다음 샘플 시점까지 끝나지 않으면 `OverflowException`이 발생할 수 있습니다. 내부 `onErrorResume`는 앞 단계인 `sample`에서 발생한 오류를 처리하지 못합니다.

   프로젝트에서 사용하는 Reactor 버전으로 동일 흐름을 재현했고, 작업 지연을 해제한 뒤에도 청산 검사가 재개되지 않았습니다. 명시적인 역압 처리와 구독 종료 감지·복구가 필요합니다.

7. **[P1] ‘포지션 종료’ 버튼이 반대 포지션을 새로 열 수 있습니다.**
   [ClosePositionButton.tsx:21](C:/JavaSpring/futures-paper-trading/frontend/src/paper/components/ClosePositionButton.tsx:21)

   화면에 표시된 수량으로 일반 반대 방향 시장가 주문을 보냅니다. 화면 갱신 전에 다른 탭이나 대기 주문이 기존 포지션을 닫았다면, 종료 버튼이 새로운 반대 포지션을 생성합니다. 종료 성공 후 계좌 갱신 완료를 기다리지 않고 버튼을 다시 활성화하는 점도 문제입니다.

   서버에 **현재 포지션을 감소시키는 용도로만 동작하는 종료 API 또는 `reduce-only` 주문**이 필요합니다.

운영 과정에서 추가로 개선할 부분도 있습니다.

| 우선순위 | 문제와 영향 | 개선 방향 |
|---|---|---|
| P2 | [대기 주문 매칭](C:/JavaSpring/futures-paper-trading/src/main/java/com/example/futurespapertrading/paper/service/PendingOrderMatcher.java:41)이 느려지면 과거 호가가 누적됩니다. 이후 생성된 주문을 주문 생성 전 호가로 체결할 수도 있습니다. | 버퍼·허용 지연을 제한하고 주문과 시세의 시간 관계를 검증 |
| P2 | [청산 검사](C:/JavaSpring/futures-paper-trading/src/main/java/com/example/futurespapertrading/paper/service/LiquidationService.java:48)가 매초 모든 계좌의 전체 주문·체결 이력을 다시 읽습니다. 프론트도 전체 내역을 반복 조회합니다. | 현재 포지션을 증분 갱신하고 열린 포지션만 검사, 내역 페이지 처리와 조회 인덱스 추가 |
| P2 | [회원가입](C:/JavaSpring/futures-paper-trading/src/main/java/com/example/futurespapertrading/auth/service/AuthService.java:63)에서 BCrypt 해싱을 리액티브 흐름 안에서 직접 실행합니다. 가입 요청이 몰리면 이벤트 처리 스레드를 오래 점유할 수 있습니다. | 해싱 실행을 별도 제한된 스케줄러로 분리하고 요청량 제한 |
| P2 | [CSRF 토큰](C:/JavaSpring/futures-paper-trading/frontend/src/shared/csrf.ts:81)을 계속 재사용합니다. 익명 세션 만료나 서버 재시작 후 로그인·회원가입이 403으로 반복 실패할 수 있습니다. | 로그인 전 토큰 동기화 또는 CSRF 실패에 한정한 토큰 갱신 처리 |

계층 분리, 거래 계산을 순수 함수로 추출한 점, `BigDecimal` 사용, 사용자 소유권 검사와 CSRF 적용은 유지할 만한 설계입니다. 가장 먼저 보완할 영역은 **주문·체결·계좌 상태를 일관되게 변경하는 경계와 백그라운드 작업의 장애 복구**입니다.

검증 결과는 백엔드 **35개 테스트 통과**, 프론트 **TypeScript 검사·프로덕션 빌드 통과**입니다. 다만 기존 테스트에는 위 트랜잭션 실패·동시 주문·스트림 지연 시나리오를 검증하는 테스트가 부족합니다. 운영 PostgreSQL에 대한 장애 주입이나 부하 테스트는 수행하지 않았습니다.
