# 003. RegisteredClientRepository는 우선 인메모리로 구현

## 상태
- [x] 확정됨 (Accepted), 추후 재검토 예정

## 결정
- OAuth2 클라이언트(`chapchu-front` 등) 등록 정보는 `InMemoryRegisteredClientRepository`로 애플리케이션 시작 시 코드로 등록한다. 별도 테이블을 만들지 않는다.

## 이유
- 클라이언트 수가 적고(프론트 1개) 자주 바뀌지 않아, 지금 단계에서 JDBC 기반 저장소를 도입하는 비용이 이익보다 크다.
- `docs/decisions/002-shared-users-table.md`에 따라 chapchu-api의 스키마에 손대지 않기로 했으므로, 클라이언트 등록 테이블을 만들려면 chapchu-auth 전용 DB나 별도 스키마가 필요해져 범위가 커진다.

## 에이전트 행동 지침
- 등록 클라이언트가 여러 개로 늘어나거나(예: 모바일 앱 추가) 파드 재시작마다 재등록되는 게 문제가 되면, `JdbcRegisteredClientRepository` + 전용 스키마로 전환을 검토하라. 이때는 새 ADR을 작성하라.
- 클라이언트 추가는 `AuthorizationServerConfig#registeredClientRepository`에 `RegisteredClient`를 더 추가하는 방식으로 하라.
