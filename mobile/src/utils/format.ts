const priceFormatter = new Intl.NumberFormat('ko-KR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const quantityFormatter = new Intl.NumberFormat('ko-KR', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 6,
});

export function formatPrice(value: number | null | undefined): string {
  return value == null || !Number.isFinite(value)
    ? '—'
    : priceFormatter.format(value);
}

// 저가 코인도 가격 차이를 읽을 수 있도록 종목 가격에는 자릿수를 더 허용한다.
export function formatMarketPrice(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) return '—';
  const digits = value < 1 ? 5 : value < 10 ? 4 : 2;
  return value.toLocaleString('en-US', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  });
}

export function formatVolume(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) return '—';
  return value >= 1e9
    ? (value / 1e9).toFixed(2) + 'B'
    : value >= 1e6
      ? (value / 1e6).toFixed(2) + 'M'
      : value.toLocaleString('en-US', { maximumFractionDigits: 0 });
}

export function formatQuantity(value: number | null | undefined): string {
  return value == null || !Number.isFinite(value)
    ? '—'
    : quantityFormatter.format(value);
}

export function formatPnl(value: number | null | undefined): string {
  if (value == null || !Number.isFinite(value)) return '—';
  const prefix = value > 0 ? '+' : '';
  return `${prefix}${priceFormatter.format(value)}`;
}

export function sideLabel(side: 'BUY' | 'SELL' | 'LONG' | 'SHORT'): string {
  return {
    BUY: '매수',
    SELL: '매도',
    LONG: '롱',
    SHORT: '숏',
  }[side];
}

export function orderStatusLabel(status: string): string {
  return (
    {
      NEW: '접수',
      OPEN: '대기',
      FILLED: '체결',
      CANCELED: '취소',
      REJECTED: '거절',
    }[status] ?? status
  );
}
