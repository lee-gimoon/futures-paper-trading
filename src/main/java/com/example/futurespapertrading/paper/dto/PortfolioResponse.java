package com.example.futurespapertrading.paper.dto;

import java.math.BigDecimal;

// 계좌 화면 1개분 응답 DTO. GET /api/paper/account 가 돌려준다.
//   cashBalance      = 시드 현금(저장값, 불변).
//   realizedPnl      = 체결 내역에서 계산한 확정 손익.
//   unrealizedPnl    = 열린 포지션 × 현재 mid 평가 손익(포지션/호가 없으면 0).
//   equity           = (현금 + 실현PnL) + 미실현PnL (총자산 평가액).
//   leverage         = '신규 주문' 레버리지(버튼이 설정). 포지션의 고정 레버리지는 position.leverage에 따로 있다.
//   usedMargin       = 포지션에 묶인 증거금(= 명목/포지션레버리지). 신규 주문 레버리지와 무관 — 고정.
//   availableBalance = 새 주문에 쓸 수 있는 여유 증거금(= 현금+실현 − usedMargin). % 바 사이징의 기준.
//   position         = 현재 포지션. 없으면(flat) null.
public record PortfolioResponse(
        BigDecimal cashBalance,
        BigDecimal realizedPnl,
        BigDecimal unrealizedPnl,
        BigDecimal equity,
        int leverage,
        BigDecimal usedMargin,
        BigDecimal availableBalance,
        PositionView position
) {
    // 포지션 1개의 표시용 값. notional = 명목금액, liquidationPrice = 추정 청산가(격리·MMR0),
    //   leverage = 이 포지션이 진입 시점에 고정한 레버리지(신규 주문 레버리지와 별개 — 버튼 눌러도 안 바뀜).
    public record PositionView(
            String symbol,
            String side,                  // LONG / SHORT
            BigDecimal quantity,          // 절대값
            BigDecimal averageEntryPrice,
            BigDecimal markPrice,         // 계산에 쓴 mid (null 가능)
            BigDecimal unrealizedPnl,
            BigDecimal notional,
            BigDecimal liquidationPrice,
            int leverage
    ) {
    }
}
