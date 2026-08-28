/**
 * 이 파일은 앱을 처음 열었을 때 보이는 첫 화면의 내용을 작성하는 곳이다.
 * Expo Router는 `app` 폴더의 파일 이름을 주소와 연결하므로,
 * `app/index.tsx`는 앱의 기본 주소 `/`에 표시되는 화면이 된다.
 *
 * 아래의 `HomeScreen` 컴포넌트는 안내 문구와 버튼을 화면에 배치한다.
 * 사용자가 버튼을 누르면 `Alert`가 운영체제의 알림 창을 띄운다.
 * 화면에서 사용하는 색상·크기·간격·배치는 파일 아래의 `styles`에 모아 둔다.
 */

/*
 * 아래 항목은 모두 `react-native` 라이브러리가 기본 제공하는 기능이다.
 *
 * Alert: 화면에 직접 그려지는 컴포넌트가 아니라, 운영체제의 알림 창을 띄우는 API 객체다.
 *        `Alert.alert(제목, 설명)`처럼 alert 메서드를 호출한다.
 * Pressable: 사용자가 누를 수 있는 영역을 만드는 UI 컴포넌트다. 웹의 button과 비슷하며,
 *            `onPress` 속성에 누른 뒤 실행할 함수를 연결한다.
 * StyleSheet: 화면에 직접 표시되지 않는 스타일 API 객체다.
 *             `StyleSheet.create(...)`로 색상·여백·글자 크기 등의 스타일 묶음을 만든다.
 * Text: 글자를 화면에 표시하는 UI 컴포넌트다.
 * View: 다른 컴포넌트를 묶고 배치하는 UI 컨테이너 컴포넌트다. 웹의 div와 비슷하다.
 */
import { Alert, Pressable, StyleSheet, Text, View } from 'react-native';
// SafeAreaView는 화면 내용이 카메라 구멍, 상태 표시줄, 홈 표시 영역과 겹치지 않도록
// 안전 영역을 적용하는 UI 컴포넌트다. `react-native-safe-area-context` 라이브러리에서 가져온다.
import { SafeAreaView } from 'react-native-safe-area-context';

// HomeScreen은 기본 주소 `/`에서 Expo Router가 렌더링하는 첫 화면 컴포넌트다.
export default function HomeScreen() {
  // 사용자가 "첫 화면 확인하기" 버튼을 누르면 실행되는 이벤트 처리 함수다.
  const showReadyMessage = () => {
    // 첫 번째 문자열은 알림 제목이고, 두 번째 문자열은 제목 아래에 표시되는 설명이다.
    Alert.alert('준비 완료', '모바일 앱의 첫 화면이 정상적으로 실행되었습니다.');
  };

  return (
    /* return 뒤의 JSX 코드는 이 구조대로 화면에 렌더링된다. */
    /* 현재 HomeScreen은 부모 컴포넌트 안에 자식 컴포넌트를 계층적으로 넣어 구성했으며, 이 구조를 컴포넌트 트리라고 한다. */

    /* SafeAreaView는 휴대폰의 안전 영역 안에서 화면 전체를 사용한다. */
    <SafeAreaView style={styles.safeArea}>
      {/* container는 아래의 배지·제목·설명·버튼·상태 문구를 하나의 영역으로 묶는다. */}
      <View style={styles.container}>
        {/* 작은 배지 영역. View가 배경을 만들고 그 안의 Text가 글자를 표시한다. */}
        <View style={styles.badge}>
          <Text style={styles.badgeText}>PAPER TRADING</Text>
        </View>

        {/* 앱 이름과 이 앱이 어떤 용도인지 설명하는 글자다. */}
        <Text style={styles.title}>Futures Paper Trading</Text>
        <Text style={styles.description}>
          실제 자산을 사용하지 않고 선물 거래를 연습하는 모바일 앱입니다.
        </Text>

        {/*
         * Pressable은 사용자가 누를 수 있는 영역을 만든다.
         * accessibilityRole="button"은 보조 기술에 이 영역이 버튼임을 알려 준다.
         * onPress는 버튼을 눌렀을 때 실행할 showReadyMessage 함수를 연결한다.
         * style 함수의 pressed는 버튼을 누르고 있는 동안 true가 되며,
         * 이때 buttonPressed 스타일을 추가해 버튼이 눌렸다는 시각적 반응을 보여 준다.
         */}
        <Pressable
          accessibilityRole="button"
          onPress={showReadyMessage}
          style={({ pressed }) => [styles.button, pressed && styles.buttonPressed]}
        >
          <Text style={styles.buttonText}>첫 화면 확인하기</Text>
        </Pressable>

        {/* 현재 Expo 모바일 프로젝트가 실행되었음을 보여 주는 보조 상태 문구다. */}
        <Text style={styles.step}>Expo 프로젝트 실행 완료</Text>
      </View>
    </SafeAreaView>
  );
}

// StyleSheet는 위 JSX에서 참조하는 색상·크기·간격·배치 규칙을 이름별로 만든다.
const styles = StyleSheet.create({
  // SafeAreaView가 화면 전체를 채우고 첫 화면의 바탕색을 사용하게 한다.
  safeArea: {
    flex: 1,
    backgroundColor: '#07111f',
  },
  // 모든 화면 내용을 세로 가운데에 놓고 좌우에 24만큼의 여백을 둔다.
  container: {
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 24,
  },
  // 배지를 글자 너비만큼만 만들고 둥근 알약 모양과 배경색을 적용한다.
  badge: {
    alignSelf: 'flex-start',
    marginBottom: 16,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 999,
    backgroundColor: '#12335b',
  },
  // 배지 안의 글자 색상·크기·굵기·글자 간격을 정한다.
  badgeText: {
    color: '#70b7ff',
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 1.2,
  },
  // 앱 제목을 가장 크고 굵은 밝은 글자로 표시한다.
  title: {
    color: '#f8fafc',
    fontSize: 34,
    fontWeight: '800',
    lineHeight: 41,
  },
  // 제목 아래 설명 문구에 위쪽 간격과 읽기 편한 줄 높이를 적용한다.
  description: {
    marginTop: 14,
    color: '#a9b8cc',
    fontSize: 16,
    lineHeight: 25,
  },
  // 버튼 내용을 가운데 정렬하고 내부 여백·둥근 모서리·파란 배경을 적용한다.
  button: {
    alignItems: 'center',
    marginTop: 36,
    paddingVertical: 16,
    borderRadius: 14,
    backgroundColor: '#2388ff',
  },
  // 사용자가 버튼을 누르고 있는 동안 투명도를 낮춰 눌림 상태를 표현한다.
  buttonPressed: {
    opacity: 0.72,
  },
  // 버튼 안의 글자를 흰색의 굵은 글자로 표시한다.
  buttonText: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '700',
  },
  // 버튼 아래의 보조 상태 문구에 간격·색상·크기·가운데 정렬을 적용한다.
  step: {
    marginTop: 20,
    color: '#65758b',
    fontSize: 13,
    textAlign: 'center',
  },
});
