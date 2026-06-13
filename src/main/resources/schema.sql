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
CREATE TABLE IF NOT EXISTS paper_orders (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),  -- "내 주문만" 격리의 기준
    symbol          VARCHAR(20)   NOT NULL,                -- 우선 'BTCUSDT'
    side            VARCHAR(4)    NOT NULL,                -- BUY / SELL
    type            VARCHAR(8)    NOT NULL,                -- MARKET / LIMIT
    status          VARCHAR(10)   NOT NULL,                -- NEW/OPEN/FILLED/CANCELED/REJECTED
    limit_price     NUMERIC(38,8),                         -- 지정가에만 값, 시장가면 NULL
    quantity        NUMERIC(38,8) NOT NULL,
    filled_quantity NUMERIC(38,8) NOT NULL DEFAULT 0,
    leverage        INT NOT NULL DEFAULT 10,                -- 주문 시점 레버리지(9단계) — 포지션이 진입 시점 값을 고정으로 들고 가는 근거
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 기존 주문(leverage 컬럼 추가 전 생성분)에도 컬럼이 생기도록 보강. 이미 있으면 무시.
ALTER TABLE paper_orders ADD COLUMN IF NOT EXISTS leverage INT NOT NULL DEFAULT 10;
ALTER TABLE paper_orders DROP COLUMN IF EXISTS account_id;

-- 8단계: 실제 체결 내역. 한 주문이 여러 번 나눠 체결되면 fill이 여러 줄 생길 수 있어 1:N.
CREATE TABLE IF NOT EXISTS paper_fills (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT NOT NULL REFERENCES paper_orders(id),
    symbol      VARCHAR(20)   NOT NULL,
    side        VARCHAR(4)    NOT NULL,
    price       NUMERIC(38,8) NOT NULL,                    -- 실제 체결 가격
    quantity    NUMERIC(38,8) NOT NULL,                    -- 실제 체결 수량
    fee         NUMERIC(38,8) NOT NULL DEFAULT 0,          -- 8단계는 0, 나중 단계에서 사용
    executed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE paper_fills DROP COLUMN IF EXISTS account_id;

-- 9단계: 사용자별 모의 계좌. 저장하는 건 '시드 현금'뿐이다.
--   포지션·실현/미실현 PnL은 테이블에 두지 않고 paper_fills에서 매번 다시 계산한다(로드맵 9단계 방향).
--   그래서 체결이 일어나도 이 테이블은 안 바뀌고, 가입 후 처음 조회할 때 1회 생성된다(시드 적립).
--   user_id UNIQUE = 한 사용자당 계좌 1개.
CREATE TABLE IF NOT EXISTS paper_accounts (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL UNIQUE REFERENCES users(id),
    cash_balance NUMERIC(38,8) NOT NULL,                   -- 시드 자본 (처음 1회 적립, 이후 불변)
    leverage     INT NOT NULL DEFAULT 10,                  -- 레버리지 배수 (% 바·마진·청산가 계산 기준)
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 기존 계좌(leverage 컬럼 추가 전 생성분)에도 컬럼이 생기도록 보강. 이미 있으면 무시.
ALTER TABLE paper_accounts ADD COLUMN IF NOT EXISTS leverage INT NOT NULL DEFAULT 10;
