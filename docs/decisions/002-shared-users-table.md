# 002. chapchu-api의 users 테이블을 공유 매핑 (스키마 소유권은 chapchu-api)

## 상태
- [x] 확정됨 (Accepted)

## 결정
- chapchu-auth와 chapchu-api는 동일한 Postgres 인스턴스의 `users` 테이블을 공유한다.
- 스키마(Flyway 마이그레이션) 소유권은 chapchu-api에 있다. chapchu-auth는 `AuthUser` 엔티티로 로그인에 필요한 컬럼(`user_id`, `google_user_id`, `email`, `role`, `account_status`)만 매핑하고, `spring.jpa.hibernate.ddl-auto=none`으로 스키마를 절대 건드리지 않는다.
- chapchu-auth는 Flyway를 실행하지 않는다.

## 이유
- 별도 DB/스키마로 완전히 분리하면 Google 로그인 시점에 chapchu-api를 네트워크로 호출해 사용자를 get-or-create해야 하는데, 이 프로젝트 규모에서는 불필요한 복잡도.
- decision 004(chapchu-api)에서 이미 "단일 DB로 RDB+벡터 통합"을 정했으므로, 같은 Postgres를 auth 서버가 공유하는 것이 일관적이다.
- `User` 엔티티(chapchu-api)가 이미 `googleUserId` 컬럼과 `User(email, googleUserId)` 생성자를 갖고 있어 이 계약을 염두에 두고 설계되어 있었다.

## 에이전트 행동 지침
- chapchu-auth에서 `users` 테이블에 새 컬럼을 추가하거나 DDL을 실행하지 마라. 스키마 변경은 항상 chapchu-api의 Flyway 마이그레이션으로만 한다.
- chapchu-auth가 매핑하는 컬럼을 늘릴 때는 반드시 chapchu-api의 `V1__init_schema.sql`(또는 이후 마이그레이션)에 이미 존재하는 컬럼인지 먼저 확인하라.
- 향후 완전한 서비스 분리가 필요해지면(예: 별도 DB로 이전) 이 문서를 갱신하고 chapchu-api 쪽에 REST 기반 get-or-create 엔드포인트를 새로 설계해야 한다.
