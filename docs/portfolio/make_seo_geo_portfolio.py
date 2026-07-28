# -*- coding: utf-8 -*-
"""Futures Paper Trading SEO/GEO case-study portfolio PDF."""

from pathlib import Path
from io import BytesIO

from reportlab.lib.colors import Color, HexColor, white
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4, landscape
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.utils import ImageReader
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas
from reportlab.platypus import Paragraph
from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
ASSET_DIR = ROOT / "docs" / "portfolio" / "assets"
OUT_DIR = ROOT / "output" / "pdf"
OUT_DIR.mkdir(parents=True, exist_ok=True)
OUT = OUT_DIR / "futures-paper-trading-seo-geo-portfolio.pdf"

W, H = landscape(A4)
M = 42

FONT_DIR = Path(r"C:\Windows\Fonts")
pdfmetrics.registerFont(TTFont("Malgun", str(FONT_DIR / "malgun.ttf")))
pdfmetrics.registerFont(TTFont("MalgunBold", str(FONT_DIR / "malgunbd.ttf")))
pdfmetrics.registerFont(TTFont("Consolas", str(FONT_DIR / "consola.ttf")))
pdfmetrics.registerFontFamily(
    "Malgun",
    normal="Malgun",
    bold="MalgunBold",
    italic="Malgun",
    boldItalic="MalgunBold",
)

NAVY = HexColor("#08111F")
NAVY_2 = HexColor("#10233B")
INK = HexColor("#122033")
MUTED = HexColor("#65758B")
LINE = HexColor("#D9E1EA")
PAPER = HexColor("#F7F9FC")
WHITE = HexColor("#FFFFFF")
MINT = HexColor("#20C997")
MINT_DARK = HexColor("#087F66")
MINT_SOFT = HexColor("#DDF8EF")
BLUE = HexColor("#377DFF")
BLUE_SOFT = HexColor("#E8F0FF")
AMBER = HexColor("#E8A317")
AMBER_SOFT = HexColor("#FFF4D6")
RED = HexColor("#E05757")
RED_SOFT = HexColor("#FFE8E8")


def style(name, size=10, leading=None, color=INK, bold=False, align=TA_LEFT):
    return ParagraphStyle(
        name,
        fontName="MalgunBold" if bold else "Malgun",
        fontSize=size,
        leading=leading or size * 1.45,
        textColor=color,
        alignment=align,
        wordWrap="CJK",
        splitLongWords=False,
    )


S_BODY = style("body", 9.4, 14.2)
S_BODY_SM = style("body-sm", 8.2, 12.2, MUTED)
S_BODY_XS = style("body-xs", 7.3, 10.5, MUTED)
S_H1 = style("h1", 22, 29, bold=True)
S_H2 = style("h2", 15, 21, bold=True)
S_H3 = style("h3", 10.5, 15, bold=True)
S_WHITE = style("white", 9.2, 14, WHITE)
S_WHITE_MUT = style("white-mut", 8.5, 12.5, HexColor("#B7C8E0"))
S_WHITE_TITLE = style("white-title", 28, 36, WHITE, bold=True)
S_MONO = style("mono", 7.5, 11, HexColor("#D6E4F5"))


def para(c, text, x, y_top, width, sty=S_BODY, max_height=300):
    """Draw Paragraph using a top-origin y coordinate. Returns rendered height."""
    p = Paragraph(text, sty)
    _, height = p.wrap(width, max_height)
    p.drawOn(c, x, y_top - height)
    return height


def pill(c, text, x, y, fill=MINT_SOFT, color=MINT_DARK, pad_x=9, height=22):
    c.setFont("MalgunBold", 7.5)
    text_w = pdfmetrics.stringWidth(text, "MalgunBold", 7.5)
    width = text_w + pad_x * 2
    c.setFillColor(fill)
    c.roundRect(x, y, width, height, height / 2, fill=1, stroke=0)
    c.setFillColor(color)
    c.drawCentredString(x + width / 2, y + 7, text)
    return width


def label(c, text, x, y, color=MINT_DARK):
    c.setFont("MalgunBold", 7.4)
    c.setFillColor(color)
    c.drawString(x, y, text)


def section_title(c, kicker, title, subtitle=None):
    label(c, kicker, M, H - 54)
    title_height = para(c, title, M, H - 72, 730, S_H1)
    if subtitle:
        para(c, subtitle, M, H - 72 - title_height - 7, 720, S_BODY_SM)


def footer(c, page):
    c.setStrokeColor(LINE)
    c.setLineWidth(0.6)
    c.line(M, 27, W - M, 27)
    c.setFont("Malgun", 7)
    c.setFillColor(MUTED)
    c.drawString(M, 14, "검색엔진과 AI가 이해하기 쉬운 웹페이지 만들기 · Futures Paper Trading")
    c.drawRightString(W - M, 14, f"{page} / 5")


def draw_fitted_image(c, path, x, y, width, height, stroke=LINE, radius=9):
    img = ImageReader(str(path))
    iw, ih = img.getSize()
    scale = min(width / iw, height / ih)
    dw, dh = iw * scale, ih * scale
    dx, dy = x + (width - dw) / 2, y + (height - dh) / 2
    c.setFillColor(NAVY)
    c.roundRect(x, y, width, height, radius, fill=1, stroke=0)
    c.drawImage(img, dx, dy, dw, dh, preserveAspectRatio=True, mask="auto")
    c.setStrokeColor(stroke)
    c.setLineWidth(0.8)
    c.roundRect(x, y, width, height, radius, fill=0, stroke=1)


def draw_cropped_image(c, path, x, y, width, height, stroke=LINE, radius=9, focus_x=0.5):
    """Center-crop a raster image to fill the target frame."""
    image = Image.open(path).convert("RGB")
    iw, ih = image.size
    target_ratio = width / height
    source_ratio = iw / ih
    if source_ratio > target_ratio:
        crop_w = int(ih * target_ratio)
        left = int((iw - crop_w) * focus_x)
        left = max(0, min(left, iw - crop_w))
        image = image.crop((left, 0, left + crop_w, ih))
    else:
        crop_h = int(iw / target_ratio)
        top = (ih - crop_h) // 2
        image = image.crop((0, top, iw, top + crop_h))
    buffer = BytesIO()
    image.save(buffer, format="JPEG", quality=92)
    buffer.seek(0)
    c.drawImage(ImageReader(buffer), x, y, width, height, mask="auto")
    c.setStrokeColor(stroke)
    c.setLineWidth(0.8)
    c.roundRect(x, y, width, height, radius, fill=0, stroke=1)


def metric_card(c, x, y, w, h, title, value, note, accent=MINT):
    c.setFillColor(WHITE)
    c.setStrokeColor(LINE)
    c.setLineWidth(0.8)
    c.roundRect(x, y, w, h, 10, fill=1, stroke=1)
    c.setFillColor(accent)
    c.roundRect(x, y + h - 5, w, 5, 5, fill=1, stroke=0)
    c.setFont("MalgunBold", 8)
    c.setFillColor(MUTED)
    c.drawString(x + 14, y + h - 23, title)
    c.setFont("MalgunBold", 23)
    c.setFillColor(INK)
    c.drawString(x + 14, y + 27, value)
    c.setFont("Malgun", 7.2)
    c.setFillColor(MUTED)
    c.drawString(x + 14, y + 12, note)


def issue_row(c, y, no, title, evidence, impact):
    c.setFillColor(WHITE)
    c.setStrokeColor(LINE)
    c.roundRect(M, y - 47, W - 2 * M, 43, 7, fill=1, stroke=1)
    c.setFillColor(RED_SOFT)
    c.circle(M + 18, y - 25, 10, fill=1, stroke=0)
    c.setFillColor(RED)
    c.setFont("MalgunBold", 8)
    c.drawCentredString(M + 18, y - 28, str(no))
    para(c, title, M + 38, y - 13, 170, S_H3)
    para(c, evidence, M + 220, y - 12, 286, S_BODY_XS)
    para(c, impact, M + 525, y - 12, 230, S_BODY_XS)


def flow_box(c, x, y, w, title, sub, accent):
    c.setFillColor(WHITE)
    c.setStrokeColor(accent)
    c.setLineWidth(1.2)
    c.roundRect(x, y, w, 78, 10, fill=1, stroke=1)
    c.setFillColor(accent)
    c.roundRect(x + 13, y + 50, 28, 15, 7, fill=1, stroke=0)
    c.setFillColor(WHITE)
    c.setFont("MalgunBold", 6.8)
    c.drawCentredString(x + 27, y + 55, "STEP")
    para(c, title, x + 14, y + 44, w - 28, S_H3)
    para(c, sub, x + 14, y + 25, w - 28, S_BODY_XS)


def score_bar(c, x, y, width, label_text, value, color):
    c.setFont("MalgunBold", 8)
    c.setFillColor(INK)
    c.drawString(x, y + 5, label_text)
    bar_x = x + 105
    bar_w = width - 142
    c.setFillColor(HexColor("#E8EDF3"))
    c.roundRect(bar_x, y, bar_w, 14, 7, fill=1, stroke=0)
    c.setFillColor(color)
    c.roundRect(bar_x, y, bar_w * value / 100, 14, 7, fill=1, stroke=0)
    c.setFont("Consolas", 9)
    c.setFillColor(INK)
    c.drawRightString(x + width, y + 3, str(value))


def link(c, text, url, x, y, size=7.4, color=BLUE):
    c.setFont("MalgunBold", size)
    c.setFillColor(color)
    c.drawString(x, y, text)
    width = pdfmetrics.stringWidth(text, "MalgunBold", size)
    c.linkURL(url, (x, y - 2, x + width, y + size + 2), relative=0)


pdf = canvas.Canvas(str(OUT), pagesize=(W, H))
pdf.setTitle("SEO and GEO Improvement Case Study")
pdf.setAuthor("Gi Mun Lee")
pdf.setSubject("Futures Paper Trading SEO/GEO case study")


# ------------------------------------------------------------------ 1 / Cover
pdf.setFillColor(NAVY)
pdf.rect(0, 0, W, H, fill=1, stroke=0)
pdf.setFillColor(Color(0.11, 0.35, 0.52, alpha=0.18))
pdf.circle(W - 105, H - 80, 210, fill=1, stroke=0)
pdf.setStrokeColor(HexColor("#173555"))
for gx in range(455, 842, 48):
    pdf.line(gx, 0, gx, H)
for gy in range(0, 596, 48):
    pdf.line(455, gy, W, gy)

label(pdf, "SEO / GEO PORTFOLIO · 2026.07", M, H - 55, MINT)
para(
    pdf,
    "검색엔진과 AI가<br/>이해하기 쉬운 웹페이지 만들기",
    M,
    H - 89,
    405,
    S_WHITE_TITLE,
)
para(
    pdf,
    "Futures Paper Trading 프로젝트를 진단하고, 검색용 정적 소개 페이지를 "
    "분리 설계한 <b>사이트 진단 → 전략 → 실행 → 검증</b> 사례",
    M,
    H - 178,
    365,
    S_WHITE_MUT,
)

draw_cropped_image(
    pdf,
    ASSET_DIR / "seo-geo-after-hero.png",
    438,
    145,
    360,
    286,
    stroke=HexColor("#2B4D70"),
    radius=12,
    focus_x=0.42,
)

facts = [
    ("TARGET", "비트코인 선물 모의투자"),
    ("METHOD", "Static HTML · JSON-LD · Sitemap"),
    ("VERIFY", "Lighthouse 13.4.1"),
]
fy = 152
for key, value in facts:
    pdf.setFont("Consolas", 7)
    pdf.setFillColor(MINT)
    pdf.drawString(M, fy, key)
    pdf.setFont("Malgun", 8.2)
    pdf.setFillColor(HexColor("#D2DFEE"))
    pdf.drawString(M + 54, fy, value)
    fy -= 24

pdf.setStrokeColor(HexColor("#26415F"))
pdf.line(M, 62, W - M, 62)
pdf.setFont("MalgunBold", 8)
pdf.setFillColor(WHITE)
pdf.drawString(M, 42, "이기문 · 개인 프로젝트")
pdf.setFont("Malgun", 7.4)
pdf.setFillColor(HexColor("#9EB1C9"))
pdf.drawRightString(W - M, 42, "github.com/lee-gimoon/futures-paper-trading")
pdf.showPage()


# ------------------------------------------------------------------ 2 / Diagnosis
pdf.setFillColor(PAPER)
pdf.rect(0, 0, W, H, fill=1, stroke=0)
section_title(
    pdf,
    "01 · BASELINE DIAGNOSIS",
    "기존 거래 화면은 기능은 보였지만, 서비스 설명은 보이지 않았습니다",
    "운영 사이트의 실제 DOM과 PageSpeed, 동일 로컬 환경의 Lighthouse를 함께 확인했습니다.",
)

metric_card(pdf, M, 346, 150, 84, "운영 PageSpeed SEO", "90", "데스크톱 · 2026-07-29", BLUE)
metric_card(pdf, M + 162, 346, 150, 84, "로컬 Lighthouse SEO", "82", "동일 전후 측정 기준선", RED)
metric_card(pdf, M + 324, 346, 150, 84, "설명 메타데이터", "0", "description · canonical 없음", AMBER)
metric_card(pdf, M + 486, 346, 150, 84, "구조화 데이터", "0", "JSON-LD 스크립트 없음", AMBER)
metric_card(pdf, M + 648, 346, 108, 84, "설명 H2", "0", "H1은 차트명 1개", RED)

pdf.setFont("MalgunBold", 9)
pdf.setFillColor(INK)
pdf.drawString(M, 323, "진단 항목")
pdf.setFont("MalgunBold", 7.3)
pdf.setFillColor(MUTED)
pdf.drawString(M + 220, 323, "확인된 증거")
pdf.drawString(M + 525, 323, "검색·AI 이해에 미치는 영향")

issue_row(pdf, 311, 1, "서비스 정의 부재", "화면의 대표 문구는 “BTCUSDT 차트 / 호가창”뿐", "누구를 위한 어떤 서비스인지 추출하기 어려움")
issue_row(pdf, 262, 2, "메타정보 부재", "description · canonical · OG · favicon 링크 없음", "검색 결과 요약과 대표 URL 신호가 약함")
issue_row(pdf, 213, 3, "검색 인프라 부재", "robots.txt · sitemap.xml이 없고 /api 제외 규칙도 없음", "중요 URL 발견·크롤링 범위를 명시하지 못함")
issue_row(pdf, 164, 4, "GEO 근거 부재", "용어 정의 · 출처 · FAQ · 한계·면책 설명 없음", "AI가 답변으로 재구성할 명확한 근거 단위가 부족")

draw_fitted_image(pdf, ASSET_DIR / "seo-geo-before.png", M, 45, W - 2 * M, 58, stroke=LINE, radius=7)
pdf.setFont("Malgun", 6.8)
pdf.setFillColor(MUTED)
pdf.drawString(M, 34, "개선 전 운영 화면 · 실시간 차트와 호가 기능은 명확하지만 검색용 설명 콘텐츠가 없는 상태")
footer(pdf, 2)
pdf.showPage()


# ------------------------------------------------------------------ 3 / Strategy
pdf.setFillColor(PAPER)
pdf.rect(0, 0, W, H, fill=1, stroke=0)
section_title(
    pdf,
    "02 · STRATEGY",
    "동적 거래 앱과 검색용 설명 페이지의 역할을 분리했습니다",
    "거래 화면의 실시간 기능은 유지하고, 검색엔진과 AI가 JavaScript 없이 읽을 수 있는 정적 페이지를 추가했습니다.",
)

flow_box(pdf, 50, 352, 160, "검색 사용자 · AI", "“비트코인 선물 모의투자는 무엇인가?”", BLUE)
flow_box(pdf, 255, 352, 220, "정적 프로젝트 소개", "정의 · 기능 · 용어 · 출처 · FAQ · 한계", MINT_DARK)
flow_box(pdf, 520, 352, 130, "실시간 앱", "차트 · 호가 · 모의 주문", AMBER)
flow_box(pdf, 680, 352, 115, "근거", "GitHub · 공식 문서", HexColor("#7357C8"))
pdf.setStrokeColor(MUTED)
pdf.setLineWidth(1.2)
for x1, x2 in [(210, 255), (475, 520), (650, 680)]:
    pdf.line(x1 + 4, 391, x2 - 5, 391)
    pdf.setFillColor(MUTED)
    pdf.drawString(x2 - 9, 388, "›")

pdf.setFillColor(WHITE)
pdf.setStrokeColor(LINE)
pdf.roundRect(M, 148, 364, 172, 10, fill=1, stroke=1)
pdf.roundRect(M + 378, 148, 378, 172, 10, fill=1, stroke=1)
label(pdf, "SEO", M + 18, 296, BLUE)
para(pdf, "검색엔진이 발견하고 문서 구조를 이해하도록", M + 18, 278, 320, S_H2)
seo_items = [
    "고유 title · description · canonical · Open Graph",
    "H1 1개와 논리적인 H2/H3 · semantic main/nav/section",
    "robots.txt · sitemap.xml · 내부 링크 · clean URL",
    "동일 거래 페이지의 Lighthouse SEO 82 → 100",
]
yy = 242
for item in seo_items:
    para(pdf, f"<font color='#377DFF'>●</font>&nbsp;&nbsp;{item}", M + 18, yy, 320, S_BODY_SM)
    yy -= 23

label(pdf, "GEO", M + 396, 296, MINT_DARK)
para(pdf, "AI가 답변으로 재구성할 수 있는 정보 단위 제공", M + 396, 278, 340, S_H2)
geo_items = [
    "첫 문단에서 서비스 정체성과 핵심 기능을 직접 답변",
    "선물 모의투자 · 호가 기반 체결 · 격리 마진 용어 정의",
    "FAQ 본문과 일치하는 FAQPage JSON-LD",
    "공식 출처 · 구현 근거 · 한계 · 업데이트 날짜 명시",
]
yy = 242
for item in geo_items:
    para(pdf, f"<font color='#087F66'>●</font>&nbsp;&nbsp;{item}", M + 396, yy, 340, S_BODY_SM)
    yy -= 23

pdf.setFillColor(AMBER_SOFT)
pdf.setStrokeColor(HexColor("#E0BE61"))
pdf.roundRect(M, 55, W - 2 * M, 73, 9, fill=1, stroke=1)
para(pdf, "<b>판단 기준</b>", M + 18, 107, 90, S_H3)
para(
    pdf,
    "“AI 노출을 보장했다”가 아니라, <b>답변 우선 구조 · 명확한 용어 · 출처 · 구조화 데이터</b>를 갖춰 "
    "기계가 정보를 추출하기 쉬운 상태를 만들었는지 평가했습니다. 실제 노출·클릭은 배포 후 Search Console로 측정합니다.",
    M + 108,
    109,
    W - 2 * M - 126,
    S_BODY_SM,
)
footer(pdf, 3)
pdf.showPage()


# ------------------------------------------------------------------ 4 / Execution
pdf.setFillColor(PAPER)
pdf.rect(0, 0, W, H, fill=1, stroke=0)
section_title(
    pdf,
    "03 · EXECUTION",
    "설명 콘텐츠를 코드와 크롤링 인프라까지 연결했습니다",
    "React Router를 추가하지 않고 Vite multi-page 정적 HTML로 분리해, 소개 페이지에서는 SSE·인증 API·대형 JS 번들이 실행되지 않습니다.",
)

draw_cropped_image(pdf, ASSET_DIR / "seo-geo-after-hero.png", M, 240, 385, 180, stroke=LINE, radius=9)

blocks = [
    ("01", "답변 우선 콘텐츠", "첫 문단에서 서비스 정의와 핵심 기능을 한 문장으로 설명하고, 기능·용어·FAQ·한계를 순서대로 배치"),
    ("02", "정적 multi-page", "project/index.html을 별도 빌드해 JavaScript 실행 전에도 전체 본문과 링크를 제공"),
    ("03", "메타·구조화 데이터", "WebApplication · FAQPage · BreadcrumbList JSON-LD와 canonical · OG · favicon 적용"),
    ("04", "크롤링 경로", "robots.txt에서 /api/ 제외, sitemap.xml에 핵심 URL 2개 등록, /project/ clean URL 매핑"),
]
bx, by = 450, 402
for idx, (no, title, body) in enumerate(blocks):
    row = idx // 2
    col = idx % 2
    x = bx + col * 173
    y = by - row * 96
    pdf.setFillColor(WHITE)
    pdf.setStrokeColor(LINE)
    pdf.roundRect(x, y - 70, 160, 82, 9, fill=1, stroke=1)
    pdf.setFillColor(MINT_SOFT if idx != 3 else BLUE_SOFT)
    pdf.circle(x + 18, y - 5, 10, fill=1, stroke=0)
    pdf.setFont("Consolas", 7)
    pdf.setFillColor(MINT_DARK if idx != 3 else BLUE)
    pdf.drawCentredString(x + 18, y - 8, no)
    para(pdf, title, x + 34, y + 1, 112, S_H3)
    para(pdf, body, x + 12, y - 27, 136, S_BODY_XS)

pdf.setFillColor(NAVY)
pdf.roundRect(M, 72, W - 2 * M, 158, 10, fill=1, stroke=0)
label(pdf, "IMPLEMENTATION EVIDENCE", M + 18, 209, MINT)
code_left = (
    '<font face="Consolas" color="#82AEEF">&lt;meta</font> '
    '<font face="Consolas" color="#D6E4F5">name="description"</font><br/>'
    '<font face="Consolas" color="#82AEEF">&lt;link</font> '
    '<font face="Consolas" color="#D6E4F5">rel="canonical"</font><br/>'
    '<font face="Consolas" color="#82AEEF">&lt;script</font> '
    '<font face="Consolas" color="#D6E4F5">type="application/ld+json"</font><br/><br/>'
    '<font face="Consolas" color="#33D6A6">WebApplication · FAQPage · BreadcrumbList</font>'
)
code_mid = (
    '<font face="Consolas" color="#82AEEF">User-agent:</font> '
    '<font face="Consolas" color="#D6E4F5">*</font><br/>'
    '<font face="Consolas" color="#82AEEF">Allow:</font> '
    '<font face="Consolas" color="#D6E4F5">/</font><br/>'
    '<font face="Consolas" color="#82AEEF">Disallow:</font> '
    '<font face="Consolas" color="#D6E4F5">/api/</font><br/><br/>'
    '<font face="Consolas" color="#33D6A6">Sitemap: …/sitemap.xml</font>'
)
code_right = (
    '<font face="Consolas" color="#82AEEF">GET</font> '
    '<font face="Consolas" color="#D6E4F5">/project</font><br/>'
    '<font face="Consolas" color="#33D6A6">→ 308 /project/</font><br/><br/>'
    '<font face="Consolas" color="#82AEEF">GET</font> '
    '<font face="Consolas" color="#D6E4F5">/project/</font><br/>'
    '<font face="Consolas" color="#33D6A6">→ static/project/index.html</font>'
)
para(pdf, code_left, M + 18, 190, 225, S_MONO)
para(pdf, code_mid, M + 268, 190, 205, S_MONO)
para(pdf, code_right, M + 500, 190, 220, S_MONO)
footer(pdf, 4)
pdf.showPage()


# ------------------------------------------------------------------ 5 / Results
pdf.setFillColor(PAPER)
pdf.rect(0, 0, W, H, fill=1, stroke=0)
section_title(
    pdf,
    "04 · VALIDATION & RESULT",
    "기초 검색 적합성은 개선됐고, 실제 노출 성과는 다음 단계로 남겼습니다",
    "Lighthouse 13.4.1 · 모바일 에뮬레이션 · 로컬 production preview · 2026-07-29",
)

pdf.setFillColor(WHITE)
pdf.setStrokeColor(LINE)
pdf.roundRect(M, 240, 470, 180, 10, fill=1, stroke=1)
label(pdf, "동일 거래 페이지 전후 비교", M + 18, 396, MINT_DARK)
score_bar(pdf, M + 18, 361, 430, "SEO", 82, RED)
score_bar(pdf, M + 18, 329, 430, "SEO · 개선 후", 100, MINT)
score_bar(pdf, M + 18, 289, 430, "접근성", 97, BLUE)
score_bar(pdf, M + 18, 257, 430, "접근성 · 개선 후", 100, MINT)
pdf.setFont("Malgun", 6.9)
pdf.setFillColor(MUTED)
pdf.drawString(M + 18, 246, "같은 URL(/)에서 meta description · robots.txt · main landmark 등을 적용한 결과")

metric_card(pdf, 534, 338, 121, 82, "소개 페이지 성능", "100", "정적 HTML · 8.8KB 전송", MINT)
metric_card(pdf, 668, 338, 121, 82, "소개 페이지 SEO", "100", "description · crawlable 통과", MINT)
metric_card(pdf, 534, 250, 121, 75, "접근성", "100", "skip link · landmark", BLUE)
metric_card(pdf, 668, 250, 121, 75, "권장사항", "100", "콘솔 오류 없는 정적 페이지", BLUE)

pdf.setFillColor(WHITE)
pdf.setStrokeColor(LINE)
pdf.roundRect(M, 76, 364, 160, 10, fill=1, stroke=1)
label(pdf, "검증 완료", M + 18, 212, MINT_DARK)
checks = [
    "Vite production build 성공 · 소개 페이지 13.4KB HTML",
    "Java compile 성공 · /project/ 라우팅 코드 컴파일",
    "robots.txt 200 text/plain · sitemap.xml URL 2개",
    "H1 1개 · H2 6개 · main 1개 · JSON-LD 1개",
    "canonical · 설명 · OG · 내부 링크 · 공식 출처 확인",
]
yy = 188
for item in checks:
    para(pdf, f"<font color='#20C997'>✓</font>&nbsp;&nbsp;{item}", M + 18, yy, 326, S_BODY_SM)
    yy -= 24

pdf.setFillColor(WHITE)
pdf.setStrokeColor(LINE)
pdf.roundRect(M + 378, 76, 378, 160, 10, fill=1, stroke=1)
label(pdf, "아직 증명하지 않은 것", M + 396, 212, AMBER)
para(
    pdf,
    "<b>검색 순위·유입·AI 인용 증가는 아직 성과로 주장하지 않습니다.</b><br/>"
    "현재 검증은 기술적 검색 접근성과 정보 구조 개선까지입니다.",
    M + 396,
    191,
    338,
    S_BODY_SM,
)
label(pdf, "배포 후 4주 측정 계획", M + 396, 143, BLUE)
para(
    pdf,
    "① Search Console 색인 요청&nbsp;&nbsp; ② 노출·클릭·CTR 기록&nbsp;&nbsp; "
    "③ 핵심 질문의 AI 답변·출처 반복 확인&nbsp;&nbsp; ④ 쿼리별 콘텐츠 보완",
    M + 396,
    127,
    338,
    S_BODY_XS,
)

link(
    pdf,
    "Live App",
    "https://futures-paper-trading-production.up.railway.app/",
    M,
    58,
)
link(
    pdf,
    "GitHub",
    "https://github.com/lee-gimoon/futures-paper-trading",
    M + 70,
    58,
)
pdf.setFont("Malgun", 6.8)
pdf.setFillColor(MUTED)
pdf.drawRightString(
    W - M,
    58,
    "근거: Lighthouse 13.4.1 JSON 보고서 · 운영 PageSpeed Insights · 저장소 코드/README",
)
footer(pdf, 5)
pdf.showPage()

pdf.save()
print(f"OK: {OUT}")
