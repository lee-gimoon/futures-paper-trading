/** 공개 화면에서 개인 데이터가 필요한 영역에만 로그인 동작을 제공한다. */
import { router } from 'expo-router';
import { StyleSheet, Text, View } from 'react-native';
import { AppButton } from '@/components/AppButton';
import { useAuth } from '@/features/auth/useAuth';
import { colors } from '@/theme/colors';

export function SignInNotice({
  message = '로그인하면 포지션과 주문을 확인할 수 있습니다.',
  returnTo = '/trade',
}: {
  message?: string;
  returnTo?: '/trade' | '/orders' | '/account';
}) {
  const { status } = useAuth();
  return (
    <View style={styles.box}>
      <Text style={styles.text}>
        {status === 'loading' ? '로그인 상태를 확인하고 있습니다.' : message}
      </Text>
      <AppButton
        label="로그인 / 회원가입"
        disabled={status === 'loading'}
        onPress={() =>
          router.push({ pathname: '/login', params: { returnTo } })
        }
        variant="primary"
      />
    </View>
  );
}
const styles = StyleSheet.create({
  box: { padding: 22, gap: 18 },
  text: {
    color: colors.textSecondary,
    fontSize: 13,
    lineHeight: 21,
    textAlign: 'center',
  },
});
