/**
 * 루트 Stack 안에 표시되는 공통 하단 탐색 영역이다.
 * Tabs.Screen은 같은 폴더의 경로 이름을 탭 이름·아이콘과 연결한다.
 */
import { Tabs } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { AppIcon } from '@/components/AppIcon';
import { colors } from '@/theme/colors';

export default function TabsLayout() {
  const insets = useSafeAreaInsets();
  return (
    <Tabs
      initialRouteName="market"
      screenOptions={{
        headerShown: false,
        tabBarHideOnKeyboard: true,
        tabBarActiveTintColor: colors.accentText,
        tabBarInactiveTintColor: colors.textMuted,
        tabBarLabelStyle: { fontSize: 11, fontWeight: '700' },
        tabBarStyle: {
          borderTopColor: colors.border,
          backgroundColor: colors.background,
          height: 62 + insets.bottom,
          paddingTop: 6,
          paddingBottom: Math.max(insets.bottom, 6),
        },
      }}
    >
      <Tabs.Screen
        name="market"
        options={{
          title: '마켓',
          tabBarIcon: ({ color }) => <AppIcon name="market" color={color} />,
        }}
      />
      <Tabs.Screen
        name="trade"
        options={{
          title: '선물',
          tabBarIcon: ({ color }) => <AppIcon name="trade" color={color} />,
        }}
      />
      <Tabs.Screen
        name="orders"
        options={{
          title: '주문',
          tabBarIcon: ({ color }) => <AppIcon name="orders" color={color} />,
        }}
      />
      <Tabs.Screen
        name="account"
        options={{
          title: '자산',
          tabBarIcon: ({ color }) => <AppIcon name="wallet" color={color} />,
        }}
      />
    </Tabs>
  );
}
