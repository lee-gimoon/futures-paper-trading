package com.example.futurespapertrading.paper.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

// 체결 목록을 시간순으로 계산해 Position record를 만드는 순수 계산기.
//   결과 Position에는 현재 남은 포지션 수량/평단과 누적 실현 PnL이 들어간다.
//   PaperTradingEngine과 같은 성격 — DB·웹을 모르고 입력(fills)만 보고 답을 낸다 → 단위 테스트가 쉽다.
//
// 계산 모델 (선물이라 롱/숏·포지션 뒤집기를 다룬다):
//   체결을 시간 순서대로 하나씩 누적하며 보유 수량(방향 부호 포함)·평균 진입가(VWAP)·실현 PnL을 갱신한다.
//   같은 방향 진입만 있으면 순서 영향이 작지만, 청산/축소/뒤집기가 섞이면
//   직전 포지션 상태에 따라 결과가 달라지므로 반드시 시간순으로 계산해야 한다.
//   각 체결은 둘 중 하나다.
//     ① 포지션이 없거나 같은 방향 → '증가': 평균 진입가(VWAP)를 수량가중으로 다시 잡는다.
//     ② 반대 방향 → '축소/청산/뒤집기': 닿는 만큼 기존 포지션을 닫아 실현 PnL을 확정하고,
//        남는 수량이 있으면 그 가격으로 반대 포지션을 새로 연다.
//   이 계산 모델이 주문 생성 전 증거금 검증과 계좌 화면의 포지션 계산에 함께 쓰인다.
//
// 수수료(fill.fee)는 8·9단계 MVP라 0이므로 PnL에 넣지 않는다(로드맵: 수수료/펀딩비는 나중 단계).
public final class PositionCalculator {

    private PositionCalculator() {}   // 상태나 주입받을 의존성이 없는 static 계산 유틸리티라 인스턴스 생성 금지

    public static Position compute(List<PaperFill> fills) {
        BigDecimal signedQty = BigDecimal.ZERO;  // 부호 있는 순포지션 수량 (+롱 / -숏 / 0 flat)
        BigDecimal avgEntry = BigDecimal.ZERO;   // 현재 열린 포지션의 평균 진입가(VWAP, flat이면 0)
        BigDecimal realized = BigDecimal.ZERO;   // 확정된 실현 PnL 누적

        for (PaperFill f : fills) {
            // 체결 수량에 방향 부호를 붙인다: BUY는 +수량, SELL은 -수량.
            BigDecimal signedFillQuantity = OrderSide.BUY.name().equals(f.side()) ? f.quantity() : f.quantity().negate();
            BigDecimal price = f.price();
            BigDecimal existingOpenQuantity = signedQty.abs(); // 기존 포지션 수량의 절대값(롱/숏 부호 제거).
            BigDecimal fillQuantity = signedFillQuantity.abs(); // 이번 체결 수량의 절대값(BUY/SELL 부호 제거).
            BigDecimal updatedSignedQty = signedQty.add(signedFillQuantity); // 이번 체결을 반영한 순포지션 수량.

            // signum()은 부호만 반환한다: 양수면 1, 0이면 0, 음수면 -1.
            // ① 포지션 없음(flat) 또는 현재 포지션과 같은 방향 체결 → 새 진입/같은 방향 추가 진입.
            // 이 경우 실현손익은 발생하지 않고, 현재 열린 포지션의 평균 진입가(VWAP)만 수량가중평균으로 다시 계산한다.
            if (signedQty.signum() == 0 || signedQty.signum() == signedFillQuantity.signum()) {
                BigDecimal updatedOpenQuantity = existingOpenQuantity.add(fillQuantity); // 증가 후 총 포지션 수량.
                avgEntry = existingOpenQuantity.multiply(avgEntry).add(fillQuantity.multiply(price))
                        .divide(updatedOpenQuantity, 8, RoundingMode.HALF_UP); // 새 평균 진입가(VWAP) = 총 진입금액 / 총수량.
                signedQty = updatedSignedQty; // 현재 순포지션 수량에 이번 체결 수량을 반영.
            } else {
                // ② 반대 방향 체결 → 기존 포지션을 줄이거나 닫고, 체결 수량이 더 크면 반대 포지션으로 뒤집는다.
                // closedQuantity = 이번 체결 중 기존 포지션을 실제로 닫는 수량 = 둘 중 작은 값(a.min(b)).
                BigDecimal closedQuantity = fillQuantity.min(existingOpenQuantity);
                // existingPositionDirection = 기존 포지션 방향. 롱은 +1, 숏은 -1. 손익 부호를 맞추기 위한 값이다.
                BigDecimal existingPositionDirection = BigDecimal.valueOf(signedQty.signum());
                // 이번에 닫힌 수량의 실현손익.
                //   롱: (매도 체결가 - 평균진입가) × 닫은수량. 비싸게 팔수록 이익.
                //   숏: (매수 체결가 - 평균진입가) × 닫은수량 × -1. 싸게 살수록 이익.
                realized = realized.add(price.subtract(avgEntry).multiply(closedQuantity).multiply(existingPositionDirection));

                if (updatedSignedQty.signum() == 0) {
                    avgEntry = BigDecimal.ZERO;        // 정확히 청산 → flat
                } else if (signedQty.signum() != updatedSignedQty.signum()) {
                    avgEntry = price;                  // 다 닫고 남은 수량이 반대 포지션을 새로 엶 → 진입가 = 이 체결가
                }
                // 그 외(부분 청산, 방향 유지) → 평균 진입가(VWAP) 그대로.
                signedQty = updatedSignedQty;
            }
        }
        // signedQty = 체결 기록을 누적 계산해 나온 부호 있는 순포지션 수량(롱 +, 숏 -, flat 0).
        // avgEntry = 현재 열린 포지션의 평균 진입가(VWAP, flat이면 0).
        // realized = 체결 기록을 누적 계산해 나온 누적 실현손익 값.
        return new Position(signedQty, avgEntry, realized);
    }

    // 체결 이력을 재생해 현재 열린 포지션의 고정 레버리지를 반환한다.
    // 열린 포지션이 있으면 해당 포지션 run을 시작한 주문의 레버리지를, flat이면 fallback을 반환한다.
    // orderLeverage는 orderId별 주문 당시 레버리지다.
    public static int openPositionLeverage(List<PaperFill> fills, Map<Long, Integer> orderLeverage, int fallback) {
        BigDecimal signedQty = BigDecimal.ZERO; // 누적 순포지션 수량. 부호로 롱(+), 숏(-), flat(0)을 판단한다.
        int positionLeverage = fallback; // 열린 포지션이 없으면 계좌의 현재 레버리지를 기본값으로 본다.

        for (PaperFill f : fills) {
            int positionDirectionBeforeFill = signedQty.signum(); // 이번 체결 전 포지션 방향.

            signedQty = signedQty.add(
                    OrderSide.BUY.name().equals(f.side())
                            ? f.quantity()
                            : f.quantity().negate()
            );

            int positionDirectionAfterFill = signedQty.signum(); // 이번 체결 후 포지션 방향.

            if (positionDirectionAfterFill == 0) { // 이번 체결 후 순포지션 수량이 0이다.
                positionLeverage = fallback; // 완전히 닫힌 상태면 다음 진입 전까지 기본 레버리지로 되돌린다.
            } else if (positionDirectionBeforeFill != positionDirectionAfterFill) { // 이번 체결 전후로 포지션 방향 상태가 바뀌었다.
                positionLeverage = orderLeverage.getOrDefault(f.orderId(), fallback); // fill의 orderId로 orderLeverage에서 주문 당시 레버리지를 찾아 새 포지션 run에 적용한다.
            }
        }

        return positionLeverage; // 마지막 체결까지 재생했을 때의 현재 포지션 레버리지.
    }
}
