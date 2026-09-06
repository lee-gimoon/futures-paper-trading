/**
 * 이 파일은 로그인 화면의 입력 상태와 Spring SESSION 로그인 요청을 담당한다.
 * 로그인에 성공하면 AuthProvider가 현재 사용자를 저장하고 로그인 전 사용하던 탭으로 돌아간다.
 */

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

export function LoginScreen() {
  const params = useLocalSearchParams<{ returnTo?: string }>();
  const returnTo = authReturn(params.returnTo);
  const { clearError, error: authError, login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const submit = async () => {
    if (submitting) return;
    const normalizedEmail = email.trim();
    if (!normalizedEmail || !password) {
      setMessage('이메일과 비밀번호를 모두 입력해 주세요.');
      return;
    }

    setSubmitting(true);
    setMessage('');
    clearError();

    try {
      await login(normalizedEmail, password);
      // 로그인 전 사용하던 탭으로 돌아가 거래 흐름을 이어 간다.
      router.dismissTo(returnTo);
    } catch (caught) {
      setMessage(
        caught instanceof Error ? caught.message : '로그인에 실패했습니다.',
      );
    } finally {
      setSubmitting(false);
    }
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
                router.canGoBack() ? router.back() : router.replace('/market')
              }
            >
              <Text style={styles.backText}>← 돌아가기</Text>
            </Pressable>

            <Text style={styles.eyebrow}>ACCOUNT</Text>
            <Text style={styles.title}>로그인</Text>
            <Text style={styles.description}>
              나의 모의 계좌와 거래 내역을 이어서 확인하세요.
            </Text>

            <View style={styles.form}>
              <View style={styles.field}>
                <Text style={styles.label}>이메일</Text>
                <TextInput
                  accessibilityLabel="로그인 이메일"
                  autoCapitalize="none"
                  autoComplete="email"
                  editable={!submitting}
                  keyboardType="email-address"
                  onChangeText={(value) => {
                    setEmail(value);
                    setMessage('');
                  }}
                  placeholder="name@example.com"
                  placeholderTextColor={colors.textMuted}
                  style={styles.input}
                  value={email}
                />
              </View>

              <View style={styles.field}>
                <Text style={styles.label}>비밀번호</Text>
                <TextInput
                  accessibilityLabel="로그인 비밀번호"
                  autoCapitalize="none"
                  autoComplete="password"
                  editable={!submitting}
                  onChangeText={(value) => {
                    setPassword(value);
                    setMessage('');
                  }}
                  onSubmitEditing={() => void submit()}
                  placeholder="비밀번호 입력"
                  placeholderTextColor={colors.textMuted}
                  secureTextEntry
                  style={styles.input}
                  value={password}
                />
              </View>

              <AppButton
                disabled={submitting}
                label={submitting ? '로그인 중...' : '로그인'}
                onPress={() => void submit()}
                variant="primary"
              />
              <AppButton
                disabled={submitting}
                label="계정 만들기"
                onPress={() =>
                  router.push({ pathname: '/signup', params: { returnTo } })
                }
                variant="choice"
              />

              {message || authError ? (
                <View
                  accessibilityLiveRegion="assertive"
                  style={styles.messageBox}
                >
                  <Text style={styles.messageText}>{message || authError}</Text>
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
    marginBottom: 36,
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
  form: { marginTop: 30, gap: 14 },
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
