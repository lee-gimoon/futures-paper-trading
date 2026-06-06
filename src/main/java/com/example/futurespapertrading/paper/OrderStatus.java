package com.example.futurespapertrading.paper;

// 주문의 생애주기 상태.
//   NEW      = 막 만들어져 아직 체결 판정 전 (보통 잠깐 거쳐가는 임시 상태)
//   OPEN     = 지정가가 아직 안 닿아 호가창에 걸려 대기 중 → 호가가 닿는지 계속 재평가하는 대상 (G단계 matcher)
//   FILLED   = 체결 완료 (종료 상태)
//   CANCELED = 사용자가 취소 (종료 상태)
//   REJECTED = 체결 불가로 거부 (예: 호가 수신 전 시장가 주문) (종료 상태)
//
// (DB에는 String으로 저장한다 — 이유는 OrderSide 참고.)
public enum OrderStatus {
    NEW,
    OPEN,
    FILLED,
    CANCELED,
    REJECTED
}
