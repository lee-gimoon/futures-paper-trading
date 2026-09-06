import { useCallback, useRef, useState } from 'react';

import { createOrder } from '@/api/paperApi';
import type { CreateOrderInput, Order } from '@/types/paper';

export function useSubmitOrder() {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const submittingRef = useRef(false);

  const submit = useCallback(
    async (input: CreateOrderInput): Promise<Order | null> => {
      // React가 버튼 비활성 상태를 다시 그리기 전의 매우 빠른 연속 터치도 즉시 막는다.
      if (submittingRef.current) return null;
      submittingRef.current = true;
      setSubmitting(true);
      setError(null);
      try {
        return await createOrder(input);
      } catch (caught) {
        setError(
          caught instanceof Error
            ? caught.message
            : '주문을 처리하지 못했습니다.',
        );
        return null;
      } finally {
        submittingRef.current = false;
        setSubmitting(false);
      }
    },
    [],
  );

  const clearError = useCallback(() => setError(null), []);
  return { submitting, error, submit, clearError };
}
