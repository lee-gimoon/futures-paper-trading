/**
 * 이 파일은 기본 동작, 주문 방식, 매수와 매도에 공통으로 사용할 버튼 컴포넌트를 만든다.
 * 버튼마다 Pressable과 스타일을 반복하지 않고, 부모가 props로 전달한 값에 따라
 * 문구·색상·선택 상태와 눌렀을 때 실행할 동작을 바꾼다.
 */

/*
 * Pressable: 사용자가 누를 수 있는 영역을 만드는 React Native 컴포넌트다.
 * Text: 버튼 문구를 표시한다.
 * StyleSheet: 버튼의 공통 스타일과 종류별 스타일 묶음을 만든다.
 * StyleProp<ViewStyle>: 부모가 전달할 수 있는 View 스타일의 TypeScript 타입이다.
 */
import {
  Pressable,
  StyleProp,
  StyleSheet,
  Text,
  ViewStyle,
} from 'react-native';

// 버튼 색상도 화면과 같은 공통 테마 값을 사용한다.
import { colors } from '@/theme/colors';

// variant는 이 버튼이 기본 버튼, 선택 버튼, 매수 버튼, 매도 버튼 중 어떤 모양인지 제한한다.
type AppButtonVariant = 'primary' | 'choice' | 'buy' | 'sell';

// 부모 컴포넌트가 AppButton에 전달할 수 있는 props의 이름과 타입이다.
type AppButtonProps = {
  // 버튼 안에 표시할 문구다.
  label: string;
  // 사용자가 버튼을 누르면 부모가 전달한 이 함수를 실행한다.
  onPress: () => void;
  // 시장가/지정가처럼 현재 선택됐는지 나타내며, 생략하면 false다.
  selected?: boolean;
  // 부모 화면이 버튼 너비 같은 추가 배치 스타일을 전달할 때 사용한다.
  style?: StyleProp<ViewStyle>;
  // 버튼의 색상 종류이며, 생략하면 choice를 사용한다.
  variant?: AppButtonVariant;
  // true면 Pressable이 터치를 받지 않고 비활성 모양으로 표시된다.
  disabled?: boolean;
};

// 구조 분해로 props를 꺼내고 선택 여부와 종류에 기본값을 지정한다.
export function AppButton({
  label,
  onPress,
  selected = false,
  style,
  variant = 'choice',
  disabled = false,
}: AppButtonProps) {
  return (
    /*
     * accessibilityRole과 accessibilityState는 보조 기술에 버튼과 선택 상태를 알려 준다.
     * style 함수의 pressed는 사용자가 버튼을 누르고 있는 동안 true가 된다.
     */
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled, selected }}
      disabled={disabled}
      // 부모에게 받은 onPress 함수를 Pressable의 onPress 속성에 연결한다.
      // 사용자가 버튼을 누르면 Pressable이 이 함수를 호출해 부모가 정한 동작을 실행한다.
      onPress={onPress}
      // 여기서 pressed는 Pressable이 style 함수를 호출할 때 인자로 제공하는 기본 눌림 상태값이며, 버튼을 누르는 동안 true가 된다.
      style={({ pressed }) => [
        // 공통 스타일 뒤에 종류·상태·부모 스타일을 순서대로 추가한다.
        // 배열 뒤쪽의 스타일이 앞쪽과 같은 속성을 가지면 뒤쪽 값이 적용된다.
        styles.button,
        variant === 'primary' && styles.primaryButton,
        variant === 'choice' && styles.choiceButton,
        variant === 'buy' && styles.buyButton,
        variant === 'sell' && styles.sellButton,
        selected && styles.selectedButton,
        pressed && !disabled && styles.pressedButton,
        disabled && styles.disabledButton,
        style,
      ]}
    >
      {/* label prop으로 받은 문자열을 버튼 안에 표시한다. */}
      <Text
        style={[
          styles.label,
          variant === 'primary' && styles.primaryLabel,
          variant === 'choice' && styles.choiceLabel,
          selected && styles.selectedLabel,
        ]}
      >
        {/* label은 부모가 AppButton의 label prop으로 전달한 버튼 문구다. */}
        {/* JSX의 중괄호 안에는 JavaScript/TypeScript 표현식을 작성할 수 있으며, 여기서는 label prop 값을 글자로 표시한다. */}
        {label}
      </Text>
    </Pressable>
  );
}

// 모든 버튼의 공통 모양과 variant/selected/pressed 상태별 차이를 분리해 둔다.
const styles = StyleSheet.create({
  button: {
    // 버튼 내부 문구를 가로·세로 가운데에 놓는다.
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 48,
    paddingHorizontal: 16,
    borderWidth: 1,
    borderRadius: 8,
  },
  // 화면의 주요 동작 버튼은 공통 강조색을 배경으로 사용한다.
  primaryButton: {
    borderColor: colors.accent,
    backgroundColor: colors.accent,
  },
  // 시장가/지정가처럼 선택 가능한 중립 버튼의 기본 모양이다.
  choiceButton: {
    borderColor: colors.border,
    backgroundColor: colors.surfaceMuted,
  },
  // 매수와 매도 버튼은 의미를 빠르게 구분하도록 서로 다른 색상을 사용한다.
  buyButton: {
    borderColor: colors.buy,
    backgroundColor: colors.buy,
  },
  sellButton: {
    borderColor: colors.sell,
    backgroundColor: colors.sell,
  },
  // 선택된 주문 방식은 공통 강조색의 테두리와 배경으로 구분한다.
  selectedButton: {
    borderColor: colors.accent,
    backgroundColor: colors.accentMuted,
  },
  // 누르고 있는 동안 투명도를 낮춰 터치에 반응했다는 것을 보여 준다.
  pressedButton: {
    opacity: 0.72,
  },
  disabledButton: {
    opacity: 0.42,
  },
  label: {
    color: colors.buttonText,
    fontSize: 15,
    fontWeight: '800',
  },
  choiceLabel: {
    color: colors.textSecondary,
  },
  selectedLabel: {
    color: colors.accentText,
  },
  primaryLabel: { color: colors.onAccent },
});
