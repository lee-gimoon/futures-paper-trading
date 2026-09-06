/** 회원가입 입력을 검사해 Spring 서버에 전송하고, 성공하면 로그인 상태로 전환한다. */

import { useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { AppButton } from '@/components/AppButton';
import { useAuth } from '@/features/auth/useAuth';
import { authReturn } from '@/features/auth/authReturn';
import { colors } from '@/theme/colors';

export function SignupScreen() {
  const params = useLocalSearchParams<{ returnTo?: string }>();
  const { clearError, signup } = useAuth();
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [message, setMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const submit = async () => {
    if (submitting) return;
    const normalizedEmail = email.trim();
    if (!normalizedEmail || !password || !passwordConfirm) {
      setMessage('이메일과 비밀번호를 모두 입력해 주세요.');
      return;
    }
    if (password.length < 8) {
      setMessage('비밀번호는 8자 이상 입력해 주세요.');
      return;
    }
    if (password !== passwordConfirm) {
      setMessage('두 비밀번호가 서로 다릅니다.');
      return;
    }

    setSubmitting(true);
    setMessage('');
    clearError();

    try {
      await signup(normalizedEmail, password, displayName);
      router.dismissTo(authReturn(params.returnTo));
    } catch (caught) {
      setMessage(
        caught instanceof Error ? caught.message : '회원가입에 실패했습니다.',
      );
    } finally {
      setSubmitting(false);
    }
  };

  const update = (setter: (value: string) => void) => (value: string) => {
    setter(value);
    setMessage('');
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.keyboardArea}
      >
        <ScrollView
          contentContainerStyle={styles.scrollContent}
          keyboardDismissMode="on-drag"
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
        >
          <View style={styles.container}>
            <Pressable
              accessibilityRole="button"
              onPress={() =>
                router.canGoBack() ? router.back() : router.replace('/login')
              }
            >
              <Text style={styles.backText}>← 돌아가기</Text>
            </Pressable>

            <Text style={styles.eyebrow}>NEW ACCOUNT</Text>
            <Text style={styles.title}>회원가입</Text>
            <Text style={styles.description}>
              계정을 만들면 같은 정보로 웹과 모바일의 모의 계좌를 사용할 수
              있습니다.
            </Text>

            <View style={styles.form}>
              <View style={styles.field}>
                <Text style={styles.label}>표시 이름 (선택)</Text>
                <TextInput
                  accessibilityLabel="표시 이름"
                  editable={!submitting}
                  maxLength={100}
                  onChangeText={update(setDisplayName)}
                  placeholder="화면에 표시할 이름"
                  placeholderTextColor={colors.textMuted}
                  style={styles.input}
                  value={displayName}
                />
              </View>

              <View style={styles.field}>
                <Text style={styles.label}>이메일</Text>
                <TextInput
                  accessibilityLabel="회원가입 이메일"
                  autoCapitalize="none"
                  autoComplete="email"
                  editable={!submitting}
                  keyboardType="email-address"
                  onChangeText={update(setEmail)}
                  placeholder="name@example.com"
                  placeholderTextColor={colors.textMuted}
                  style={styles.input}
                  value={email}
                />
              </View>

              <View style={styles.field}>
                <Text style={styles.label}>비밀번호</Text>
                <TextInput
                  accessibilityLabel="회원가입 비밀번호"
                  autoCapitalize="none"
                  autoComplete="new-password"
                  editable={!submitting}
                  maxLength={100}
                  onChangeText={update(setPassword)}
                  placeholder="8자 이상"
                  placeholderTextColor={colors.textMuted}
                  secureTextEntry
                  style={styles.input}
                  value={password}
                />
              </View>

              <View style={styles.field}>
                <Text style={styles.label}>비밀번호 확인</Text>
                <TextInput
                  accessibilityLabel="회원가입 비밀번호 확인"
                  editable={!submitting}
                  onChangeText={update(setPasswordConfirm)}
                  onSubmitEditing={() => void submit()}
                  placeholder="비밀번호 다시 입력"
                  placeholderTextColor={colors.textMuted}
                  secureTextEntry
                  style={styles.input}
                  value={passwordConfirm}
                />
              </View>

              <AppButton
                disabled={submitting}
                label={submitting ? '계정 만드는 중...' : '회원가입'}
                onPress={() => void submit()}
                variant="primary"
              />

              {message ? (
                <View
                  accessibilityLiveRegion="assertive"
                  style={styles.messageBox}
                >
                  <Text style={styles.messageText}>{message}</Text>
                </View>
              ) : null}
            </View>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: colors.background },
  keyboardArea: { flex: 1 },
  scrollContent: { flexGrow: 1 },
  container: {
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 24,
    paddingVertical: 30,
  },
  backText: {
    marginBottom: 28,
    color: colors.accentText,
    fontSize: 14,
    fontWeight: '700',
  },
  eyebrow: {
    color: colors.accentText,
    fontSize: 12,
    fontWeight: '800',
    letterSpacing: 1.2,
  },
  title: {
    marginTop: 8,
    color: colors.textPrimary,
    fontSize: 34,
    fontWeight: '900',
  },
  description: {
    marginTop: 12,
    color: colors.textSecondary,
    fontSize: 14,
    lineHeight: 22,
  },
  form: { marginTop: 26, gap: 14 },
  field: { gap: 8 },
  label: { color: colors.textSecondary, fontSize: 13, fontWeight: '700' },
  input: {
    minHeight: 50,
    paddingHorizontal: 15,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    borderRadius: 13,
    backgroundColor: colors.input,
    color: colors.textPrimary,
    fontSize: 16,
  },
  messageBox: {
    paddingHorizontal: 14,
    paddingVertical: 11,
    borderWidth: 1,
    borderColor: colors.sell,
    borderRadius: 12,
    backgroundColor: colors.surface,
  },
  messageText: {
    color: colors.sellText,
    fontSize: 13,
    lineHeight: 19,
    textAlign: 'center',
  },
});
