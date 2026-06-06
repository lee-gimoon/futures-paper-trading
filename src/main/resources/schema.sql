-- 7단계: 회원 테이블. R2DBC는 자동 DDL이 없어 부팅 시 이 스크립트로 생성한다.
CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(100),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 8단계: 사용자가 낸 주문. (포지션/잔고 원장은 9단계에서 추가)
--   가격·수량은 백엔드 원칙대로 NUMERIC(=자바 BigDecimal). double 금지.
--   side/type/status는 VARCHAR로 저장한다(자바 enum은 도메인 로직에서만 쓰고 경계에서 변환).
--   account_id는 9단계 계좌 도입 전이라 NULL 허용 + FK 없음(paper_accounts 테이블이 아직 없음).
CREATE TABLE IF NOT EXISTS paper_orders (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),  -- "내 주문만" 격리의 기준
    account_id      BIGINT,                                -- 9단계 전까지 NULL
    symbol          VARCHAR(20)   NOT NULL,                -- 우선 'BTCUSDT'
    side            VARCHAR(4)    NOT NULL,                -- BUY / SELL
    type            VARCHAR(8)    NOT NULL,                -- MARKET / LIMIT
    status          VARCHAR(10)   NOT NULL,                -- NEW/OPEN/FILLED/CANCELED/REJECTED
    limit_price     NUMERIC(38,8),                         -- 지정가에만 값, 시장가면 NULL
    quantity        NUMERIC(38,8) NOT NULL,
    filled_quantity NUMERIC(38,8) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 8단계: 실제 체결 내역. 한 주문이 여러 번 나눠 체결되면 fill이 여러 줄 생길 수 있어 1:N.
CREATE TABLE IF NOT EXISTS paper_fills (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT NOT NULL REFERENCES paper_orders(id),
    account_id  BIGINT,                                    -- 9단계 전까지 NULL
    symbol      VARCHAR(20)   NOT NULL,
    side        VARCHAR(4)    NOT NULL,
    price       NUMERIC(38,8) NOT NULL,                    -- 실제 체결 가격
    quantity    NUMERIC(38,8) NOT NULL,                    -- 실제 체결 수량
    fee         NUMERIC(38,8) NOT NULL DEFAULT 0,          -- 8단계는 0, 나중 단계에서 사용
    executed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
