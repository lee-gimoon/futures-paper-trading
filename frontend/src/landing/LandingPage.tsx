import { Link } from 'react-router-dom';

import heroBackground from '../assets/landing/hero-background-4k.webp';
import styles from './LandingPage.module.css';

// 코인·산맥·차트는 장식용 이미지로 표시하고, 제목·버튼·안내문은 실제 React 요소로 렌더링한다.
export default function LandingPage() {
  return (
    <main className={styles.page}>
      <img
        className={styles.background}
        src={heroBackground}
        alt=""
        aria-hidden="true"
        decoding="async"
        fetchPriority="high"
      />
      <div className={styles.scrim} aria-hidden="true" />

      <header className={styles.header}>
        <Link className={styles.brand} to="/" aria-label="BTC Paper Futures 홈">
          <span>BTC</span> PAPER FUTURES
        </Link>

        <Link className={styles.tradeButton} to="/trade">
          거래하러 가기
        </Link>
      </header>

      <footer className={styles.footer}>
        <p>
          본 서비스는 학습용 모의거래 사이트이며 실제 돈은 사용되지 않습니다.
          <span> 모든 거래는 실제 가치가 없는 연습용 포인트로 진행됩니다.</span>
        </p>
      </footer>
    </main>
  );
}
