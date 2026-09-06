/** 외부 아이콘 폰트 없이 SVG 선으로 그리는 공통 탐색 아이콘이다. */
import Svg, { Circle, Path, Rect } from 'react-native-svg';
import type { ColorValue } from 'react-native';

export type IconName =
  | 'market'
  | 'trade'
  | 'orders'
  | 'wallet'
  | 'back'
  | 'search'
  | 'chart'
  | 'chevron'
  | 'close';

export function AppIcon({
  name,
  color = '#a6adb8',
  size = 22,
}: {
  name: IconName;
  color?: ColorValue;
  size?: number;
}) {
  const paths: Partial<Record<IconName, string>> = {
    market: 'M4 19V12M10 19V5M16 19V9M22 19V2',
    trade: 'M3 7h17l-4-4M21 17H4l4 4',
    orders: 'M8 7h8M8 12h8M8 17h5',
    back: 'M15 5l-7 7 7 7',
    chart: 'M3 20h18M6 16V7M4 10h4M12 14V3M10 7h4M18 17V8M16 12h4',
    chevron: 'M8 10l4 4 4-4',
    close: 'M6 6l12 12M18 6L6 18',
    wallet: 'M3 7V5a2 2 0 0 1 2-2h13v4M17 12h4v5h-4z',
  };
  return (
    <Svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke={color}
      strokeWidth={1.7}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      {name === 'search' ? (
        <>
          <Circle cx={10} cy={10} r={6} />
          <Path d="m15 15 5 5" />
        </>
      ) : (
        <Path d={paths[name]} />
      )}
      {name === 'orders' ? (
        <Rect x={4} y={2} width={16} height={20} rx={2} />
      ) : null}
      {name === 'wallet' ? (
        <Rect x={3} y={7} width={18} height={14} rx={2} />
      ) : null}
    </Svg>
  );
}
