import { useCallback, useState } from 'react';

import type { CreateOrderInput, OrderSide, OrderType } from '@/types/paper';

export function useOrderForm() {
  const [orderType, setOrderTypeState] = useState<OrderType>('MARKET');
  const [quantity, setQuantityState] = useState('');
  const [limitPrice, setLimitPriceState] = useState('');
  const [error, setError] = useState<string | null>(null);

  const setOrderType = useCallback((value: OrderType) => {
    setOrderTypeState(value);
    setError(null);
  }, []);

  const setQuantity = useCallback((value: string) => {
    setQuantityState(value.replace(',', '.'));
    setError(null);
  }, []);

  const setLimitPrice = useCallback((value: string) => {
    setLimitPriceState(value.replace(',', '.'));
    setError(null);
  }, []);

  const selectLimitPrice = useCallback((price: number) => {
    setOrderTypeState('LIMIT');
    setLimitPriceState(String(price));
    setError(null);
  }, []);

  const buildOrder = useCallback(
    (side: OrderSide): CreateOrderInput | null => {
      const numericQuantity = Number(quantity);
      if (
        !quantity.trim() ||
        !Number.isFinite(numericQuantity) ||
        numericQuantity <= 0
      ) {
        setError('0보다 큰 주문 수량을 입력해 주세요.');
        return null;
      }

      if (orderType === 'LIMIT') {
        const numericLimitPrice = Number(limitPrice);
        if (
          !limitPrice.trim() ||
          !Number.isFinite(numericLimitPrice) ||
          numericLimitPrice <= 0
        ) {
          setError('지정가 주문에는 0보다 큰 주문 가격이 필요합니다.');
          return null;
        }
        return {
          side,
          type: orderType,
          quantity: numericQuantity,
          limitPrice: numericLimitPrice,
        };
      }

      return { side, type: orderType, quantity: numericQuantity };
    },
    [limitPrice, orderType, quantity],
  );

  const clearAfterSubmit = useCallback(() => {
    setQuantityState('');
    setError(null);
  }, []);

  return {
    orderType,
    quantity,
    limitPrice,
    error,
    setOrderType,
    setQuantity,
    setLimitPrice,
    selectLimitPrice,
    buildOrder,
    clearAfterSubmit,
  };
}

export type OrderFormController = ReturnType<typeof useOrderForm>;
