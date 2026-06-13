# 9단계 — 계좌 · 포지션 · PnL (학습 노트)

8단계가 "주문을 호가로 체결해 `paper_fills`에 남긴다"까지 했다면, 9단계는 그 **체결 기록을 계좌의 잔고·포지션·손익으로 본다**.

## 큰 설계 결정: "저장하지 말고 계산하라"

로드맵 9단계는 포지션·실현/미실현 PnL을 **테이블로 저장하지 않는다**. 저장하는 건 `paper_accounts`의 **시드 현금 하나**뿐이고, 나머지는 요청이 올 때마다 `paper_fills`를 다시 읽어 계산한다.

```
저장(DB)          : paper_accounts.cash_balance  (가입 후 1회 적립, 이후 불변)
계산(매 요청)      : 포지션 · 실현 PnL  ← paper_fills 전체를 누적
계산(매 요청+호가) : 미실현 PnL        ← 포지션 × 현재 mid
```

왜 이렇게?
- 로드맵이 정한 방향: "포지션은 주문/체결 내역과 현재 호가로 **먼저 계산**해보고, 구조가 안정되면 별도 테이블로 분리."
- **8단계 코드를 한 줄도 안 건드린다.** 주문 체결 경로(`PaperOrderService`)는 그대로고, 9단계는 읽기·계산만 얹는다.
- 단일 진실 원천이 `paper_fills` 하나라 포지션과 체결 내역이 **절대 어긋날 수 없다**(이중 장부 불일치가 구조적으로 불가능).

대신 비용: 매 조회마다 그 사용자의 체결을 전부 다시 누적한다. 체결이 수만 건이 되면 스냅샷 테이블로 분리할 시점 — MVP 학습 규모에선 무시 가능.

## equity(평가자산) 모델

```
equity = cash_balance(시드) + realizedPnl + unrealizedPnl
```

`cash_balance`는 이익이 나도 안 변한다("입금한 원금"). 번 돈은 `realizedPnl`(확정)과 `unrealizedPnl`(미확정)로 따로 보여주고, 셋을 합친 게 총자산 평가액이다. 로드맵의 3-필드 계좌 모델(cashBalance/realizedPnl/unrealizedPnl)과 그대로 맞는다.

## 포지션 누적 (PositionCalculator)

체결을 **시간 순서(id 오름차순)**로 하나씩 먹으며 세 값을 갱신한다: `qty`(부호 있는 보유수량), `avgEntry`(평균 진입가), `realized`(실현 PnL 누적).

각 체결은 BUY=+, SELL=− 로 부호 수량(`signed`)을 만든 뒤 둘 중 하나:

### ① 포지션이 없거나 같은 방향 → 증가
평균 진입가를 수량가중으로 다시 잡는다.
```
avgEntry = (|qty|·avgEntry + |signed|·price) / (|qty| + |signed|)
qty     += signed
```

### ② 반대 방향 → 축소 / 청산 / 뒤집기
닿는 만큼(`closeQty = min(|signed|, |qty|)`)만 기존 포지션을 닫아 실현 PnL을 확정한다.
```
realized += (price − avgEntry) · closeQty · sign(qty)     // 롱 +1, 숏 −1
qty      += signed
  - 정확히 0      → flat, avgEntry = 0
  - 부호가 뒤집힘 → 남은 수량이 새 포지션, avgEntry = price
  - 그 외(부분청산)→ avgEntry 그대로
```

`sign(qty)` 한 항이 롱/숏을 모두 한 식으로 처리한다:
- 롱(qty>0)을 SELL로 닫으면 `(price−avgEntry)·closeQty` → 비싸게 팔수록 이익(+).
- 숏(qty<0)을 BUY로 닫으면 부호가 뒤집혀 `(avgEntry−price)·closeQty` → 싸게 되살수록 이익(+).

### 워크스루 예시

| 순서 | 체결 | qty | avgEntry | realized |
|---|---|---|---|---|
| 시작 | — | 0 | 0 | 0 |
| 1 | BUY 100×1 | +1 | 100 | 0 |
| 2 | BUY 110×3 | +4 | 107.5 | 0 | ← (100·1+110·3)/4 |
| 3 | SELL 110×1 | +3 | 107.5 | +2.5 | ← (110−107.5)·1, 진입가 유지 |

**뒤집기 예시**: 롱 1@100 → SELL 3@110 → 1개 닫아 +10 실현, 남은 2개가 110에 **숏** 진입(avgEntry=110, qty=−2).

(이 시나리오들은 `PositionCalculatorTest`가 검증한다.)

## 미실현 PnL & 실시간 갱신

```
unrealizedPnl = (mid − avgEntry) × qty(부호)
```
부호 수량이라 롱/숏 모두 한 식. 포지션이 없거나 호가가 아직 없으면 0.

**실시간성**은 폴링이 아니라 **이미 받는 SSE로** 해결한다(6단계 차트가 mid를 재사용한 것과 같은 발상):
- 백엔드 `/account`는 조회 시점의 mid로 미실현 PnL을 한 번 준다(폴링 3초).
- 프론트는 응답의 `avgEntry·quantity·side`에 **실시간 SSE mid**를 곱해 미실현 PnL·equity를 화면에서 다시 계산한다 → 100ms마다 숫자가 움직인다(추가 연결 0개).
- 폴링(3초)은 포지션 자체가 바뀌는 경우(주문 체결, 특히 백그라운드 matcher의 OPEN 지정가 자동 체결)를 따라잡는 용도.

## 계좌 lazy 생성

7단계 회원가입 코드를 안 건드리려고, 계좌는 가입 때가 아니라 **처음 `/account` 조회 때** 만든다(`switchIfEmpty` → 시드 현금으로 INSERT). `user_id`에 UNIQUE를 걸어 한 사용자당 1개를 DB가 보장한다.

## 레버리지 · 마진 · 자동 청산 (로드맵에서 당겨온 부분)

로드맵은 레버리지/마진/청산을 뒤로 미뤘지만, 바이낸스식 % 바·USDT 사이징을 위해 9단계에서 당겨 구현했다. **격리(isolated) 마진 · 유지증거금률(MMR) 0 · 수수료 0 · 단일 심볼** 가정.

```
walletBalance    = 현금 + 실현PnL
usedMargin       = 평균진입가 × |수량| / L          (포지션에 묶인 증거금, MarginCalculator)
availableBalance = walletBalance − usedMargin        (새 주문에 쓸 여유, 0 미만이면 0)
청산가(격리, MMR0): 롱 = 진입가 × (1 − 1/L)   숏 = 진입가 × (1 + 1/L)
```

- **레버리지 (두 종류로 분리)**:
  - **계좌 레버리지** `paper_accounts.leverage`(기본 10x, `PUT /api/paper/account/leverage`로 변경) = '신규 주문'용. % 바·매수가능 검증·주문에 박는 값. 버튼이 바꾼다.
  - **포지션 레버리지** = 이미 연 포지션이 '진입 시점'에 박아 둔 값. 사용증거금·청산가·가용잔고는 이걸로 계산해 **버튼을 눌러도 안 흔들린다**(격리 마진의 포지션 레버리지 고정). 구현: 주문마다 `paper_orders.leverage`에 진입 시점 레버리지를 기록 → `PositionCalculator.openLeverage`가 체결을 시간순으로 보며 '현재 열린 run이 진입한 시점의 레버리지'를 복원(같은 방향 추가는 유지, 뒤집히면 갱신, 닫히면 계좌값). `PortfolioResponse.position.leverage`로 따로 내려준다.
- **매수가능 검증**: `PaperOrderService.placeOrder`가 새로 여는 수량의 필요증거금(= 여는명목/L)이 `availableBalance`를 넘으면 `InsufficientMarginException`(400). 순수 축소/청산 주문은 증거금 0이라 통과.
- **% 바**: 프론트에서 `최대수량 = availableBalance × L ÷ 기준가`, 25/50/75/100% 곱해 입력칸 채움. (백엔드는 그대로 BTC 수량을 받음 — USDT 입력·% 바는 프론트 환산)
- **자동 청산**: `LiquidationMonitor`가 snapshot을 1초 샘플로 구독 → `LiquidationService.runOnce()`가 전 계좌를 훑어 `mark`가 청산가를 넘긴 포지션을 찾으면, 반대방향 시장가 주문+체결을 **청산가에** 만들어 강제 청산한다. 이때 실현 PnL = (청산가−진입가)×수량 = **−usedMargin**(격리에서 묶인 증거금만 손실). 단일 진실 원천이 `paper_fills`라, 청산도 "체결 1건 추가"로 자연스럽게 반영된다.
  - 청산가는 진입가·레버리지로만 정해져 mark와 무관 → 프론트는 백엔드 값을 그대로 표시(폴링 사이 재계산 불필요).
  - MVP: 매 틱 전 계좌 스캔(O(계좌수)). 사용자가 많아지면 '포지션 보유 계좌' 인덱스로 최적화.

## 여전히 MVP라 미루는 것
- 수수료/펀딩비 → 0 (`fill.fee` 0).
- 교차(cross) 마진·유지증거금률(MMR) 단계·실제 청산 수수료 → 격리·MMR0으로 단순화.
- `paper_positions`/`balance_ledger` 테이블 → 계산이 안정되면 분리.

## 파일

| 파일 | 역할 |
|---|---|
| `domain/PaperAccount` | `paper_accounts` 엔티티(시드 현금 + 레버리지) |
| `domain/Position` | 계산 결과 값(부호수량·평균진입가·실현PnL) |
| `domain/PositionCalculator` | ★ 체결목록 → 포지션 순수 함수 |
| `domain/MarginCalculator` | ★ 포지션+레버리지 → 명목·사용증거금·청산가·청산판정 순수 함수 |
| `repository/PaperAccountRepository` | 계좌 조회/저장 |
| `repository/PaperFillRepository#findByUserIdOrderByIdAsc` | 유저 체결 전부(JOIN, 오름차순) |
| `service/PortfolioService` | 포지션·미실현·equity·마진 조립 + 레버리지 변경 + `accountState`(공용) |
| `service/LiquidationService` | 전 계좌 청산 판정 + 강제 청산 체결 |
| `service/LiquidationMonitor` | snapshot 1초 샘플 구독 → `runOnce()` (matcher와 같은 패턴) |
| `service/PaperOrderService#placeOrder` | 매수가능(증거금) 검증 추가 |
| `dto/PortfolioResponse`(+PositionView) | 계좌 화면 응답(레버리지·증거금·가용잔고·청산가 포함) |
| `dto/FillResponse` · `dto/SetLeverageRequest` | 체결 내역 응답 · 레버리지 변경 요청 |
| `exception/InsufficientMarginException` | 증거금 부족(400) |
| `controller/PortfolioController` | `GET /account` · `GET /fills` · `PUT /account/leverage` |
| 프론트 `paper/*` | 주문 폼(Long/Short·BTC↔USDT 토글·레버리지·% 바)·계좌요약(증거금·청산가)·주문목록·체결내역 + `useTrading` 폴링 |
