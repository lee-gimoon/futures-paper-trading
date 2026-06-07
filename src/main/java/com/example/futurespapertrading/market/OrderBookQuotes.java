package com.example.futurespapertrading.market;

import java.math.BigDecimal;   // 반환 타입 — bestBid/bestAsk 가격(가격은 double 아닌 BigDecimal)
import java.util.Comparator;   // max/min의 비교 기준 — naturalOrder()(BigDecimal 값 크기 순)

// 호가 snapshot에서 bestBid / bestAsk를 뽑는 헬퍼(helper).
//
// ── bestBid / bestAsk 란? (= 최우선 호가, top of book) ──
//   bids(매수호가) = "이 가격에 사겠다"는 주문들 / asks(매도호가) = "이 가격에 팔겠다"는 주문들.
//   bestBid = bids 중 가장 '높은' price — 지금 제일 비싸게 사주겠다는 값.
//   bestAsk = asks 중 가장 '낮은' price — 지금 제일 싸게 팔겠다는 값.
//   이 둘은 스프레드의 양 끝(매수쪽 꼭대기 / 매도쪽 바닥)이라, 새 주문이 닿으면 '가장 먼저' 체결된다.
//
// ── 왜 'best'(최선) 인가? = 당장 거래하려는 사람에게 '가장 유리한' 가격이라서 ──
//   지금 시장가로 팔려면 → bids를 친다 → 그중 제일 비싼 bestBid에 파는 게 가장 이득.
//   지금 시장가로 사려면 → asks를 친다 → 그중 제일 싼  bestAsk에 사는 게 가장 이득.
//   즉 'best' = 각 방향에서 즉시 체결 가능한 값 중 가장 좋은(=최우선) 가격.
//
// ── 왜 'helper'(헬퍼) 라는 이름? ──
//   엔티티(도메인 객체)도 큰 부품도 아니고, "snapshot → best 가격"이라는 작은 계산 하나를
//   여러 곳(엔진·컨트롤러·matcher·PnL·프론트)이 똑같이 쓰므로, 그 계산을 한 곳에 모아 '거들어주는'
//   재사용 도구다. 상태 없는 static 메서드 모음이라 객체를 만들 필요도 없다 → 유틸/헬퍼라 부른다.
//   (프론트 quote.ts(deriveQuote)가 하던 같은 역할의 백엔드 미러)
//
// "정렬 가정 안 함": bids.get(0)/asks.get(0)가 best라고 믿지 않고, 매번 max/min으로 직접 고른다.
//   (도착 순서가 흐트러져도 안전. quote.ts도 Math.max(...bids)/Math.min(...asks)로 같은 방식)
public final class OrderBookQuotes {

    private OrderBookQuotes() {}   // 인스턴스 만들 일 없는 유틸 → 생성자 막음

    // 한쪽이 비어 있으면 계산 불가 → null. (수신 직후 한쪽이 잠깐 빌 수 있음 — quote.ts와 동일 처리)
    public static BigDecimal bestBid(OrderBookSnapshot snapshot) {
        return snapshot.bids().stream()
                .map(OrderBookLevel::price)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    public static BigDecimal bestAsk(OrderBookSnapshot snapshot) {
        return snapshot.asks().stream()
                .map(OrderBookLevel::price)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }
}
