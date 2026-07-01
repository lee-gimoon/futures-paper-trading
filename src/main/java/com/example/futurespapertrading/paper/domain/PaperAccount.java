package com.example.futurespapertrading.paper.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

// paper_accounts 테이블 한 줄 ↔ 자바 객체. "사용자 한 명의 모의 계좌"다. (User·PaperOrder와 같은 record+@Table 패턴)
//   저장하는 값은 시드 현금(cashBalance)과 레버리지(leverage)뿐 — 포지션·실현/미실현 PnL은 저장하지 않고
//   paper_fills에서 매번 계산한다(PositionCalculator). 그래서 이 엔티티는 체결이 일어나도 cashBalance는 안 바뀐다.
//   leverage = % 바·마진·청산가 계산의 기준 배수(사용자가 바꿀 수 있는 유일한 가변 값).
//   created_at/updated_at은 DB가 DEFAULT now()로 채우므로 record에 두지 않는다.
@Table("paper_accounts")
public record PaperAccount(
        @Id Long id,
        @Column("user_id") Long userId,
        @Column("cash_balance") BigDecimal cashBalance,
        int leverage
) {
}
