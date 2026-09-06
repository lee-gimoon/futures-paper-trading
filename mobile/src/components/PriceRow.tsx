/**
 * 이 파일은 호가 목록의 가격 한 줄을 표시하는 재사용 컴포넌트다.
 * 부모인 TradeScreen이 가격·수량·매수/매도 호가 구분을 props로 전달하면
 * PriceRow는 전달받은 값과 방향에 맞는 색상만 화면에 표시한다.
 */

// View는 가격과 수량을 한 행으로 묶고, Text는 각 값을 글자로 표시한다.
// StyleSheet는 행의 Flexbox 배치와 매수·매도 가격 색상을 정의한다.
import { StyleSheet, Text, View } from 'react-native';

// 여러 화면에서 매수·매도 의미가 같은 색상을 사용하도록 공통 테마를 가져온다.
import { colors } from '@/theme/colors';

// ask는 매도 호가, bid는 매수 호가를 뜻하며 다른 문자열은 사용할 수 없다.
export type PriceSide = 'ask' | 'bid';

// PriceRow를 사용할 부모가 반드시 전달해야 하는 props의 모양이다.
type PriceRowProps = {
  price: string;
  quantity: string;
  // 해당 행이 매도 호가(ask)인지 매수 호가(bid)인지 구분한다.
  side: PriceSide;
};

// 구조 분해로 props의 가격, 수량과 방향을 각각 꺼낸다.
export function PriceRow({ price, quantity, side }: PriceRowProps) {
  // 화면 읽기 기능이 ask/bid 대신 사용자가 이해할 수 있는 매도/매수로 읽게 한다.
  const sideLabel = side === 'ask' ? '매도' : '매수';

  return (
    // accessibilityLabel은 가격 한 줄의 의미를 보조 기술에 하나의 문장으로 전달한다.
    <View
      accessibilityLabel={`${sideLabel} 호가 ${price} USDT, 수량 ${quantity} BTC`}
      style={styles.row}
    >
      {/* 삼항 연산자로 매도호가는 빨강, 매수호가는 초록 스타일을 선택한다. */}
      <Text style={[styles.value, side === 'ask' ? styles.askPrice : styles.bidPrice]}>
        {price}
      </Text>
      {/* 수량은 매수/매도 호가 구분과 관계없이 같은 보조 글자색을 사용한다. */}
      <Text style={[styles.value, styles.quantity]}>{quantity}</Text>
    </View>
  );
}

// 호가 한 줄의 배치와 값 종류별 글자 모양을 이름별로 정의한다.
const styles = StyleSheet.create({
  row: {
    // 가격과 수량을 가로로 놓고 각각 행의 양쪽 끝에 배치한다.
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    minHeight: 38,
    paddingHorizontal: 4,
  },
  value: {
    // tabular-nums는 숫자마다 같은 너비를 사용해 여러 가격 행의 자릿수를 맞춘다.
    fontSize: 14,
    fontVariant: ['tabular-nums'],
    fontWeight: '700',
  },
  askPrice: {
    color: colors.sellText,
  },
  bidPrice: {
    color: colors.buyText,
  },
  quantity: {
    color: colors.textSecondary,
    fontWeight: '600',
  },
});
