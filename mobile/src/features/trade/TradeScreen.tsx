/**
 * 이 파일은 거래 화면 전체를 담당한다.
 * 주문 방식과 수량 같은 화면 상태를 관리하고, 제목·주문 입력·호가 목록을 조합한다.
 * 아직 Spring 서버에는 연결하지 않으며 버튼을 누르면 로컬 결과 메시지만 보여 준다.
 * 이 화면에서 함께 사용하는 주문 방식·수량·결과 메시지 상태는 이 컴포넌트가 `useState`로 관리한다.
 * AppButton과 PriceRow는 자체 화면 상태를 관리하지 않고, 부모가 props로 전달한 값을 표시하거나 콜백을 실행한다.
 */

// useState는 선택한 주문 방식, 입력 수량처럼 화면 안에서 바뀌는 값을 기억하는 React Hook이다.
import { useState } from 'react';

/*
 * 아래 항목은 모두 `react-native`가 제공한다.
 *
 * FlatList: 배열 데이터를 필요한 항목만 효율적으로 렌더링하는 목록 컴포넌트다.
 * KeyboardAvoidingView: 키보드가 열릴 때 입력 영역이 가려지지 않도록 사용 가능한 높이를 조정한다.
 * Platform: 현재 앱이 Android와 iOS 중 어디에서 실행되는지 알려 주는 API 객체다.
 * ScrollView: 화면보다 긴 내용을 손가락으로 스크롤할 수 있게 한다.
 * StyleSheet: 색상·간격·Flexbox 배치 같은 스타일 묶음을 만든다.
 * Text: 글자를 표시하고, TextInput은 사용자가 글자나 숫자를 입력하게 한다.
 * View: 다른 컴포넌트를 묶고 배치하는 UI 컨테이너이며 웹의 div와 비슷하다.
 */
import {
  FlatList,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';

// SafeAreaView는 화면 내용이 상태 표시줄, 카메라 구멍과 홈 표시 영역에 겹치지 않게 한다.
import { SafeAreaView } from 'react-native-safe-area-context';

// 완성된 화면 안에서 반복해서 사용하는 버튼과 가격 행을 별도 컴포넌트에서 가져온다.
import { AppButton } from '@/components/AppButton';
import { PriceRow } from '@/components/PriceRow';

// `import type`은 실행할 값이 아니라 TypeScript 검사에만 사용할 타입 정보를 가져온다.
import type { PriceSide } from '@/components/PriceRow';

// 반복되는 색상값은 직접 작성하지 않고 공통 테마에서 가져온다.
import { colors } from '@/theme/colors';

// 주문 방식과 방향에 허용되는 문자열을 TypeScript 타입으로 제한한다.
type OrderType = 'market' | 'limit';
type OrderSide = 'buy' | 'sell';

// FlatList의 항목 하나가 가져야 하는 데이터 모양이다.
type OrderBookItem = {
  id: string;
  price: string;
  quantity: string;
  side: PriceSide;
};

// 이 화면은 API를 호출하지 않으므로 현재가는 로컬 고정 값으로 사용한다.
const CURRENT_PRICE = '104,280.50';

// 서버 대신 로컬에 정의한 고정 호가를 화면에 표시한다.
const ORDER_BOOK: OrderBookItem[] = [
  { id: 'ask-3', price: '104,310.00', quantity: '0.084', side: 'ask' },
  { id: 'ask-2', price: '104,300.50', quantity: '0.126', side: 'ask' },
  { id: 'ask-1', price: '104,291.20', quantity: '0.218', side: 'ask' },
  { id: 'bid-1', price: '104,280.50', quantity: '0.194', side: 'bid' },
  { id: 'bid-2', price: '104,271.80', quantity: '0.311', side: 'bid' },
  { id: 'bid-3', price: '104,260.10', quantity: '0.152', side: 'bid' },
];

// index.tsx의 HomeScreen이 자식으로 렌더링하는 거래 화면 컴포넌트다.
export function TradeScreen() {
  // orderType은 현재 선택한 시장가/지정가를 보관한다. 처음에는 시장가가 선택된다.
  const [orderType, setOrderType] = useState<OrderType>('market');
  // quantity는 TextInput에 입력한 문자열을 그대로 보관한다.
  const [quantity, setQuantity] = useState('');
  // message가 빈 문자열이 아니면 주문 버튼 아래에 결과 안내를 표시한다.
  const [message, setMessage] = useState('');

  // 매수 또는 매도 버튼을 눌렀을 때 입력을 검사하고 로컬 준비 메시지를 만든다.
  // 이 화면은 로컬 입력 상태만 관리하므로 서버에 주문을 전송하지 않는다.
  const prepareOrder = (side: OrderSide) => {
    // TextInput 값은 문자열이므로 크기를 검사할 수 있도록 숫자로 변환한다.
    const numericQuantity = Number(quantity);

    // 빈 값, 숫자가 아닌 값, 0 이하는 주문 수량으로 사용할 수 없다.
    if (!quantity.trim() || !Number.isFinite(numericQuantity) || numericQuantity <= 0) {
      setMessage('0보다 큰 주문 수량을 입력해 주세요.');
      return;
    }

    const sideLabel = side === 'buy' ? '매수' : '매도';
    const orderTypeLabel = orderType === 'market' ? '시장가' : '지정가';
    // setMessage가 상태를 바꾸면 React가 이 컴포넌트를 다시 렌더링해 새 메시지를 표시한다.
    setMessage(`${sideLabel} ${orderTypeLabel} 주문을 준비했습니다. (${quantity} BTC)`);
  };

  return (
    // SafeAreaView가 전체 화면의 가장 바깥쪽에서 휴대폰의 안전 영역을 적용한다.
    <SafeAreaView style={styles.safeArea}>
      {/* 키보드가 열리면 사용할 수 있는 화면 높이에 맞춰 입력 영역을 줄인다. */}
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.keyboardArea}
      >
        <View style={styles.screen}>
          {/* 입력 영역만 스크롤되므로 작은 화면이나 열린 키보드 뒤에 버튼이 가려지지 않는다. */}
          <ScrollView
            contentContainerStyle={styles.formContent}
            keyboardDismissMode="on-drag"
            keyboardShouldPersistTaps="handled"
            showsVerticalScrollIndicator={false}
            style={styles.formScroll}
          >
            {/* 종목명과 이 시세가 로컬 데이터라는 표시를 가로로 배치한다. */}
            <View style={styles.titleRow}>
              <View>
                <Text style={styles.eyebrow}>무기한 선물 · 모의투자</Text>
                <Text style={styles.symbol}>BTCUSDT</Text>
              </View>
              <View style={styles.liveBadge}>
                <View style={styles.liveDot} />
                <Text style={styles.liveText}>로컬 시세</Text>
              </View>
            </View>

            {/* 현재가는 아직 API 값이 아니라 위에서 선언한 CURRENT_PRICE를 표시한다. */}
            <View style={styles.priceCard}>
              <Text style={styles.priceLabel}>현재가</Text>
              <Text style={styles.currentPrice}>{CURRENT_PRICE}</Text>
              <Text style={styles.currency}>USDT</Text>
            </View>

            <View style={styles.section}>
              <Text style={styles.sectionLabel}>주문 방식</Text>
              <View style={styles.buttonRow}>
                {/* 시장가와 지정가 AppButton 컴포넌트는 항상 함께 렌더링된다.
                    orderType과 일치하는 AppButton만 selected가 true가 되어 선택된 모양으로 표시된다. */}
                <AppButton
                  label="시장가"
                  onPress={() => setOrderType('market')}
                  selected={orderType === 'market'}
                  style={styles.flexButton}
                  variant="choice"
                />
                <AppButton
                  label="지정가"
                  onPress={() => setOrderType('limit')}
                  selected={orderType === 'limit'}
                  style={styles.flexButton}
                  variant="choice"
                />
              </View>
            </View>

            {/* TextInput의 입력값과 quantity 상태를 value/onChangeText로 서로 연결한다. */}
            <View style={styles.section}>
              <Text style={styles.sectionLabel}>수량</Text>
              <View style={styles.inputShell}>
                <TextInput
                  accessibilityLabel="주문 수량"
                  keyboardType="decimal-pad"
                  onChangeText={(value) => {
                    // 입력할 때마다 최신 문자열을 저장하고 이전 결과 메시지는 지운다.
                    setQuantity(value);
                    setMessage('');
                  }}
                  placeholder="0.001"
                  placeholderTextColor={colors.textMuted}
                  returnKeyType="done"
                  style={styles.input}
                  value={quantity}
                />
                <Text style={styles.inputUnit}>BTC</Text>
              </View>
            </View>

            {/* 같은 AppButton에 서로 다른 variant와 동작을 props로 전달해 매수·매도 버튼을 만든다. */}
            <View style={styles.buttonRow}>
              <AppButton
                label="매수"
                onPress={() => prepareOrder('buy')}
                style={styles.flexButton}
                variant="buy"
              />
              <AppButton
                label="매도"
                onPress={() => prepareOrder('sell')}
                style={styles.flexButton}
                variant="sell"
              />
            </View>

            {/* message가 있을 때만 안내 영역을 렌더링하는 조건부 JSX다. */}
            {message ? (
              <View accessibilityLiveRegion="polite" style={styles.messageBox}>
                <Text style={styles.messageText}>{message}</Text>
              </View>
            ) : null}
          </ScrollView>

          {/* 주문 입력과 별도로 호가 제목, 열 이름과 FlatList를 묶는 영역이다. */}
          <View style={styles.orderBookSection}>
            <View style={styles.orderBookTitleRow}>
              <Text style={styles.orderBookTitle}>호가</Text>
              <Text style={styles.orderBookCaption}>서버 연결 전 고정 데이터</Text>
            </View>
            <View style={styles.columnHeader}>
              <Text style={styles.columnLabel}>가격(USDT)</Text>
              <Text style={styles.columnLabel}>수량(BTC)</Text>
            </View>
            {/* FlatList는 호가 데이터가 많아져도 현재 보이는 영역과 주변 항목을 중심으로 렌더링해 성능을 관리한다.
                사용자가 목록을 스크롤하면 다음 호가 행을 추가로 렌더링한다. */}
            <FlatList
              // 목록에서 사용할 전체 데이터 배열이다.
              data={ORDER_BOOK}
              // 각 행 사이에 표시할 구분선 컴포넌트다.
              ItemSeparatorComponent={() => <View style={styles.separator} />}
              // 키보드가 열린 상태에서 목록을 터치했을 때의 처리 방식을 정한다.
              keyboardShouldPersistTaps="handled"
              // 각 데이터 항목에서 React가 사용할 고유 key를 꺼낸다.
              keyExtractor={(item) => item.id}
              // FlatList가 data 배열의 각 항목을 하나씩 꺼내 renderItem 함수의 item 매개변수로 전달한다.
              // renderItem은 전달받은 item을 PriceRow의 props로 넘겨 호가 한 행을 만든다.
              renderItem={({ item }) => (
                <PriceRow price={item.price} quantity={item.quantity} side={item.side} />
              )}
              // 세로 스크롤 표시줄을 보여 줄지 결정한다.
              showsVerticalScrollIndicator={false}
              // FlatList 전체 컴포넌트에 적용할 스타일이다.
              style={styles.orderBookList}
            />
          </View>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

// 위 JSX에서 참조하는 색상·크기·간격과 Flexbox 배치 규칙을 이름별로 만든다.
const styles = StyleSheet.create({
  // 가장 바깥 두 영역은 flex: 1로 사용 가능한 화면 높이를 모두 채운다.
  safeArea: {
    flex: 1,
    backgroundColor: colors.background,
  },
  keyboardArea: {
    flex: 1,
  },
  screen: {
    flex: 1,
    paddingHorizontal: 20,
  },
  // 입력 영역과 호가 영역이 화면 높이를 나눠 가지며, 입력 내용이 길면 ScrollView가 스크롤한다.
  formScroll: {
    flex: 1.15,
  },
  formContent: {
    paddingTop: 16,
    paddingBottom: 20,
  },
  // flexDirection: 'row'는 자식들을 가로로, space-between은 양 끝으로 배치한다.
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 16,
  },
  eyebrow: {
    color: colors.textSecondary,
    fontSize: 12,
    fontWeight: '600',
  },
  symbol: {
    marginTop: 4,
    color: colors.textPrimary,
    fontSize: 28,
    fontWeight: '800',
  },
  liveBadge: {
    // 점과 글자를 가로로 나란히 놓는다.
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
    paddingHorizontal: 10,
    paddingVertical: 7,
    borderRadius: 999,
    backgroundColor: colors.surfaceMuted,
  },
  liveDot: {
    width: 7,
    height: 7,
    borderRadius: 999,
    backgroundColor: colors.accent,
  },
  liveText: {
    color: colors.textSecondary,
    fontSize: 11,
    fontWeight: '700',
  },
  priceCard: {
    // 현재가 이름, 가격과 단위를 한 줄에 배치한다.
    flexDirection: 'row',
    alignItems: 'baseline',
    marginTop: 20,
    padding: 18,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 16,
    backgroundColor: colors.surface,
  },
  priceLabel: {
    marginRight: 12,
    color: colors.textSecondary,
    fontSize: 13,
  },
  currentPrice: {
    flex: 1,
    color: colors.textPrimary,
    fontSize: 25,
    fontVariant: ['tabular-nums'],
    fontWeight: '800',
  },
  currency: {
    color: colors.textMuted,
    fontSize: 11,
    fontWeight: '700',
  },
  section: {
    marginTop: 18,
  },
  sectionLabel: {
    marginBottom: 9,
    color: colors.textSecondary,
    fontSize: 13,
    fontWeight: '700',
  },
  buttonRow: {
    // 두 개의 버튼을 가로로 배치하고 버튼 사이에 10만큼 간격을 둔다.
    flexDirection: 'row',
    gap: 10,
  },
  flexButton: {
    // 같은 행의 각 버튼이 남은 너비를 동일한 비율로 나눠 가진다.
    flex: 1,
  },
  inputShell: {
    // TextInput과 BTC 단위를 하나의 입력 상자 안에 가로로 배치한다.
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: colors.borderStrong,
    borderRadius: 14,
    backgroundColor: colors.input,
  },
  input: {
    flex: 1,
    paddingHorizontal: 15,
    paddingVertical: 14,
    color: colors.textPrimary,
    fontSize: 17,
    fontVariant: ['tabular-nums'],
  },
  inputUnit: {
    paddingRight: 15,
    color: colors.textSecondary,
    fontSize: 12,
    fontWeight: '700',
  },
  messageBox: {
    marginTop: 13,
    paddingHorizontal: 14,
    paddingVertical: 11,
    borderWidth: 1,
    borderColor: colors.accentBorder,
    borderRadius: 12,
    backgroundColor: colors.accentMuted,
  },
  messageText: {
    color: colors.accentText,
    fontSize: 13,
    lineHeight: 19,
    textAlign: 'center',
  },
  orderBookSection: {
    // 남은 화면 높이를 사용하되 작은 화면에서도 최소 190 높이의 목록 영역을 확보한다.
    flex: 1,
    minHeight: 190,
    paddingTop: 16,
    borderTopWidth: 1,
    borderTopColor: colors.border,
  },
  orderBookTitleRow: {
    // 호가 제목은 왼쪽, 고정 데이터 설명은 오른쪽에 배치한다.
    flexDirection: 'row',
    alignItems: 'baseline',
    justifyContent: 'space-between',
    gap: 12,
  },
  orderBookTitle: {
    color: colors.textPrimary,
    fontSize: 17,
    fontWeight: '800',
  },
  orderBookCaption: {
    color: colors.textMuted,
    fontSize: 11,
  },
  columnHeader: {
    // 가격과 수량 열 이름을 각각 목록의 양쪽 끝에 배치한다.
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingTop: 12,
    paddingBottom: 8,
  },
  columnLabel: {
    color: colors.textMuted,
    fontSize: 11,
    fontWeight: '600',
  },
  orderBookList: {
    flex: 1,
  },
  separator: {
    height: 1,
    backgroundColor: colors.divider,
  },
});
