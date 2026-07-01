package com.example.futurespapertrading.paper.dto;

import java.math.BigDecimal;

// Service는 주문 처리, 계산, 저장처럼 동작을 수행하는 객체다.
// record는 그런 동작 수행 객체가 아니라, 이름 그대로 관련된 값을 하나의 의미 있는 기록(record)으로 묶어 표현하고 전달하는 객체를 만들 때 사용한다.
// 그래서 PortfolioResponse는 계좌를 조작하는 서비스 객체가 아니라,
// GET 요청 시점의 계좌 화면 상태를 하나의 불변 응답 기록(record)으로 담아 전달하는 record 객체다.

// 계좌 화면 1개분 응답 DTO. GET /api/paper/account 가 돌려준다.
//   cashBalance      = BigDecimal 객체가 표현하는 시드 현금 값(저장값, 불변).
//   realizedPnl      = BigDecimal 객체가 표현하는 체결 내역 기준 확정 손익 값.
//   unrealizedPnl    = BigDecimal 객체가 표현하는 열린 포지션 × 현재 mid 평가 손익 값(포지션/호가 없으면 0).
//   equity           = BigDecimal 객체가 표현하는 총자산 평가액 값(현금 + 실현PnL + 미실현PnL).
//   leverage         = int 원시값이 표현하는 '신규 주문' 레버리지 값(버튼이 설정).
//   usedMargin       = BigDecimal 객체가 표현하는 포지션에 묶인 증거금 값(= 명목/포지션레버리지).
//   availableBalance = BigDecimal 객체가 표현하는 새 주문에 쓸 수 있는 여유 증거금 값(= 현금+실현 - usedMargin).
//   position         = PositionView 객체가 표현하는 현재 포지션 값 묶음. 없으면(flat) null.
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

// PortfolioResponse 객체 구조 예시:
// PortfolioResponse 객체
//  ├─ cashBalance ───────> BigDecimal("10000") 객체
//  ├─ realizedPnl ───────> BigDecimal("120") 객체
//  ├─ unrealizedPnl ─────> BigDecimal("35") 객체
//  ├─ equity ────────────> BigDecimal("10155") 객체
//  ├─ leverage ────────── 10
//  ├─ usedMargin ────────> BigDecimal("500") 객체
//  ├─ availableBalance ──> BigDecimal("9620") 객체
//  └─ position ──────────> PositionView 객체
//                           ├─ symbol ───────────────> String("BTCUSDT") 객체
//                           ├─ side ─────────────────> String("LONG") 객체
//                           ├─ quantity ─────────────> BigDecimal("0.1") 객체
//                           ├─ averageEntryPrice ────> BigDecimal("50000") 객체
//                           ├─ markPrice ────────────> BigDecimal("50350") 객체
//                           ├─ unrealizedPnl ────────> BigDecimal("35") 객체
//                           ├─ notional ─────────────> BigDecimal("5035") 객체
//                           ├─ liquidationPrice ─────> BigDecimal("45000") 객체
//                           └─ leverage ───────────── 10
