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
public final class OrderBookQuotes {   // final = 이 클래스 상속(extends) 금지 — 확장할 게 없는 유틸이라 막음

    // ── 유틸리티(utility) 클래스란? ──
    //   상태(필드) 없이 static 메서드/상수만 모아둔 '도구 모음집'. 객체로 만들어 쓰는 게 아니라
    //   클래스 이름으로 바로 호출한다(예: OrderBookQuotes.bestBid(snapshot)). 인스턴스가 가질
    //   값이 없으니 new 로 만들 이유가 없다.
    // 이 클래스는 객체로 만들어 쓰는 게 아니라 static 모음집이니, 아예 인스턴스 생성 자체를
    // 컴파일 단계에서 차단한다. (private 생성자를 직접 두면 자바가 public 기본 생성자를
    // 자동으로 만들지 않고, private 이라 외부의 new 호출은 컴파일 에러가 된다.)
    private OrderBookQuotes() {}

    // ── stream()/map()/max()/orElse() 는 왜 import 없이 쓸까? ──
    //   import는 "허가증"이 아니라 "이름 줄임말"이다.
    //   흔한 오해가 "import해야 그 클래스를 쓸 수 있다(사용 허가)"인데 사실이 아니다.
    //   클래스는 import하든 안 하든 이미 다 쓸 수 있는 상태다(classpath에 있으니까).
    //   import가 하는 일은 딱 하나 — 내가 소스에 짧은 이름으로 적을 수 있게 해주는 줄임말 등록이다.
    //   (증거: import를 하나도 안 해도 풀네임으로 적으면 멀쩡히 돌아간다. 예: java.util.List<String> list = new java.util.ArrayList<>();)
    //   → 그래서 stream()/map()/max()/orElse() 처럼 '객체에 점 찍어 부르는' 메서드는 타입 이름을 소스에 적을 일이 없어 import와 무관하다.
    //   (반면 Comparator.naturalOrder()는 'Comparator'라는 타입 이름을 직접 적으므로 import java.util.Comparator 가 필요하다.)
    public static BigDecimal bestBid(OrderBookSnapshot snapshot) {
        return snapshot.bids().stream()           // stream(): bids 리스트를 한 개씩 흘려보내는 처리 통로로 바꾼다.
                //   "타입이름::메서드이름" 은 '흘러온 그 원소에 이 메서드를 호출하라'로 읽는다.
                //   즉 .map(level -> level.price()) 람다와 똑같다 (받은 원소에 .price()만 부르므로 자바가 짧게 쓰게 해준 형태).
                .map(OrderBookLevel::price)       // map(): 각 호가레벨을 그 안의 price(BigDecimal) 값으로 변환한다.
                // max/min은 비교 규칙이 있어야 동작한다 — 두 값 중 뭐가 앞인지 판단할 잣대가 필요해서다.
                //   Comparator.naturalOrder() 는 "그 타입의 기본 순서(숫자면 크기 순, 글자면 사전 순)를 그대로 비교 기준으로 삼아라"라는 지정이다.
                //   여기 흐르는 값은 BigDecimal(가격)이라 기본 순서 = 숫자 크기 순 → max가 가장 큰 가격(최고가)을 고른다.
                .max(Comparator.naturalOrder())   // max(): 변환된 가격들 중 최댓값을 골라 Optional로 감싸 돌려준다.
                .orElse(null);                    // orElse(): Optional에 값이 있으면 그 값을, 비었으면 null을 반환한다.
    }

    public static BigDecimal bestAsk(OrderBookSnapshot snapshot) {
        return snapshot.asks().stream()
                .map(OrderBookLevel::price)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }
}

// ── 진짜 호가값으로 따라가 보는 bestBid 흐름 (예시) ──
//   bids = [ Level(price=100.5, qty=2),  Level(price=101.0, qty=5),  Level(price=99.5, qty=1) ] 라고 하자.
//
//   snapshot.bids()      →  [ Level(100.5,2), Level(101.0,5), Level(99.5,1) ]   // 호가 3개가 든 리스트 그대로
//   .stream()            →  100.5짜리 · 101.0짜리 · 99.5짜리 호가를 하나씩 흘려보냄
//   .map(..::price)      →  100.5,  101.0,  99.5                                 // 수량(qty)은 버리고 가격만 남음
//   .max(naturalOrder()) →  Optional[101.0]                                      // 그중 제일 큰 값을 '상자'에 담음
//   .orElse(null)        →  101.0                                                // 상자를 풀어 꺼낸 값 = bestBid 반환값
//
//   bestAsk 는 똑같이 흐르되 .min() 이라 가장 '작은' 값을 고른다 → 위 값이 asks였다면 99.5.
//
//   만약 bids 가 비어 있으면 [] :
//   .stream() 빈 흐름 → .map() 빈 흐름 → .max() Optional.empty() (담을 값 없음) → .orElse(null) = null

// ── 이 max()는 어느 클래스의 max인가? = java.util.stream.Stream 의 max(Comparator) ──
//   (Math.max(a,b) 처럼 값 두 개를 받는 max가 아니다. 앞 단계 .map()이 Stream<BigDecimal>을 돌려줬으니,
//    그 Stream에 점 찍어 부르는 Stream.max 이다. 그래서 인자도 두 값이 아니라 '비교 기준' Comparator 하나다.)
//
//   ── 흐르는 값인데 어떻게 최댓값을 뽑나? = "지금까지의 1등(챔피언)" 하나만 기억하며 갱신한다 ──
//     max는 모든 값을 한꺼번에 모아두고 고르는 게 아니다. 변수 하나에 '현재 챔피언'만 들고,
//     흘러오는 값마다 "지금 챔피언보다 크냐?"만 비교해서 크면 갈아치운다. (토너먼트의 현재 1등 자리)
//
//     예) 100.5 → 101.0 → 99.5 가 차례로 흘러올 때:
//       시작:          챔피언 = (없음)
//       100.5 흘러옴 → 비교할 챔피언이 없음        → 챔피언 = 100.5
//       101.0 흘러옴 → 101.0 > 100.5 (더 큼)      → 챔피언 = 101.0  (갈아치움)
//        99.5 흘러옴 →  99.5 < 101.0 (작음)       → 챔피언 = 101.0  (그대로)
//       흐름 끝!     → 마지막 챔피언 101.0 을 Optional로 감싸 반환
//
//     → 그래서 값을 한 개씩만 봐도(=동시에 다 들고 있지 않아도) 최댓값이 나온다. 메모리도 값 하나 분량뿐.
//
//   ── 한 가지 더: 흐름을 '실제로' 일으키는 게 바로 max다 (종단 연산) ──
//     stream()/map()까지는 "이렇게 처리할 거야" 하고 파이프라인만 깔아둘 뿐 아무것도 안 흐른다(게으름).
//     max 같은 종단(terminal) 연산이 호출되는 순간 비로소 값들을 빨아들여 하나씩 통과시키며 챔피언을 갱신한다.
//     (즉 max는 '이미 흘러가 버린 값 중 고르는' 게 아니라, 자기가 값을 끌어와 통과시키며 추려내는 단계다.)

// ── 참고: Stream 과 WebFlux(Flux/Mono) 비교 ──
//   둘은 출신도 용도도 다른 '독립된' 도구다. 헷갈리기 쉬운 건 딱 하나의 공통점 때문인데, 그것부터 짚고 차이로 간다.
//
//   ▷ 닮은 점 (이것 하나뿐) : 둘 다 '게으르다(lazy)' — 방아쇠를 당기기 전엔 아무 일도 안 한다.
//       Stream:  list.stream().map(..).filter(..)  → 여기까진 설정만,  .max(..)  가 방아쇠 → 그때부터 값이 흐름
//       Flux:    flux.map(..).filter(..)           → 여기까진 설정만,  .subscribe(..) 가 방아쇠 → 그때부터 값이 흐름
//       → 그래서 'max() ↔ subscribe()', '중간 연산 ↔ Flux 연산자' 로 대응된다. (max는 subscribe의 축소판 사촌 격)
//
//   ▷ 다른 점 (본질은 여기서 갈린다)
//       구분        | java.util.stream.Stream        | Reactor 의 Flux / Mono (WebFlux)
//       ------------|--------------------------------|------------------------------------------
//       소속        | JDK 표준 (자바 8+)             | 외부 라이브러리 (Project Reactor)
//       데이터      | 이미 다 가진 고정 데이터       | 시간차로 도착하는 값 (웹소켓·네트워크 등)
//       방식        | 동기·당김(pull), 그 자리서 완료| 비동기·밀어줌(push)
//       시간 축     | 없음 ('다음 값' 기다림 개념 X) | 있음 (0~무한 개가 시간 두고 옴)
//       신호        | 그냥 값이 흐르다 끝            | onNext/onError/onComplete 신호 + 백프레셔(속도 조절)
//       재사용      | 한 번 쓰면 소모 (종단 1회)     | (cold면) 구독할 때마다 처음부터 다시 흐름
//
//   ▷ 한 줄 요약
//       Stream = '이미 가진 데이터를 동기로 한 번 훑는' 도구.
//       Flux   = '시간차로 오는 데이터를 비동기·신호 기반으로 다루는' 도구.
//   여기 bids().stream() 은 이미 받아둔 호가 리스트를 그 자리서 가공하는 것이라, WebFlux 와는 무관하다.

// ── 고정 리스트인데 왜 for 대신 stream? (속도 아님, 사람이 읽고 고치기 위함) ──
//   "가격 뽑아 → 최댓값 → 없으면 null" 처럼 절차(how)가 아닌 하려는 일(what)을 그대로 적어 의도가 한눈에 보인다.
//   max/Optional 이 비교 방향·빈 케이스 실수를 막아주고, .filter(..) 한 줄로 단계 추가/삭제도 쉽다.
