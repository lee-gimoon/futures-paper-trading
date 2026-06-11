# -*- coding: utf-8 -*-
"""
Binance 실시간 데이터 파이프라인 — 백엔드 포트폴리오 PDF 생성 스크립트.
실행:  python make_binance_stream_pdf.py
출력:  binance-stream-portfolio.pdf (이 스크립트와 같은 폴더)
"""
import os

from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.lib.colors import HexColor
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.styles import ParagraphStyle
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate, PageTemplate, Frame, Paragraph, Spacer, PageBreak,
    Table, TableStyle, Flowable, KeepTogether,
)

# ---------------------------------------------------------------- 폰트
FONT_DIR = r"C:\Windows\Fonts"
pdfmetrics.registerFont(TTFont("Malgun", os.path.join(FONT_DIR, "malgun.ttf")))
pdfmetrics.registerFont(TTFont("MalgunBold", os.path.join(FONT_DIR, "malgunbd.ttf")))
pdfmetrics.registerFont(TTFont("Consolas", os.path.join(FONT_DIR, "consola.ttf")))
pdfmetrics.registerFontFamily(
    "Malgun", normal="Malgun", bold="MalgunBold", italic="Malgun", boldItalic="MalgunBold"
)

# ---------------------------------------------------------------- 색
INK = HexColor("#0f172a")
MUT = HexColor("#64748b")
RULE = HexColor("#e6e3dc")
CANVAS = HexColor("#fbfbf8")

FLUX = HexColor("#1d4ed8");    FLUX_BG = HexColor("#dbeafe")
SINK = HexColor("#c2410c");    SINK_BG = HexColor("#fed7aa");  SINK_SOFT = HexColor("#fff1e3")
AR   = HexColor("#a16207");    AR_BG = HexColor("#fef3c7")
EXT  = HexColor("#15803d");    EXT_BG = HexColor("#dcfce7")
BRW  = HexColor("#7e22ce");    BRW_BG = HexColor("#f3e8ff")
NTY  = HexColor("#475569");    NTY_BG = HexColor("#e2e8f0")

# ---------------------------------------------------------------- 스타일
def st(name, **kw):
    base = dict(fontName="Malgun", fontSize=9.5, leading=15, textColor=INK,
                alignment=TA_LEFT, wordWrap="CJK")
    base.update(kw)
    return ParagraphStyle(name, **base)

S_TITLE    = st("title", fontName="MalgunBold", fontSize=21, leading=29)
S_SUB      = st("sub", fontSize=11, leading=17, textColor=MUT)
S_H1       = st("h1", fontName="MalgunBold", fontSize=14.5, leading=20,
                spaceBefore=4, spaceAfter=8)
S_H2       = st("h2", fontName="MalgunBold", fontSize=11, leading=16, spaceBefore=6,
                spaceAfter=3)
S_BODY     = st("body", spaceAfter=5)
S_BODY_MUT = st("bodymut", textColor=MUT, fontSize=9, leading=14)
S_LABEL    = st("label", fontName="MalgunBold", fontSize=9.5, leading=15)
S_CAPTION  = st("caption", fontSize=8.5, leading=12.5, textColor=MUT)
S_KICKER   = st("kicker", fontName="MalgunBold", fontSize=9.5, leading=13,
                textColor=SINK)

CODE = '<font face="Consolas" size="8.7" color="#334155">%s</font>'


def code(s):
    return CODE % s


def bullet(text, style=S_BODY):
    return Paragraph(f"•&nbsp;&nbsp;{text}", style)


# ---------------------------------------------------------------- 아키텍처 다이어그램
class ArchDiagram(Flowable):
    def __init__(self, width=495, height=472):
        super().__init__()
        self.width, self.height = width, height

    # top 기준 좌표(위에서 아래로)를 reportlab y 좌표로 변환해 박스를 그린다.
    def _box(self, x, top, w, h, fill, stroke, title, sub=None, mono_sub=True,
             title_size=8.8, sub_size=7.6):
        c = self.canv
        y = self.height - top - h
        c.setFillColor(fill); c.setStrokeColor(stroke); c.setLineWidth(1.1)
        c.roundRect(x, y, w, h, 5, fill=1, stroke=1)
        c.setFillColor(INK)
        if sub:
            c.setFont("MalgunBold", title_size)
            c.drawCentredString(x + w / 2, y + h - 13, title)
            c.setFont("Consolas" if mono_sub else "Malgun", sub_size)
            c.setFillColor(MUT)
            c.drawCentredString(x + w / 2, y + 6, sub)
        else:
            c.setFont("MalgunBold", title_size)
            c.drawCentredString(x + w / 2, y + h / 2 - 3, title)

    def _arrow(self, x, top1, top2, label=None, label_dx=7):
        c = self.canv
        y1, y2 = self.height - top1, self.height - top2
        c.setStrokeColor(MUT); c.setLineWidth(1)
        c.line(x, y1, x, y2 + 5)
        c.setFillColor(MUT)
        p = c.beginPath()
        p.moveTo(x - 3.2, y2 + 5.5); p.lineTo(x + 3.2, y2 + 5.5); p.lineTo(x, y2)
        p.close()
        c.drawPath(p, fill=1, stroke=0)
        if label:
            c.setFont("Malgun", 7.6); c.setFillColor(MUT)
            c.drawString(x + label_dx, (y1 + y2) / 2 - 2, label)

    def _fan(self, x1, top1, x2, top2):
        c = self.canv
        y1, y2 = self.height - top1, self.height - top2
        c.setStrokeColor(BRW); c.setLineWidth(0.9)
        c.line(x1, y1, x2, y2 + 5)
        c.setFillColor(BRW)
        p = c.beginPath()
        p.moveTo(x2 - 3, y2 + 5.5); p.lineTo(x2 + 3, y2 + 5.5); p.lineTo(x2, y2)
        p.close()
        c.drawPath(p, fill=1, stroke=0)

    def draw(self):
        c = self.canv
        W = self.width
        cx = W / 2

        # ── Binance (외부)
        self._box(87.5, 0, 320, 40, EXT_BG, EXT,
                  "Binance USDS-M Futures (외부 거래소)",
                  "wss://fstream.binance.com/ws/btcusdt@depth20@100ms")
        self._arrow(cx, 40, 68, "WebSocket — 서버당 정확히 1개")
        c.setFont("MalgunBold", 7.6); c.setFillColor(EXT)
        c.drawRightString(cx - 10, self.height - 56, "upstream = 1")

        # ── 서버 컨테이너
        c.setFillColor(CANVAS); c.setStrokeColor(NTY); c.setLineWidth(1.2)
        c.roundRect(20, self.height - 382, 455, 314, 7, fill=1, stroke=1)
        c.setFont("MalgunBold", 9.2); c.setFillColor(NTY)
        c.drawString(32, self.height - 84, "Spring Boot 서버 — WebFlux · Reactor Netty (단일 인스턴스)")

        # 1) WebSocket 클라이언트
        self._box(40, 92, 415, 36, NTY_BG, NTY,
                  "ReactorNettyWebSocketClient — @PostConstruct 부팅 시 1회 connect",
                  "Netty event loop 스레드 · non-blocking 수신", mono_sub=False)
        self._arrow(cx, 128, 142)

        # 2) Reactor 파이프라인
        self._box(40, 142, 415, 34, FLUX_BG, FLUX,
                  "session.receive() → map(getPayloadAsText) → doOnNext(파싱·저장)",
                  "Reactor 파이프라인 — 메시지가 도착할 때마다 위→아래로 1회 흐름", mono_sub=False)
        self._arrow(cx, 176, 190)

        # 3) 파서
        self._box(40, 190, 415, 34, FLUX_BG, FLUX,
                  "OrderBookSnapshotParser → 불변 record OrderBookSnapshot",
                  '"67000.10"(String) → BigDecimal — double 미경유, 실패 시 warn 후 스트림 유지',
                  mono_sub=False)
        self._arrow(cx, 224, 238)

        # 4) Store (이중 발행)
        c.setFillColor(SINK_SOFT); c.setStrokeColor(SINK); c.setLineWidth(1.1)
        c.roundRect(40, self.height - 316, 415, 78, 6, fill=1, stroke=1)
        c.setFont("MalgunBold", 8.8); c.setFillColor(SINK)
        c.drawCentredString(247.5, self.height - 251, "LatestOrderBookSnapshotStore.update(snapshot) — 이중 발행")
        self._box(50, 264, 195, 42, AR_BG, AR,
                  "AtomicReference<Snapshot>",
                  "latest.set() — 최신 1건 보관(조회용)", mono_sub=False)
        self._box(250, 264, 195, 42, SINK_BG, SINK,
                  "Sinks.many().replay().limit(1)",
                  "tryEmitNext() — hot multicast(push용)", mono_sub=False)

        self._arrow(147.5, 316, 334)
        self._arrow(347.5, 316, 334)

        # 5) 엔드포인트
        self._box(50, 334, 195, 36, AR_BG, AR,
                  "GET …/depth/latest · 주문 API",
                  "단발 조회(404) · 모의 주문 체결 기준", mono_sub=False)
        self._box(250, 334, 195, 36, SINK_BG, SINK,
                  "GET …/depth/stream  (SSE)",
                  "sink.asFlux() → ServerSentEvent")

        # ── 브라우저 fan-out
        c.setFont("MalgunBold", 7.6); c.setFillColor(BRW)
        c.drawString(32, self.height - 398, "downstream = N")
        bx0, bw, gap = 32.5, 130, 20
        centers = [bx0 + bw / 2 + i * (bw + gap) for i in range(3)]
        for bc in centers:
            self._fan(347.5, 370, bc, 408)
        labels = ["브라우저 탭 1", "브라우저 탭 2", "브라우저 탭 N"]
        for i, bc in enumerate(centers):
            self._box(bc - bw / 2, 408, bw, 40, BRW_BG, BRW,
                      labels[i], "React EventSource", mono_sub=True)

        c.setFont("Malgun", 7.8); c.setFillColor(MUT)
        c.drawCentredString(cx, self.height - 462,
                            "늦게 접속한 탭도 replay(1)로 즉시 최신 호가 1건을 받고, 탭을 닫으면 그 구독만 cancel된다 — Binance 연결은 영향 없음")


# ---------------------------------------------------------------- 페이지 템플릿
PAGE_W, PAGE_H = A4
M_L, M_R, M_T, M_B = 50, 50, 52, 46
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "binance-stream-portfolio.pdf")


def on_page(canvas_obj, doc):
    canvas_obj.saveState()
    canvas_obj.setStrokeColor(RULE); canvas_obj.setLineWidth(0.7)
    canvas_obj.line(M_L, M_B - 12, PAGE_W - M_R, M_B - 12)
    canvas_obj.setFont("Malgun", 7.5); canvas_obj.setFillColor(MUT)
    canvas_obj.drawString(M_L, M_B - 24,
                          "Futures Paper Trading — Binance 실시간 데이터 파이프라인")
    canvas_obj.drawRightString(PAGE_W - M_R, M_B - 24, f"{canvas_obj.getPageNumber()}")
    canvas_obj.restoreState()


doc = BaseDocTemplate(OUT, pagesize=A4,
                      leftMargin=M_L, rightMargin=M_R, topMargin=M_T, bottomMargin=M_B,
                      title="Binance 실시간 데이터 파이프라인 — 백엔드 포트폴리오",
                      author="lee-gimoon")
frame = Frame(M_L, M_B, PAGE_W - M_L - M_R, PAGE_H - M_T - M_B, id="main",
              leftPadding=0, rightPadding=0, topPadding=0, bottomPadding=0)
doc.addPageTemplates([PageTemplate(id="page", frames=[frame], onPage=on_page)])

story = []

# ================================================================ 1p — 표지 + 개요
story.append(Paragraph("BACKEND PORTFOLIO · PART 1", S_KICKER))
story.append(Spacer(1, 6))
story.append(Paragraph("실시간 모의 선물 거래소", S_TITLE))
story.append(Paragraph("Binance 실시간 데이터 파이프라인", S_TITLE))
story.append(Spacer(1, 8))
story.append(Paragraph(
    "Binance USDS-M Futures의 실시간 호가창(order book)을 받아, 서버를 거쳐 모든 브라우저에 "
    "지연 없이 fan-out하는 reactive 파이프라인", S_SUB))
story.append(Spacer(1, 14))

tech_rows = [
    ["Backend",  "Java 21 · Spring Boot 4 (WebFlux) · Reactor Netty · Jackson 3"],
    ["Auth/DB",  "Spring Security · PostgreSQL (R2DBC)"],
    ["Frontend", "React + TypeScript (Vite) · TradingView Lightweight Charts"],
    ["External", "Binance USDS-M Futures — WebSocket (depth) · REST (kline)"],
]
tech = Table(
    [[Paragraph(f"<b>{k}</b>", S_LABEL), Paragraph(v, S_BODY)] for k, v in tech_rows],
    colWidths=[70, 425])
tech.setStyle(TableStyle([
    ("VALIGN", (0, 0), (-1, -1), "TOP"),
    ("LINEBELOW", (0, 0), (-1, -2), 0.5, RULE),
    ("TOPPADDING", (0, 0), (-1, -1), 5),
    ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
    ("LEFTPADDING", (0, 0), (-1, -1), 0),
]))
story.append(tech)
story.append(Spacer(1, 16))

story.append(Paragraph("이 문서의 범위", S_H2))
story.append(Paragraph(
    "전체 프로젝트는 <b>호가창 → 차트 → 회원가입/로그인 → 호가창 기준 모의 주문 → 계좌·포지션·PnL</b>로 "
    "이어지는 학습용 모의 선물 거래소다. 이 문서는 그 중 모든 기능의 기준 가격 데이터를 만들어내는 "
    "<b>Binance 실시간 데이터 파이프라인</b> 파트만 다룬다. 주문 체결 엔진과 계좌/PnL 파트는 완성 후 별도 장으로 추가 예정.",
    S_BODY))
story.append(Spacer(1, 10))

story.append(Paragraph("왜 이 파트가 프로젝트의 핵심인가", S_H2))
story.append(bullet(
    "호가창 표시, 캔들차트의 진행 봉, 모의 주문 체결가, 미실현 PnL까지 — 전부 이 파이프라인이 만드는 "
    "최신 호가 snapshot 하나를 기준값으로 동작한다."))
story.append(bullet(
    "외부 실시간 시스템(거래소 WebSocket) 연동, reactive 비동기 모델(cold/hot publisher), "
    "락 없는 동시성 처리, 생명주기 분리 설계가 이 파트에 응축되어 있다."))
story.append(bullet(
    "직접 측정으로 원인을 좁힌 트러블슈팅 2건(중복 연결, 지역 차단)이 이 파트에서 나왔다 — 4페이지 참조."))
story.append(Spacer(1, 14))

story.append(Paragraph(
    "GitHub: github.com/lee-gimoon &nbsp;·&nbsp; "
    "Sink Flow 인터랙티브 시각화: (호스팅 링크 — 배포 후 기입) &nbsp;·&nbsp; 2026.06", S_BODY_MUT))
story.append(PageBreak())

# ================================================================ 2p — 아키텍처
story.append(Paragraph("아키텍처 — 호가 데이터가 흐르는 길", S_H1))
story.append(ArchDiagram())
story.append(Spacer(1, 10))

flow_steps = [
    ("부팅 시 1회 연결", "@PostConstruct가 Binance WebSocket을 연결한다. 이후 브라우저가 몇 명 붙든 "
     "서버↔Binance 연결은 1개로 유지된다."),
    ("non-blocking 수신", "Netty event loop가 100ms마다 도착하는 depth20 프레임(상위 20개 bid/ask)을 "
     "non-blocking으로 수신한다."),
    ("파싱", "raw JSON을 불변 record OrderBookSnapshot으로 변환한다. 가격·수량은 String → BigDecimal 직행."),
    ("이중 발행", "Store가 AtomicReference 교체(조회용)와 sink.tryEmitNext(push용)를 동시에 수행한다."),
    ("SSE push", "/depth/stream 구독자 전원에게 즉시 push. 새로 접속한 브라우저는 replay(1)로 "
     "최신 1건을 바로 받아 빈 화면 없이 시작한다."),
    ("pull 조회", "/depth/latest(디버깅용 단발 조회)뿐 아니라 모의 주문 API(POST /api/paper/orders)도 "
     "체결 직전에 AtomicReference에서 최신 snapshot을 꺼내 체결 기준 호가로 쓴다."),
]
rows = []
for i, (k, v) in enumerate(flow_steps, 1):
    rows.append([Paragraph(f"<b>{i}. {k}</b>", S_LABEL), Paragraph(v, S_BODY)])
flow_table = Table(rows, colWidths=[105, 390])
flow_table.setStyle(TableStyle([
    ("VALIGN", (0, 0), (-1, -1), "TOP"),
    ("TOPPADDING", (0, 0), (-1, -1), 2.5),
    ("BOTTOMPADDING", (0, 0), (-1, -1), 2.5),
    ("LEFTPADDING", (0, 0), (-1, -1), 0),
]))
story.append(flow_table)
story.append(PageBreak())

# ================================================================ 3p — 설계 결정
story.append(Paragraph("핵심 설계 결정", S_H1))

decisions = [
    ("1. Binance 연결은 사용자가 아니라 서버가 소유한다",
     "연결을 HTTP 요청이 아닌 <b>서버 생명주기</b>(@PostConstruct)에 묶었다. 1개여야 하는 "
     "upstream(서버↔Binance)과 N개여도 되는 downstream(브라우저↔서버)을 다른 트리거로 분리한 것. "
     "브라우저가 전부 나가도 수신은 계속되고, 100명이 접속해도 Binance 연결은 1개다."),
    ("2. 폴링을 버리고 Hot Sink push로 전환",
     "초기 구현은 " + code("Flux.interval(100ms)") + "로 store를 폴링하고 "
     + code("distinctUntilChanged") + "로 중복을 걸렀다. 이를 "
     + code("Sinks.many().replay().limit(1)") + " 기반 push로 교체 — snapshot이 도착하는 "
     "순간 모든 구독자에게 전달되고, 빈 tick 낭비와 폴링 주기만큼의 지연이 사라졌다. "
     "늦게 붙은 구독자도 replay(1)로 최신 1건을 즉시 받는다."),
    ("3. 락 없는 동시성 — AtomicReference + 불변 record",
     "Writer는 WebSocket event loop 1개, Reader는 HTTP 요청 N개다. snapshot을 부분 수정하지 않고 "
     "매번 새 불변 record로 <b>통째 교체</b>하므로, synchronized 없이 AtomicReference의 원자적 "
     "참조 교체만으로 안전하다. 역할도 분리했다 — AtomicReference는 pull용(단발 조회 + 모의 주문의 "
     "체결 기준 snapshot 공급), Sink는 실시간 push용."),
    ("4. 가격은 String → BigDecimal 직행, double 금지",
     "Binance가 가격을 " + code('"67000.10"') + " 문자열로 보내는 이유는 이진 부동소수점 오차 때문이다. "
     + code("asDouble()") + "을 거치면 그 시점에 이미 값이 망가지므로, "
     + code("asString() → new BigDecimal(String)") + " 경로만 사용한다. 거래소에서 1원 오차는 누군가의 손익이다."),
    ("5. 메시지 단위 장애 격리",
     "파싱 예외를 메시지 1건 단위로 잡아 warn 로그만 남기고 스트림은 계속 흐르게 했다. "
     "깨진 메시지 하나가 실시간 파이프라인 전체를 죽이는 일을 막는다."),
    ("6. WebSocket 중계 대신 SSE, Tomcat 대신 WebFlux",
     "서버→브라우저는 단방향 push라 SSE로 충분하다(EventSource 자동 재연결 포함). "
     "blocking MVC로 SSE를 구현하면 연결 100개에 worker thread 100개가 묶이지만, "
     "WebFlux/Netty event loop는 쓸 데이터가 생긴 순간에만 잠깐 write를 처리하므로 "
     "적은 스레드로 많은 SSE 연결을 유지한다."),
]
for title, body in decisions:
    story.append(KeepTogether([
        Paragraph(title, S_H2),
        Paragraph(body, S_BODY),
        Spacer(1, 5),
    ]))
story.append(PageBreak())

# ================================================================ 4p — 트러블슈팅
story.append(Paragraph("트러블슈팅", S_H1))


def ts_block(no, title, items):
    flow = [Paragraph(f"{no}. {title}", S_H2), Spacer(1, 2)]
    rows = [[Paragraph(f"<b>{k}</b>", S_LABEL), Paragraph(v, S_BODY)] for k, v in items]
    t = Table(rows, colWidths=[62, 433])
    t.setStyle(TableStyle([
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("TOPPADDING", (0, 0), (-1, -1), 3),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
        ("LEFTPADDING", (0, 0), (-1, -1), 0),
        ("LINEBELOW", (0, 0), (-1, -2), 0.4, RULE),
    ]))
    flow.append(t)
    flow.append(Spacer(1, 14))
    return flow


story += ts_block(
    "1", "같은 Binance 스트림이 호출 수만큼 중복 연결되던 문제",
    [
        ("증상", "초기엔 프론트의 '스트림 시작' 버튼이 POST /raw/start로 connect()를 호출하는 구조였다. "
         "버튼 재클릭·새로고침·새 탭마다 Binance WebSocket이 1개씩 늘어났고, 재연결이 아니라 같은 시간대에 "
         "N개가 나란히 살아서 동일 데이터의 수신 트래픽·로그·CPU가 N배가 됐다."),
        ("원인", code("webSocketClient.execute(...)") + "가 반환하는 Mono는 <b>cold publisher</b> — "
         "subscribe할 때마다 새 연결을 만든다. 근본 원인은 1개여야 하는 upstream 연결의 생성을 "
         "N번 발생하는 downstream 트리거(사용자 행동)에 묶은 설계였다."),
        ("해결", "connect()를 HTTP 엔드포인트에서 떼어내 @PostConstruct로 이동 — 빈 생성 직후 정확히 1회만 "
         "subscribe된다. 연결 수가 사용자 행동과 완전히 분리됐다."),
        ("배운 점", "cold/hot publisher 구분이 '이 코드가 몇 번 실행되는가'를 결정한다는 것. "
         "그리고 외부 연결의 소유권은 요청이 아니라 서버 생명주기에 둬야 한다는 원칙."),
    ])

story += ts_block(
    "2", "WebSocket이 연결은 되는데 메시지가 0건 — 지역 차단 진단",
    [
        ("증상", "캔들차트용 @kline 스트림을 구독하면 에러 없이 연결되지만 메시지가 한 건도 오지 않았다. "
         "예외도 로그도 없어서 코드만 봐서는 원인을 알 수 없는 상태."),
        ("진단", "스트림을 계열별로 분리해 수신 메시지 수를 직접 측정했다. 결과 — 체결 계열"
         "(@kline·@aggTrade·@markPrice)은 전부 <b>0건</b>, 호가 계열(@depth·@bookTicker)과 REST API는 "
         "<b>정상</b>. 동일한 코드·연결 방식에서 스트림 계열별로만 결과가 갈리므로, 코드 문제가 아니라 "
         "네트워크/지역 차단으로 원인을 분리했다."),
        ("해결", "과거 봉은 Binance kline REST로 backfill(공개 시세라 API 키가 불필요 → 브라우저가 직접 호출), "
         "진행 봉은 이미 백엔드가 받고 있는 호가 SSE의 best ask로 OHLC를 갱신. 추가 연결 0개로 차트를 완성했다."),
        ("배운 점", "'안 된다'를 '정확히 무엇이 어디까지 되는가'로 좁히는 측정 기반 디버깅. "
         "진행 봉이 체결가가 아닌 호가 기준이라는 한계는 숨기지 않고 문서에 명시했다."),
    ])
story.append(PageBreak())

# ================================================================ 5p — 한계와 다음 단계
story.append(Paragraph("현재 한계와 다음 단계", S_H1))

story.append(Paragraph("정직하게 적는 현재 한계", S_H2))
story.append(bullet(
    "<b>partial depth20 snapshot</b> — 정밀한 로컬 오더북이 아니다. Binance가 100ms마다 보내는 "
    "상위 20레벨을 통째로 교체할 뿐, sequence 검증이 없다. 호가창 표시·간단한 모의 체결에는 충분하지만 "
    "정밀 체결 시뮬레이션에는 부족하다."))
story.append(bullet(
    "<b>단일 서버 local fan-out</b> — 구독자 수백 명까지는 무난할 것으로 보지만, 1,000명 전후부터는 "
    "부하 테스트가 필요하다."))
story.append(bullet(
    "<b>재연결·운영성 미구현</b> — 연결 끊김 시 지수 백오프 재연결, stale 호가 감지, health 표시는 "
    "거래 흐름(주문·계좌) 완성 후 붙일 계획이다."))
story.append(Spacer(1, 10))

story.append(Paragraph("다음 단계 (로드맵 기준)", S_H2))
story.append(bullet(
    "<b>정밀 로컬 오더북</b> — REST snapshot + diff depth 스트림을 U/u/pu 필드로 sequence 검증하며 "
    "유지하고, 불일치 시 snapshot 재동기화. 현재의 SSE/화면은 그대로 두고 내부 source만 교체한다."))
story.append(bullet(
    "<b>운영성 보강</b> — health 엔드포인트, 연결 status(UP/DOWN/STALE), 지수 백오프 재연결, 설정 외부화."))
story.append(bullet(
    "<b>확장 설계</b> — 선직렬화(구독자 N명에게 같은 JSON을 N번 만들지 않기), throttle/diff 전송, "
    "다중 인스턴스 시 브로커 도입 검토."))
story.append(Spacer(1, 16))

story.append(Paragraph("이 파트가 받치는 다음 기능들", S_H2))
story.append(Paragraph(
    "이 파이프라인이 만드는 최신 호가 snapshot은 이후 단계의 기준값이 된다 — 모의 주문은 best bid/ask부터 "
    "호가 레벨을 순서대로 소진하며 체결되고(주문:체결 = 1:N), 미실현 PnL은 mid price로 계산된다. "
    "즉 이 문서의 파이프라인이 거래소 전체의 '가격 진실 공급원(source of truth)'이다.", S_BODY))
story.append(Spacer(1, 16))
story.append(Paragraph(
    "본 문서는 프로젝트 진행에 따라 업데이트됩니다. 주문 체결 엔진(PART 2), 계좌·포지션·PnL(PART 3) 추가 예정.",
    S_BODY_MUT))

doc.build(story)
print("OK:", OUT)
