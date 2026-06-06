package com.example.futurespapertrading.paper;

// 주문 방향 — 살 거냐(BUY) 팔 거냐(SELL).
//
// enum = "미리 정한 값만 가질 수 있는 타입". side에 "buy", "Buy", "BYU" 같은 오타가 끼는 걸
//        컴파일 단계에서 막아준다 (그냥 String이면 런타임까지 못 잡는다).
//   예) String    side = "BYU";              // 오타인데 컴파일 통과 → 실행하다가(런타임) 터짐
//       OrderSide side = OrderSide.BYU;      // ❌ 컴파일 에러 — 그런 값 없음 → 코드 짜는 중에 바로 잡힘
//
// ── enum을 만들어 놓고, 왜 PaperOrder(엔티티)에는 enum이 아니라 String "BUY"로 저장하나? ──
//   한마디로: "지금은 DB에 넣기 쉬운 쪽으로 단순하게 가려고."
//
//   - DB(데이터베이스)는 글자·숫자 같은 단순한 값만 담는다. 자바의 OrderSide.BUY라는 "객체"가
//     뭔지는 모른다. 그래서 DB에는 그냥 글자 "BUY"를 넣는다.
//   - 글자 "BUY"를 DB에서 읽어와 enum으로 바꾸는 건 자동이다.
//     그런데 반대로 enum을 DB에 글자로 "저장"할 때는 자동이 아니라서, 변환 설정 코드를 더 깔아야 한다.
//   - 8단계 A는 기본 그릇만 만드는 단계라 그 추가 설정까진 안 한다 (나중에 필요하면 그때 붙임).
//     그래서 엔티티는 그냥 String "BUY"로 저장한다 (User가 email을 String으로 저장하는 것과 똑같음).
//
//   - 대신 enum은 "계산이 중요한 곳(엔진·컨트롤러)"에서 쓰고, 그 입구/출구에서만 글자↔enum을 바꾼다:
//       "BUY"(DB) ──OrderSide.valueOf("BUY")──▶ OrderSide.BUY (자바에서 계산) ──.name()──▶ "BUY"(DB로 다시 저장)
//     (Parser가 JSON의 글자 "67000.10"을 BigDecimal 숫자로 바꿔 쓰던 것과 같은 "입구에서 변환"과 같은 패턴)
public enum OrderSide {
    // BUY, SELL = enum 상수(enum constant) — 각각 그 자체로 OrderSide 타입의 인스턴스(객체) 1개다.
    //   (public static final 필드 = 보통 '상수'라 부르는 그것. 그중에서도 enum 본문에 선언한 상수라
    //    콕 집어 'enum 상수'라 한다. 단순 상수(static final int=100)와 달리 new로 만든 '객체인 상수'.)
    BUY,
    SELL
}

// ════════════════════════════════════════════════════════════════════════════
// 위 enum(BUY, SELL) 한 줄은 "압축형"이다. 실제로는 아래 두 클래스가 협력해 동작한다.
//   [자식] OrderSide      = 컴파일러(javac)가 자동 생성 — 내 enum 고유(BUY/SELL 인스턴스)
//   [부모] java.lang.Enum = JDK 제공 — 모든 enum이 공유하는 공통 기계(name/ordinal/==/valueOf)
//
// ※ 주의: 아래 코드는 진짜 소스를 그대로 옮긴 게 아니라, 이해를 위해 "핵심만 추려 단순화"한 것이다.
//   실제 javac 생성물·JDK 원본에는 방어 코드, 직렬화 처리, 내부 필드($VALUES 등)가 더 있다.
//   여기서는 "동작의 뼈대"만 보여준다. (정확한 원본은 IntelliJ에서 Enum을 Ctrl+B로 열어 확인)
//
// ── [자식] 컴파일러가 만들어주는 OrderSide (핵심만) ──────────────────────────────
// public final class OrderSide extends Enum<OrderSide> {     // Enum을 이미 상속 → 다른 클래스 상속 불가(인터페이스는 가능)
//
//     // BUY, SELL = 미리 1개씩 만들어 둔 인스턴스(public static final 싱글톤). "BUY"=name, 0=ordinal(선언순서)
//     public static final OrderSide BUY  = new OrderSide("BUY", 0);
//     public static final OrderSide SELL = new OrderSide("SELL", 1);
//
//     private OrderSide(String name, int ordinal) { super(name, ordinal); }  // private → 외부 new 불가 → 값 고정
//
//     // values() : 이 enum의 "모든 상수"를 선언 순서대로 배열에 담아 돌려준다.
//     //            → [BUY, SELL]. 전체를 훑을 때 쓴다. 예: for (OrderSide s : OrderSide.values()) {...}
//     public static OrderSide[] values()          { ... }
//
//     // valueOf(n) : 문자열 이름(n)과 일치하는 상수를 찾아 돌려준다. (String → enum 변환기)
//     //            → "BUY"면 OrderSide.BUY 반환, 없는 이름이면 IllegalArgumentException 던짐.
//     //              우리 프로젝트에선 DB의 side("BUY") → OrderSide.BUY로 바꿀 때 쓴다.
//     public static OrderSide   valueOf(String n) { ... }
// }
//
// ── [부모] java.lang.Enum (JDK 제공, 모든 enum의 부모) ───────────────────────────
// public abstract class Enum<E extends Enum<E>> implements Comparable<E>, Serializable {
//
//     private final String name;     // 상수 이름        → name()
//     private final int    ordinal;  // 선언 순서(0,1…)  → ordinal()
//
//     protected Enum(String name, int ordinal) {        // protected → enum 서브클래스만 호출(자식의 super(...)가 이것)
//         this.name = name; this.ordinal = ordinal;
//     }
//     public final String name()    { return name; }
//     public final int    ordinal() { return ordinal; }
//
//     public final boolean equals(Object o) { return this == o; }    // 싱글톤이라 동일성(==)이 곧 값 비교 → switch/== 안전
//     public final int     compareTo(E o)   { return this.ordinal - o.ordinal; }    // 정렬: 내 순서(this) − 상대 순서(o), 선언 순서 기준
//                                                                                    //   (실제 JDK는 다른 enum 타입 비교를 막는 방어 캐스팅이 더 있지만, 하는 일은 이 한 줄)
//
//     public static <T extends Enum<T>> T valueOf(Class<T> cls, String name) { ... }   // 자식 valueOf가 이걸 호출
// }
//   ※ <E extends Enum<E>> = 자기참조 제네릭. OrderSide가 자기 자신을 E로 끼워(extends Enum<OrderSide>)
//      compareTo가 "OrderSide끼리만" 되도록 막는 장치 (OrderSide vs OrderStatus 비교는 컴파일 에러).
// ════════════════════════════════════════════════════════════════════════════
