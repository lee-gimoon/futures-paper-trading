/**
 * Spring의 `text/event-stream` 응답을 바이트 단위로 읽어 호가 JSON 이벤트로 변환한다.
 * fetch 응답은 연결을 유지하며 여러 조각으로 도착하므로 미완성 문자열은 buffer에 보관한다.
 */

import { fetch } from 'expo/fetch';

import { apiUrl } from '@/api/config';
import { HttpError } from '@/api/client';
import type { OrderBookLevel, OrderBookSnapshot } from '@/types/market';

type DepthStreamOptions = {
  signal: AbortSignal;
  onOpen: () => void;
  onSnapshot: (snapshot: OrderBookSnapshot) => void;
};

function isLevel(value: unknown): value is OrderBookLevel {
  return (
    typeof value === 'object' &&
    value !== null &&
    'price' in value &&
    typeof value.price === 'number' &&
    'quantity' in value &&
    typeof value.quantity === 'number'
  );
}

function isSnapshot(value: unknown): value is OrderBookSnapshot {
  return (
    typeof value === 'object' &&
    value !== null &&
    'symbol' in value &&
    typeof value.symbol === 'string' &&
    'eventTime' in value &&
    typeof value.eventTime === 'number' &&
    'bids' in value &&
    Array.isArray(value.bids) &&
    value.bids.every(isLevel) &&
    'asks' in value &&
    Array.isArray(value.asks) &&
    value.asks.every(isLevel)
  );
}

function readEvent(block: string): OrderBookSnapshot | null {
  const data = block
    .split('\n')
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n');

  if (!data) return null;

  try {
    const parsed: unknown = JSON.parse(data);
    return isSnapshot(parsed) ? parsed : null;
  } catch {
    // 이벤트 하나가 깨져도 다음 SSE 이벤트는 계속 받을 수 있게 건너뛴다.
    return null;
  }
}

export async function consumeDepthStream({
  signal,
  onOpen,
  onSnapshot,
}: DepthStreamOptions): Promise<void> {
  const response = await fetch(
    apiUrl('/api/binance-futures/btcusdt/depth/stream'),
    {
      headers: { Accept: 'text/event-stream' },
      signal,
    },
  );

  if (!response.ok) {
    throw new HttpError(
      response.status,
      '실시간 호가 연결을 시작하지 못했습니다.',
    );
  }
  if (!response.body) throw new Error('실시간 호가 응답 스트림이 없습니다.');

  onOpen();

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  try {
    while (!signal.aborted) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n');
      const blocks = buffer.split('\n\n');
      buffer = blocks.pop() ?? '';

      for (const block of blocks) {
        const snapshot = readEvent(block);
        if (snapshot) onSnapshot(snapshot);
      }
    }
  } finally {
    reader.releaseLock();
  }
}
