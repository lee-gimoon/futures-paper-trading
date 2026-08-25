import { Alert, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

export default function HomeScreen() {
  const showReadyMessage = () => {
    Alert.alert('준비 완료', '모바일 앱의 첫 화면이 정상적으로 실행되었습니다.');
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <View style={styles.container}>
        <View style={styles.badge}>
          <Text style={styles.badgeText}>PAPER TRADING</Text>
        </View>

        <Text style={styles.title}>Futures Paper Trading</Text>
        <Text style={styles.description}>
          실제 자산을 사용하지 않고 선물 거래를 연습하는 모바일 앱입니다.
        </Text>

        <Pressable
          accessibilityRole="button"
          onPress={showReadyMessage}
          style={({ pressed }) => [styles.button, pressed && styles.buttonPressed]}
        >
          <Text style={styles.buttonText}>첫 화면 확인하기</Text>
        </Pressable>

        <Text style={styles.step}>1단계 · Expo 프로젝트 실행</Text>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#07111f',
  },
  container: {
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 24,
  },
  badge: {
    alignSelf: 'flex-start',
    marginBottom: 16,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 999,
    backgroundColor: '#12335b',
  },
  badgeText: {
    color: '#70b7ff',
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 1.2,
  },
  title: {
    color: '#f8fafc',
    fontSize: 34,
    fontWeight: '800',
    lineHeight: 41,
  },
  description: {
    marginTop: 14,
    color: '#a9b8cc',
    fontSize: 16,
    lineHeight: 25,
  },
  button: {
    alignItems: 'center',
    marginTop: 36,
    paddingVertical: 16,
    borderRadius: 14,
    backgroundColor: '#2388ff',
  },
  buttonPressed: {
    opacity: 0.72,
  },
  buttonText: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '700',
  },
  step: {
    marginTop: 20,
    color: '#65758b',
    fontSize: 13,
    textAlign: 'center',
  },
});
