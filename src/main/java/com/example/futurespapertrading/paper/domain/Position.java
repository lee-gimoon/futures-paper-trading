package com.example.futurespapertrading.paper.domain;

import java.math.BigDecimal;

// 계좌 화면을 만들기 전에 백엔드가 체결 기록으로 계산해 낸 중간 결과.
//   DB 테이블에 저장되는 엔티티가 아니고, API 응답용 DTO도 아니다.
//   PositionCalculator가 paper_fills(체결 기록)를 시간순으로 계산해서 만들고,
//   PortfolioService·MarginCalculator가 이 값을 받아 미실현 PnL, 사용 증거금, 청산가를 계산한다.
//   signedQuantity = 부호 있는 보유 수량. 양수 = 롱, 음수 = 숏, 0 = flat(포지션 없음).
//   averageEntryPrice = 현재 열린 포지션의 평균 진입가. flat(포지션 없음)이면 0.
//   realizedPnl = 지금까지 포지션을 줄이거나 닫으며 확정된 누적 실현손익.
//   unrealizedPnl은 현재 시장가격(mid)이 있어야 하므로 여기 두지 않고 PortfolioService에서 계산한다.
public record Position(
        BigDecimal signedQuantity,
        BigDecimal averageEntryPrice,
        BigDecimal realizedPnl
) {
}

// 이 객체 자체가 API response에 들어가는 것은 아니다.
//   필드값들이 PortfolioService에서 PortfolioResponse 조립에 쓰인다.
//   averageEntryPrice, realizedPnl은 거의 그대로 응답에 들어가고,
//   signedQuantity는 side/quantity로 가공되어 들어간다.
