package com.example.futurespapertrading.paper.domain;

import java.math.BigDecimal;

// 체결 내역을 누적해 나온 "현재 포지션" 상태. PositionCalculator의 계산 결과 값이다(DB 테이블 아님 — 9단계는 저장 안 함).
//   signedQuantity = 부호 있는 보유 수량.  양수 = 롱(매수 우위), 음수 = 숏(매도 우위), 0 = 포지션 없음(flat).
//   averageEntryPrice = 현재 열린 포지션의 평균 진입가(수량가중평균). flat이면 0.
//   realizedPnl = 지금까지 포지션을 줄이거나 닫으며 확정된 실현 손익의 합.
//   ※ 미실현 PnL은 현재 호가(mid)가 있어야 나오므로 여기 두지 않고 PortfolioService가 따로 계산한다.
public record Position(
        BigDecimal signedQuantity,
        BigDecimal averageEntryPrice,
        BigDecimal realizedPnl
) {
}
