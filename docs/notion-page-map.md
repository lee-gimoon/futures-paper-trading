# Notion 페이지 매핑

작성일: 2026-05-13  
목적: 로컬 Markdown 문서와 Notion 페이지의 연결 관계를 고정해서, 문서 동기화 때마다 Notion 검색을 반복하지 않게 한다.

이 문서는 `futures-paper-trading` 프로젝트 문서를 Notion에 동기화할 때 가장 먼저 확인하는 기준표다.

---

## 동기화 원칙

1. 로컬 md 파일을 Notion에 반영할 때는 먼저 이 파일에서 매핑을 찾는다.
2. 매핑이 있으면 Notion 검색을 하지 말고, 표에 적힌 Notion URL 또는 page id로 바로 fetch/update 한다.
3. 매핑이 없는 새 md 파일이 생기면 Notion에서 한 번만 페이지를 찾거나 만들고, 이 파일에 매핑을 추가한다.
4. Notion 페이지 제목이 바뀌거나 이동되면 이 파일의 제목, URL, page id를 함께 갱신한다.
5. 로컬 md 내용이 기준이고, Notion은 동기화 대상이다.

---

## 상위 페이지

| 구분 | Notion 제목 | Notion page id | Notion URL |
|---|---|---|---|
| 프로젝트 문서 묶음 | futures paper trading | `35e81af1-4757-80f6-91c1-dea66bc744b4` | https://www.notion.so/35e81af1475780f691c1dea66bc744b4 |

---

## 로컬 md와 Notion 페이지 매핑

| 로컬 md 파일 | Notion 페이지 제목 | Notion page id | Notion URL | 동기화 기준 |
|---|---|---|---|---|
| `docs/implementation-roadmap.md` | futures-paper-trading 구현 로드맵 | `35e81af1-4757-8140-868e-fcb349e8a30e` | https://www.notion.so/35e81af147578140868efcb349e8a30e | 로컬 md가 원본 |
| `docs/steps/00-project-skeleton.md` | 00단계. 프로젝트 뼈대 만들기 | `35e81af1-4757-81ab-9148-e1d9ea3ff0cc` | https://www.notion.so/35e81af1475781ab9148e1d9ea3ff0cc | 로컬 md가 원본 |
| `docs/steps/01-binance-market-data.md` | 01단계. Binance 실시간 시세 수신 | `35f81af1-4757-8123-bd1f-cf360a712f93` | https://www.notion.so/35f81af147578123bd1fcf360a712f93 | 로컬 md가 원본 |
| `docs/notion-page-map.md` | Notion 페이지 매핑 | `35e81af1-4757-81e3-bca8-e9b8581fff93` | https://www.notion.so/35e81af1475781e3bca8e9b8581fff93 | 로컬 md가 원본 |
| `AGENTS.md` | AGENTS 작업 가이드 | `35e81af1-4757-813a-a9d5-eba55fc5bf44` | https://www.notion.so/35e81af14757813aa9d5eba55fc5bf44 | 로컬 md가 원본 |
| `HELP.md` | futures-paper-trading 도움말 | `35e81af1-4757-8196-9712-e54335e67d45` | https://www.notion.so/35e81af1475781969712e54335e67d45 | 로컬 md가 원본 |
| `N/A (Notion 하위 보충 페이지)` | 00-보충. PaperTradingProperties 코드 설명 | `35f81af1-4757-81b5-ad26-e6320d9f51c2` | https://www.notion.so/35f81af1475781b5ad26e6320d9f51c2 | 00단계 페이지 하위 문서 |
| `N/A (Notion 하위 보충 페이지)` | 00-보충. application.yaml 설정 파일 설명 | `35f81af1-4757-8144-ad85-cf90a1fb6cc3` | https://www.notion.so/35f81af147578144ad85cf90a1fb6cc3 | 00단계 페이지 하위 문서 |
| `N/A (Notion 하위 보충 페이지)` | 00-보충. HealthController 코드 설명 | `35f81af1-4757-8175-8ef0-d86d4276f340` | https://www.notion.so/35f81af1475781758ef0d86d4276f340 | 00단계 페이지 하위 문서 |
| `N/A (Notion 하위 보충 페이지)` | 00-보충. record 직관 설명 | `35f81af1-4757-8122-9d04-e6532ece1ac4` | https://www.notion.so/35f81af1475781229d04e6532ece1ac4 | 00단계 페이지 하위 문서 |
| `N/A (Notion 하위 보충 페이지)` | 01-보충. application.yaml Binance 설정 설명 | `35f81af1-4757-814e-b37d-f6dfb1c95557` | https://www.notion.so/35f81af14757814eb37df6dfb1c95557 | 01단계 페이지 하위 문서 |
| `N/A (Notion 하위 보충 페이지)` | 01-보충. BinanceProperties 코드 설명 | `35f81af1-4757-8167-ac9b-ef22ace8a549` | https://www.notion.so/35f81af147578167ac9bef22ace8a549 | 01단계 페이지 하위 문서 |
| `N/A (Notion 하위 보충 페이지)` | 01-보충. BinanceMarketDataClient 코드 설명 | `35f81af1-4757-8190-9411-e0d3f8c88f17` | https://www.notion.so/35f81af1475781909411e0d3f8c88f17 | 01단계 페이지 하위 문서 |
| `N/A (Notion 하위 보충 페이지)` | 01-보충. BinanceMarketEventMapper 코드 설명 | `35f81af1-4757-8125-aa0e-cd1fb939cd69` | https://www.notion.so/35f81af147578125aa0ecd1fb939cd69 | 01단계 페이지 하위 문서 |
| `N/A (Notion 하위 보충 페이지)` | 01-보충. BinanceStreamDescriptor 코드 설명 | `35f81af1-4757-81b0-b7f9-dc5a5ba60163` | https://www.notion.so/35f81af1475781b0b7f9dc5a5ba60163 | 01단계 페이지 하위 문서 |
| `N/A (Notion 하위 보충 페이지)` | 01-보충. MarketEvent 코드 설명 | `35f81af1-4757-81da-b1e3-e18278064f1f` | https://www.notion.so/35f81af1475781dab1e3e18278064f1f | 01단계 페이지 하위 문서 |
| `N/A (Notion 하위 보충 페이지)` | 01-보충. BookTickerEvent 코드 설명 | `35f81af1-4757-81a6-a9f1-e36853a8381d` | https://www.notion.so/35f81af1475781a6a9f1e36853a8381d | 01단계 페이지 하위 문서 |
| `N/A (Notion 하위 보충 페이지)` | 01-보충. MarkPriceEvent 코드 설명 | `35f81af1-4757-819c-8d58-e275b04759a9` | https://www.notion.so/35f81af14757819c8d58e275b04759a9 | 01단계 페이지 하위 문서 |
| `N/A (Notion 하위 보충 페이지)` | 01-보충. AggTradeEvent 코드 설명 | `35f81af1-4757-8145-a696-ee2e3af0e8a1` | https://www.notion.so/35f81af147578145a696ee2e3af0e8a1 | 01단계 페이지 하위 문서 |
| `N/A (Notion 하위 보충 페이지)` | 01-보충. MarketDataStatus 코드 설명 | `35f81af1-4757-814b-9d7f-eeedb6f65610` | https://www.notion.so/35f81af14757814b9d7feeedb6f65610 | 01단계 페이지 하위 문서 |
| `N/A (Notion 하위 보충 페이지)` | 01-보충. MarketDataStatusController 코드 설명 | `35f81af1-4757-810b-8c34-c432f7ff5b7b` | https://www.notion.so/35f81af14757810b8c34c432f7ff5b7b | 01단계 페이지 하위 문서 |

---

## 아직 매핑되지 않은 md

현재 미매핑 md 없음.

새 문서를 만들면 아래 형식으로 위 표에 추가한다.

```text
| `docs/steps/01-binance-market-data.md` | 01단계. Binance 실시간 시세 수신 | `<page-id>` | `<notion-url>` | 로컬 md가 원본 |
```

---

## 작업 메모

- 2026-05-13: 기존 Notion 페이지 검색 결과를 기준으로 최초 매핑 작성.
- 2026-05-13: `docs/notion-page-map.md` 자체도 Notion 페이지로 만들고 매핑에 추가.
- 이후 Notion 동기화 작업에서는 이 문서를 먼저 보고, 매핑이 없을 때만 검색한다.
- 2026-05-13: `00단계. 프로젝트 뼈대 만들기` 하위에 `00-보충. PaperTradingProperties 코드 설명` 페이지 생성 후 매핑 추가.
- 2026-05-13: `00단계. 프로젝트 뼈대 만들기` 하위에 `00-보충. application.yaml 설정 파일 설명` 페이지 생성 후 매핑 추가.
- 2026-05-13: `00단계. 프로젝트 뼈대 만들기` 하위에 `00-보충. HealthController 코드 설명` 페이지 생성 후 매핑 추가.
- 2026-05-13: `00단계. 프로젝트 뼈대 만들기` 하위에 `00-보충. record 직관 설명` 페이지 생성 후 매핑 추가.
- 2026-05-13: `01단계. Binance 실시간 시세 수신` 페이지 생성 후 매핑 추가.
- 2026-05-13: `01단계. Binance 실시간 시세 수신` 하위에 `01-보충. application.yaml Binance 설정 설명` 페이지 생성 후 매핑 추가.
- 2026-05-13: `01단계. Binance 실시간 시세 수신` 하위에 `01-보충. BinanceProperties 코드 설명` 페이지 생성 후 매핑 추가.
- 2026-05-13: `01단계. Binance 실시간 시세 수신` 하위에 `01-보충. BinanceMarketDataClient 코드 설명` 페이지 생성 후 매핑 추가.
- 2026-05-13: `01단계. Binance 실시간 시세 수신` 하위에 `01-보충. BinanceMarketEventMapper 코드 설명` 페이지 생성 후 매핑 추가.
- 2026-05-13: `01단계. Binance 실시간 시세 수신` 하위에 `01-보충. BinanceStreamDescriptor 코드 설명` 페이지 생성 후 매핑 추가.
- 2026-05-13: `01단계. Binance 실시간 시세 수신` 하위에 `01-보충. MarketEvent 코드 설명` 페이지 생성 후 매핑 추가.
- 2026-05-13: `01단계. Binance 실시간 시세 수신` 하위에 `01-보충. BookTickerEvent 코드 설명` 페이지 생성 후 매핑 추가.
- 2026-05-13: `01단계. Binance 실시간 시세 수신` 하위에 `01-보충. MarkPriceEvent 코드 설명` 페이지 생성 후 매핑 추가.
- 2026-05-13: `01단계. Binance 실시간 시세 수신` 하위에 `01-보충. AggTradeEvent 코드 설명` 페이지 생성 후 매핑 추가.
- 2026-05-13: `01단계. Binance 실시간 시세 수신` 하위에 `01-보충. MarketDataStatus 코드 설명` 페이지 생성 후 매핑 추가.
- 2026-05-13: `01단계. Binance 실시간 시세 수신` 하위에 `01-보충. MarketDataStatusController 코드 설명` 페이지 생성 후 매핑 추가.
