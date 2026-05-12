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
| `docs/notion-page-map.md` | Notion 페이지 매핑 | `35e81af1-4757-81e3-bca8-e9b8581fff93` | https://www.notion.so/35e81af1475781e3bca8e9b8581fff93 | 로컬 md가 원본 |
| `AGENTS.md` | AGENTS 작업 가이드 | `35e81af1-4757-813a-a9d5-eba55fc5bf44` | https://www.notion.so/35e81af14757813aa9d5eba55fc5bf44 | 로컬 md가 원본 |
| `HELP.md` | futures-paper-trading 도움말 | `35e81af1-4757-8196-9712-e54335e67d45` | https://www.notion.so/35e81af1475781969712e54335e67d45 | 로컬 md가 원본 |

---

## 아직 매핑되지 않은 md

현재 없음.

새 문서를 만들면 아래 형식으로 위 표에 추가한다.

```text
| `docs/steps/01-binance-market-data.md` | 01단계. Binance 실시간 시세 수신 | `<page-id>` | `<notion-url>` | 로컬 md가 원본 |
```

---

## 작업 메모

- 2026-05-13: 기존 Notion 페이지 검색 결과를 기준으로 최초 매핑 작성.
- 2026-05-13: `docs/notion-page-map.md` 자체도 Notion 페이지로 만들고 매핑에 추가.
- 이후 Notion 동기화 작업에서는 이 문서를 먼저 보고, 매핑이 없을 때만 검색한다.
