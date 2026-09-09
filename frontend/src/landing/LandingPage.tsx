import { Link } from 'react-router-dom';

import tradingPreview from '../assets/landing/trading-preview.png';
import styles from './LandingPage.module.css';

// 랜딩페이지에 표시할 핵심 기능을 데이터로 분리해 같은 카드 구조를 반복해서 사용한다.
const features = [
  {
    number: '01',
    title: '실시간 시장 데이터',
    description: 'BTCUSDT 캔들 차트와 매수·매도 호가를 실시간으로 확인하며 시장의 흐름을 읽습니다.',
  },
  {
    number: '02',
    title: '다양한 주문 연습',
    description: '시장가와 지정가 주문을 실행하고 대기 주문, 체결 내역, 포지션 변화를 한 화면에서 확인합니다.',
  },
  {
    number: '03',
    title: '선물거래 시뮬레이션',
    description: '1배부터 50배까지 레버리지를 설정하고 증거금, 손익, 청산가 변화를 직접 경험합니다.',
  },
];

// 사용자가 서비스를 시작하는 과정을 짧은 단계로 안내한다.
const steps = [
  { number: '01', title: '계정 만들기', description: '간단히 가입하고 모의거래 계정을 준비합니다.' },
  { number: '02', title: '시장 분석하기', description: '실시간 차트와 호가를 보며 진입 시점을 판단합니다.' },
  { number: '03', title: '거래 실행하기', description: '가상 자금으로 주문하고 포지션과 손익을 확인합니다.' },
];

// 비트코인 심볼을 사용한 서비스 로고. 별도 이미지 요청 없이 선명하게 보이도록 SVG로 작성한다.
function BrandMark() {
  return (
    <span className={styles.brandMark} aria-hidden="true">
      <svg viewBox="0 0 32 32" role="img">
        <circle cx="16" cy="16" r="16" fill="currentColor" />
        <path
          d="M20.6 14.3c1.9-.7 2.8-2.1 2.5-4.1-.4-2.7-2.8-3.6-5.9-3.8V3h-2.1v3.3h-1.7V3h-2.1v3.4H7v2.2h1.6c.9 0 1.3.5 1.3 1.1v9.6c-.1.5-.4.9-1.1.9H7.2L6.8 23h4.3v3.5h2.1V23h1.7v3.5H17V23c4.1-.2 7-1.3 7.4-5.1.3-2.3-.9-3.5-3.8-3.6Zm-6.8-5.5c1.9 0 5-.3 5 2.1 0 2.3-3.1 2.1-5 2.1V8.8Zm0 11.2v-4.6c2.3 0 6-.3 6 2.3 0 2.6-3.7 2.3-6 2.3Z"
          fill="#111318"
        />
      </svg>
    </span>
  );
}

// 기본 주소(/)에서 보이는 서비스 소개 페이지.
// 실제 시세 구독과 거래 훅은 TradingPage가 마운트되는 /trade에서만 실행된다.
export default function LandingPage() {
  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <nav className={styles.nav} aria-label="주요 메뉴">
          <a className={styles.brand} href="#top" aria-label="BTC Paper 홈">
            <BrandMark />
            <span>BTC PAPER</span>
          </a>

          <div className={styles.navLinks}>
            <a href="#features">주요 기능</a>
            <a href="#how-it-works">이용 방법</a>
          </div>

          <Link className={styles.navCta} to="/trade">
            거래하러 가기
            <span aria-hidden="true">↗</span>
          </Link>
        </nav>
      </header>

      <main id="top">
        <section className={styles.hero}>
          <div className={styles.heroGlow} aria-hidden="true" />
          <p className={styles.eyebrow}>
            <span className={styles.liveDot} />
            REAL-TIME BTCUSDT · PAPER FUTURES
          </p>
          <h1>
            실시간 시장에서,
            <br />
            <span>손실 부담 없이</span> 거래하세요.
          </h1>
          <p className={styles.heroDescription}>
            실제 비트코인 시장 데이터를 보며 선물거래를 연습하세요.
            <br className={styles.desktopBreak} /> 가상 자금으로 주문부터 포지션 관리까지 직접 경험할 수 있습니다.
          </p>

          <div className={styles.heroActions}>
            <Link className={styles.primaryCta} to="/trade">
              무료로 거래 시작하기
              <span aria-hidden="true">→</span>
            </Link>
            <a className={styles.secondaryCta} href="#features">
              기능 살펴보기
            </a>
          </div>

          <div className={styles.proofRow} aria-label="서비스 특징">
            <span><b>10,000 USDT</b> 가상 자금</span>
            <span><b>LIVE</b> 실시간 호가</span>
            <span><b>0원</b> 실제 자금 불필요</span>
          </div>
        </section>

        <section className={styles.previewSection} aria-label="거래 화면 미리보기">
          <div className={styles.previewFrame}>
            <div className={styles.previewBar}>
              <div className={styles.windowDots} aria-hidden="true"><span /><span /><span /></div>
              <span className={styles.previewAddress}>btc-paper.app/trade</span>
              <span className={styles.previewStatus}><i /> MARKET LIVE</span>
            </div>
            <img
              className={styles.previewImage}
              src={tradingPreview}
              alt="BTCUSDT 실시간 차트와 호가창이 표시된 모의 선물거래 화면"
            />
          </div>
        </section>

        <section className={styles.features} id="features">
          <div className={styles.sectionHeading}>
            <p className={styles.sectionLabel}>WHY BTC PAPER</p>
            <h2>연습에 필요한 기능을<br />한 화면에 담았습니다.</h2>
            <p>복잡한 거래소 기능 중 학습에 필요한 흐름에 집중했습니다.</p>
          </div>

          <div className={styles.featureGrid}>
            {features.map((feature) => (
              <article className={styles.featureCard} key={feature.number}>
                <span className={styles.cardNumber}>{feature.number}</span>
                <div className={styles.cardIcon} aria-hidden="true">
                  {feature.number === '01' && <span className={styles.chartIcon}><i /><i /><i /><i /></span>}
                  {feature.number === '02' && <span className={styles.orderIcon}><i /><i /><i /></span>}
                  {feature.number === '03' && <span className={styles.leverageIcon}>50×</span>}
                </div>
                <h3>{feature.title}</h3>
                <p>{feature.description}</p>
              </article>
            ))}
          </div>
        </section>

        <section className={styles.howSection} id="how-it-works">
          <div className={styles.howIntro}>
            <p className={styles.sectionLabel}>HOW IT WORKS</p>
            <h2>가입하고, 분석하고,<br />직접 거래해 보세요.</h2>
            <p>실제 자금이나 복잡한 준비 없이 몇 분 안에 시작할 수 있습니다.</p>
          </div>

          <ol className={styles.stepList}>
            {steps.map((step) => (
              <li key={step.number}>
                <span>{step.number}</span>
                <div>
                  <h3>{step.title}</h3>
                  <p>{step.description}</p>
                </div>
              </li>
            ))}
          </ol>
        </section>

        <section className={styles.finalCta}>
          <div className={styles.ctaGlow} aria-hidden="true" />
          <BrandMark />
          <p className={styles.sectionLabel}>READY TO PRACTICE?</p>
          <h2>시장에 들어갈 준비가 되셨나요?</h2>
          <p>10,000 USDT 가상 자금으로 첫 거래를 시작해 보세요.</p>
          <Link className={styles.primaryCta} to="/trade">
            거래 화면으로 이동
            <span aria-hidden="true">→</span>
          </Link>
        </section>
      </main>

      <footer className={styles.footer}>
        <div className={styles.footerBrand}>
          <BrandMark />
          <span>BTC PAPER</span>
        </div>
        <p>본 서비스는 학습용 모의거래 서비스이며 실제 자금 거래를 제공하지 않습니다.</p>
        <span>© 2026 BTC Paper</span>
      </footer>
    </div>
  );
}
