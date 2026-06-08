package com.example.futurespapertrading.market;

import java.util.List;

// 한 시점(eventTime)의 호가창 스냅샷. bids 20개 + asks 20개.
//   bids = 매수 호가 목록 ("이 가격에 사고 싶다"고 걸려 있는 주문들). 가격이 높을수록 시장가에 가까움.
//     └ bid는 영어로 "(경매에서) 값을 부르다/입찰하다". 사려는 사람이 "이만큼 내겠다"고 부르는 가격 → 매수.
//   asks = 매도 호가 목록 ("이 가격에 팔고 싶다"고 걸려 있는 주문들). 가격이 낮을수록 시장가에 가까움.
//     └ ask는 영어로 "(값을) 요구하다/부르다" (asking price = 부르는 값). 파는 사람이 "이만큼은 받아야겠다"고 요구하는 가격 → 매도.
//   → bids의 최고가 < asks의 최저가, 그 사이 빈 구간이 스프레드(spread).
// 정렬 가정에 기대지 않는다 — bids는 내림차순, asks는 오름차순으로 도착하지만
// 사용 시점에 다시 정렬해서 쓸 수 있도록 받은 순서를 그대로 보관만 한다.

// 스키마 = "데이터가 항상 가질 모양·필드들을 미리 적어둔 약속". 자바 record/class는 그 약속을 코드로 적은 것.
public record OrderBookSnapshot(
		String symbol,
		long eventTime,
		List<OrderBookLevel> bids,   // 매수호가 목록(사겠다). 예: [ Level(100.5, 2), Level(100.0, 5), Level(99.5, 1) ]  ← 보통 높은→낮은 가격
		List<OrderBookLevel> asks    // 매도호가 목록(팔겠다). 예: [ Level(101.0, 3), Level(101.5, 4), Level(102.0, 1) ] ← 보통 낮은→높은 가격
) {
}

// 필드 타입이 왜 List<OrderBookLevel>인가 (raw JSON 예시는 OrderBookSnapshotParser.java 참고):
//   1) bids/asks가 JSON 배열([...])로 온다. 길이가 1~20개로 가변이고 한쪽이 비어 있을 수도 있어 가변 컨테이너인 List가 자연스러운 매칭.
//   2) 배열의 각 원소가 또 길이 2짜리 배열 ["price", "quantity"] → 이걸 OrderBookLevel 한 개로 묶는다. 그래서 List<OrderBookLevel>.

// record를 쓰지 않으면 아래처럼 일반 클래스로 같은 역할을 직접 구현해야 한다.
// (final 필드 + 생성자 + 접근자 + equals/hashCode/toString을 전부 손으로 작성)
//
// public final class OrderBookSnapshot {
//     private final String symbol;
//     private final long eventTime;
//     private final List<OrderBookLevel> bids;
//     private final List<OrderBookLevel> asks;
//
//     public OrderBookSnapshot(String symbol, long eventTime,
//                              List<OrderBookLevel> bids, List<OrderBookLevel> asks) {
//         this.symbol = symbol;
//         this.eventTime = eventTime;
//         this.bids = bids;
//         this.asks = asks;
//     }
//
//     public String symbol() { return symbol; }
//     public long eventTime() { return eventTime; }
//     public List<OrderBookLevel> bids() { return bids; }
//     public List<OrderBookLevel> asks() { return asks; }
//
//     @Override
//     public boolean equals(Object o) {
//         if (this == o) return true;
//         if (!(o instanceof OrderBookSnapshot other)) return false;
//         return eventTime == other.eventTime
//             && java.util.Objects.equals(symbol, other.symbol)
//             && java.util.Objects.equals(bids, other.bids)
//             && java.util.Objects.equals(asks, other.asks);
//     }
//
//     @Override
//     public int hashCode() {
//         return java.util.Objects.hash(symbol, eventTime, bids, asks);
//     }
//
//     @Override
//     public String toString() {
//         return "OrderBookSnapshot[symbol=" + symbol
//             + ", eventTime=" + eventTime
//             + ", bids=" + bids
//             + ", asks=" + asks + "]";
//     }
// }