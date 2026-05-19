package com.example.futurespapertrading.market;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

// Spring Boot 4은 Jackson 3을 사용하고, 패키지가 com.fasterxml.jackson.* → tools.jackson.*로 바뀌었다.
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// Binance Futures partial depth raw JSON 한 줄을 OrderBookSnapshot으로 변환한다.
// 왜 변환하는가 — raw JSON 그대로 쓰지 않고 굳이 자바 객체(OrderBookSnapshot)로 옮기는 이유:
//   1) raw JSON은 결국 한 덩어리 문자열. 매번 파싱·문자열 인덱싱하면서 쓰면 코드가 지저분해지고 매 호출마다 다시 파싱하는 낭비가 생긴다.
//      한 번 OrderBookSnapshot으로 만들어두면 그 뒤 코드는 snapshot.bids()처럼 자바 객체로만 다루면 된다.
//   2) 타입 안전성 — JSON의 "67000.10"은 문자열이라 컴파일러가 "이게 진짜 가격인지" 검사해주지 못한다.
//      BigDecimal/long/String으로 박아두면, 잘못된 타입을 쓰면 컴파일 단계에서 막힌다 (런타임 사고 → 컴파일 에러로 당겨짐).
//   3) 도메인 언어로 번역 — 거래소 API의 "depthUpdate", "b", "a", "E" 같은 약어는 우리 도메인 용어가 아니다.
//      "OrderBookSnapshot", "bids", "asks", "eventTime"으로 옮겨두면, 호출부 코드를 사람이 읽기 쉽다.
//   4) 격리(anti-corruption layer) — 바이낸스가 어느 날 필드명을 바꾸거나 다른 거래소(예: Bybit)도 붙이게 되면,
//      이 파서 한 곳만 고치면 끝. 나머지 코드는 OrderBookSnapshot이라는 우리만의 모양만 보고 살아서 영향을 받지 않는다.
// 매핑 전용 DTO 클래스는 아직 만들지 않는다. JsonNode 트리에서 필요한 필드(s, E, b, a)만 직접 꺼낸다.
@Component
public class OrderBookSnapshotParser {

	// ObjectMapper는 Jackson의 JSON 변환 엔진 — readTree(json)로 JSON 문자열을 JsonNode 트리로 풀어 .get("s") 식으로 필드를 꺼내려고 쓴다.
	// 이름의 의미: Object(자바 객체) + Mapper(서로 대응시켜 옮기는 도구) → "JSON ↔ 자바 객체를 서로 매핑(변환)해주는 도구"라는 뜻.
	// ObjectMapper는 spring-boot-starter-jackson이 자동 구성해주는 빈이라 생성자 주입으로 그대로 받는다.
	private final ObjectMapper objectMapper;

	public OrderBookSnapshotParser(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	// raw JSON 문자열을 받아 OrderBookSnapshot으로 만든다.
	// Jackson 3의 파싱 예외(JacksonException)는 RuntimeException 계열이라 throws를 따로 선언하지 않는다.
	public OrderBookSnapshot parse(String json) {
		JsonNode root = objectMapper.readTree(json);
		// Binance partial depth payload 필드명:
		//   "s" = symbol, "E" = event time(ms), "b" = bids, "a" = asks
		// asXxx() = "이 JsonNode 안에 든 값을 자바 Xxx 타입으로 꺼내달라". asString → String, asLong → long.
		String symbol = root.get("s").asString();
		long eventTime = root.get("E").asLong();
		List<OrderBookLevel> bids = readLevels(root.get("b"));
		List<OrderBookLevel> asks = readLevels(root.get("a"));
		return new OrderBookSnapshot(symbol, eventTime, bids, asks);
	}

	// 거래소가 준 원시 JSON 배열을 내 코드에서 안전하고 읽기 좋게 쓸 수 있는 호가 객체 리스트(도메인 객체)로 변환한다.
	private List<OrderBookLevel> readLevels(JsonNode arrayNode) {
		List<OrderBookLevel> levels = new ArrayList<>(arrayNode.size());
		for (JsonNode levelNode : arrayNode) {
			// 문자열을 곧장 BigDecimal 생성자에 넘긴다. double을 거치면 정밀도가 망가지므로 절대 거치지 않는다.
			BigDecimal price = new BigDecimal(levelNode.get(0).asString());
			BigDecimal quantity = new BigDecimal(levelNode.get(1).asString());
			levels.add(new OrderBookLevel(price, quantity));
		}
		return levels;
	}

	// 바이낸스가 실제로 보내는 raw JSON 한 줄은 아래 모양이다.
	// {
	//   "e": "depthUpdate",
	//   "E": 1715900000000,
	//   "s": "BTCUSDT",
	//   "b": [
	//     ["67000.10", "1.234"],
	//     ["67000.00", "0.500"],
	//     ["66999.80", "3.000"]
	//     // ... 최대 20개
	//   ],
	//   "a": [
	//     ["67000.50", "0.800"],
	//     ["67000.60", "1.200"]
	//     // ... 최대 20개
	//   ]
	// }
	// 위 JSON 모양 때문에 파서가 이렇게 짜인 이유:
	//
	//   1) 숫자가 "67000.10"처럼 문자열(따옴표 포함)로 온다 → asString() → new BigDecimal(...) 경로로만 받고 절대 double을 거치지 않는다.
	//      ─ 왜 거래소가 굳이 문자열로 보내는가:
	//        컴퓨터는 0.1 같은 십진 소수를 double(이진 부동소수점)로 저장할 때 정확히 못 담는다. (예: 0.1 + 0.2 == 0.30000000000000004)
	//        67000.10을 double로 받으면 어딘가에서 67000.09999...가 되어 있을 수 있고, 거래소 입장에서 이 1원 오차는 누군가의 손익이 된다.
	//        그래서 "마음대로 double로 해석하지 마라"는 뜻으로 따옴표를 붙여 문자열로 보낸다.
	//      ─ 자바에서 받는 두 경로:
	//          ❌ asDouble() → new BigDecimal(double): asDouble 단계에서 이미 망가진 값을 그대로 BigDecimal에 담는 꼴 → 의미 없음.
	//          ✅ asString() → new BigDecimal(String): 문자열을 한 글자씩 십진수로 읽어 그대로 저장 → 오차 0.
	//
	//   2) {"price":..., "quantity":...} 객체 배열이 아니라 위치 기반 2-튜플 배열 ["67000.10","1.234"]로 온다 → get(0)/get(1) 인덱스로 꺼낸다.
	//      ─ 왜 거래소가 굳이 배열로 보내는가: 트래픽 절약.
	//        호가창은 초당 수십~수백 번 갱신, 한 줄에 40레벨(bids 20 + asks 20). "price"/"quantity" 필드명이 매 레벨마다 반복되면
	//        그것만으로 한 줄당 수백 바이트가 새고, 하루치 쌓이면 어마어마하다. 그래서 필드명을 빼고 "0번=price, 1번=quantity"라는
	//        규칙을 API 문서로 약속하고 값만 배열로 보낸다.
	//      ─ 그게 코드에 미친 영향: 이름이 없으니 위치로만 꺼낼 수 있다 → get(0)=가격, get(1)=수량.
	//        만약 바이낸스가 순서를 ["quantity","price"]로 바꾸면 이 두 줄은 즉시 깨진다. 그래서 주석으로 약속을 명시해 둬야 한다.
	//
	// (필드 타입이 왜 List<OrderBookLevel>인지는 OrderBookSnapshot.java 참고.)
}
