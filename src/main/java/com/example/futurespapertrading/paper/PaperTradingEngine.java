package com.example.futurespapertrading.paper;

import org.springframework.stereotype.Service; // 이 클래스를 스프링 빈으로 등록(컨트롤러가 주입받을 수 있게)

import com.example.futurespapertrading.market.OrderBookLevel;
import com.example.futurespapertrading.market.OrderBookSnapshot;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// 이 클래스는 '체결 계산기' 역할을 한다. 주문 1건과 지금 호가창(snapshot)을 받아,
// 그 주문이 지금 호가에 대고 "어떻게 체결되는지"를 계산해 체결 내역(fill 목록)으로 돌려준다.
//   넘겨준 입력만 보고 답을 내며, DB·인터넷 같은 바깥 것은 일절 안 만진다 (이런 함수를 '순수 함수'라 부른다).
//   → 바깥과 안 엮이니 주문·호가만 만들어 넣고 결과만 확인하면 되는 단위 테스트가 아주 쉽다. (B단계 ★)
//
// ── 무엇을 돌려주나: '몇 개 남았는지'가 아니라 'fill 목록' ──
//   예) 100에 10개 매수인데 호가가 8개어치만 닿으면 → fill 8개어치 목록만 돌려준다 (남은 2개는 손도 안 댐).
//   "10개 중 8개 체결 → 2개 남음"이라는 계산도, 그 남은 2개를 어떻게 할지 정하는 것도 엔진이 아니라 호출부의 몫이다.
//
// ── 여기서 '안' 하는 일 (호출부 = 컨트롤러/matcher, C·G단계의 몫) ──
//   · 주문 상태 판정(FILLED / OPEN / REJECTED)    — fill 목록과 주문 수량을 비교해 호출부가 정한다.
//   · 남은 수량을 OPEN으로 호가창에 걸어 대기       — 별도 기능이며, 아직 구현 안 됨(C·G단계 예정).
//   · DB 저장                                       — 호출부가 한다.
//   엔진은 그냥 "이렇게 체결됩니다" 하고 fill 목록만 건넬 뿐이다.
//
// ── 한 줄 정리: 엔진이란? ──
//   주문 1건(PaperOrder)과 지금 호가창(OrderBookSnapshot)을 인자로 받아, 실거래소의 매칭 규칙대로
//   "어떻게 체결되는지"를 계산해 체결 내역(List<PaperFill>)을 return하는 순수 계산기다.
//   DB·웹 같은 바깥은 일절 안 만지고 입력만 보고 답을 내며, 이 체결 계산이 곧 모의투자의 핵심 도메인 로직이다.
//
// @Service = 스프링이 이 클래스를 빈으로 1개 만들어 관리 → 컨트롤러가 생성자 주입으로 받아 쓴다.
//   (B단계엔 테스트가 new로 직접 만들어 써서 불필요했지만, C단계에서 PaperOrderController가 주입받으면서 필요해졌다.
//    상태 없는 순수 계산기라 싱글톤 1개를 공유해도 안전하다. 테스트는 여전히 new PaperTradingEngine()으로 직접 만들어 쓴다.)
@Service
public class PaperTradingEngine {

    // 8단계 MVP라 수수료는 0. private static final = 이 클래스 전용 상수(클래스에 딱 하나, 재할당 금지).
    //   BigDecimal.ZERO 는 BigDecimal이 미리 만들어둔 '0' 상수다 (enum 아님 — 그냥 public static final 필드.
    //    BigDecimal은 0·1·3.14… 무한한 값을 가지니 enum이 될 수 없고, ZERO/ONE/TEN은 자주 쓰는 값의 단축키일 뿐).
    private static final BigDecimal FEE_ZERO = BigDecimal.ZERO;

    // 주문을 현재 snapshot에 대고 체결해 본다.
    //   반환 = 체결된 fill 목록. 호가 레벨을 가격순으로 긁으며 레벨마다 1건씩 → 주문 1 : fill N.
    //   empty 목록의 의미(둘 다 "체결 0건"):
    //     · 시장가인데 먹을 호가가 비어 있음        → 호출부가 REJECTED로 판단
    //     · 지정가인데 한도가 best에 안 닿음          → 호출부가 OPEN으로 등록
    public List<PaperFill> tryFill(PaperOrder order, OrderBookSnapshot snapshot) {
        // OrderSide.valueOf(order.side())는 "BUY"라는 글자에 맞는 이미 존재하는 OrderSide.BUY 싱글톤 인스턴스를 그대로 반환한다.
        OrderSide side = OrderSide.valueOf(order.side());   // 주문에 문자열("BUY"/"SELL")로 저장된 side를 OrderSide enum으로 변환.
        OrderType type = OrderType.valueOf(order.type());   // 주문의 type 문자열("MARKET"/"LIMIT")을 OrderType enum으로 변환.

        // 어느 쪽 호가를 먹나:
        //   BUY  → asks(매도호가)를 싼 가격부터 (오름차순)
        //   SELL → bids(매수호가)를 비싼 가격부터 (내림차순)
        List<OrderBookLevel> levels = (side == OrderSide.BUY)   // 삼항연산자 ?: — 조건(BUY인가?)이 참이면 ? 뒤, 거짓이면 : 뒤를 고른다.
                ? sortedAscending(snapshot.asks())              // BUY 일 때: 매도호가(asks)를 싼 가격부터(오름차순) 정렬해 긁을 대상으로 삼는다.
                : sortedDescending(snapshot.bids());            // SELL 일 때: 매수호가(bids)를 비싼 가격부터(내림차순) 정렬해 긁을 대상으로 삼는다.

        List<PaperFill> fills = new ArrayList<>();      // 체결 결과(fill)를 담을 빈 리스트. 레벨을 긁을 때마다 하나씩 add.
        // remaining = "앞으로 더 채워야 할 수량". 시작엔 아직 0개 체결이라 주문 전량 = order.quantity()로 출발하고,
        //   아래 루프에서 체결할 때마다 remaining.subtract(take)로 깎여 0이 되면 끝난다. (그래서 이름이 quantity가 아니라 remaining)
        BigDecimal remaining = order.quantity();

        for (OrderBookLevel level : levels) {
            // signum() = 부호 판별. 값이 음수면 -1, 0이면 0, 양수면 1을 돌려준다 (BigDecimal의 메서드).
            //   remaining.signum() <= 0 → 남은 수량이 0 이하 = 다 채웠다는 뜻이라 멈춘다.
            if (remaining.signum() <= 0) break;          // 다 채웠으면 멈춤

            // 지정가 가격 한도: 한도를 벗어난 레벨이 나오는 순간 멈춘다(가격순이라 그 뒤는 다 벗어남).
            //   BUY  : level.price > limit → 더 비싸짐 → 멈춤
            //   SELL : level.price < limit → 더 싸짐   → 멈춤
            //   limit과 '딱 같은' 가격은 체결된다(로드맵 ≤/≥ 경계 포함).
            if (type == OrderType.LIMIT) {                  // 지정가일 때만 한도 검사 (시장가는 한도가 없어 이 블록을 통째로 건너뜀).
                BigDecimal limit = order.limitPrice();      // 주문에 지정한 한도 가격(= 허용하는 최악의 가격선).
                // compareTo: a.compareTo(b) → a가 크면 양수(>0), 같으면 0, 작으면 음수(<0). (BigDecimal은 >,< 연산자를 못 써서 이걸 씀)
                if (side == OrderSide.BUY && level.price().compareTo(limit) > 0) break;   // BUY: 호가가 한도보다 비싸면(price>limit) 멈춤.
                if (side == OrderSide.SELL && level.price().compareTo(limit) < 0) break;  // SELL: 호가가 한도보다 싸면(price<limit) 멈춤.
            }

            // BigDecimal의 min(other) = '나(remaining)와 인자(level.quantity()) 두 값 중 작은 쪽'을 돌려주는 메서드다. (Math.min(a,b)의 BigDecimal판)
            //   예) remaining=10, level.quantity()=3 → 3 반환.   remaining=2, level.quantity()=5 → 2 반환.
            BigDecimal take = remaining.min(level.quantity());   // 남은 양과 이 호가 물량 중 작은 쪽 = 이번 레벨에서 먹을 수량

            // 이 레벨에서 체결된 만큼을 fill 1건으로 만들어 결과 목록에 추가.
            fills.add(new PaperFill(
                    null,                // id — 아직 없음. DB에 저장될 때 자동 부여된다 (엔진은 DB를 모름)
                    order.id(),          // 어느 주문의 체결인지 (이 fill의 부모 주문 id)
                    order.accountId(),   // 어느 계정의 체결인지 — 9단계(계정) 전까지는 null
                    order.symbol(),      // 종목 (예: BTCUSDT) — 주문 것을 그대로
                    order.side(),        // 방향(BUY/SELL) — 주문 것을 그대로 (문자열)
                    level.price(),       // 체결가 = 이 호가 레벨의 가격 (레벨마다 달라 fill이 여러 건으로 쪼개진다)
                    take,                // 체결 수량 = 위에서 구한 take
                    FEE_ZERO             // 수수료 — 8단계 MVP라 0
            ));

            // BigDecimal의 subtract(other) = '나(remaining) - 인자(take)' 결과를 돌려주는 메서드다. (- 연산자의 BigDecimal판)
            //   주의: BigDecimal은 못 바뀌는 값이라 remaining 자체가 깎이는 게 아니라, 새 값이 나온다 → 다시 remaining에 담아야 한다.
            //   (즉 BigDecimal은 int 같은 원시값이 아니라 heap에 사는 '객체'다. remaining은 그 객체를 가리키는 참조이고, 옛 객체는 GC가 청소.)
            remaining = remaining.subtract(take);   // 남은 수량에서 이번에 먹은 만큼(take) 차감 → 0이 되면 다음 회차에서 멈춤(루프 위 break)
        }

        return fills;
    }

    // "정렬 가정 안 함": 받은 순서에 기대지 않고 가격 기준으로 직접 정렬해 best부터 긁는다.
    //   가격 오름차순(싼 것부터)으로 정렬한 새 리스트를 돌려준다. (BUY가 asks를 긁을 때 씀)
    private static List<OrderBookLevel> sortedAscending(List<OrderBookLevel> levels) {
        return levels.stream()                                       // 리스트 → 스트림(처리 통로)
                // Comparator.comparing(price) = "각 레벨의 price를 키로 비교"하는 정렬 기준. sorted는 이 기준이 있어야 정렬함.
                .sorted(Comparator.comparing(OrderBookLevel::price)) // price 오름차순으로 정렬 (정렬된 새 Stream 반환)
                .toList();                                           // 다시 List로 모아 반환 (불변 List)
    }

    // 위와 같되 정렬만 반대 — 가격 내림차순(비싼 것부터). (SELL이 bids를 긁을 때 씀)
    private static List<OrderBookLevel> sortedDescending(List<OrderBookLevel> levels) {
        return levels.stream()
                .sorted(Comparator.comparing(OrderBookLevel::price).reversed())  // price 비교 기준을 .reversed()로 뒤집어 내림차순.
                .toList();
    }
}

// ── stream() → sorted() → toList() 가 메모리에서 흐르는 그림 ──
//   원본 리스트 levels = [ Level(101.0,3), Level(100.5,2), Level(101.5,5) ]   ← 그대로 안 변함
//           │
//           │ .stream()   ← 상자에 벨트 연결 (새 통로 생성, 리스트는 그대로)
//           ▼
//      [컨베이어 벨트]  ← "모양" 없음(자료구조 아님). 켜지면 한 개씩 흐를 준비만 됨
//           │ .sorted(price)   ← 흐르는 도중 price 순으로 줄세우는 station
//           ▼
//           │ .toList()   ← ★ 방아쇠(종단 연산)! 여기서 비로소 벨트가 켜져 흐르고, 흐른 걸 그릇에 담는다.
//           ▼                  (이게 없으면 벨트는 멈춘 채라 정렬도 실행 안 됨 + 반환 타입도 Stream이라 List 자리에 못 들어가 컴파일 에러)
//   새 리스트 = [ Level(100.5,2), Level(101.0,3), Level(101.5,5) ]   ← 정렬된 '새' 리스트 (원본과 별개)
