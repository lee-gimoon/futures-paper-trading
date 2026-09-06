/**
 * 이 파일은 `/orders` 경로와 실제 주문내역 화면 컴포넌트를 연결한다.
 * 화면 내용은 경로 파일에 작성하지 않고 OrdersScreen이 담당한다.
 */

import { OrdersScreen } from '@/features/orders/OrdersScreen';

// OrdersRoute는 주문내역 탭이 선택됐을 때 OrdersScreen을 렌더링한다.
export default function OrdersRoute() {
  return <OrdersScreen />;
}
