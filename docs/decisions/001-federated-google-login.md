# 001. Google 로그인은 Spring Authorization Server의 federated identity 패턴으로 구현

## 상태
- [x] 확정됨 (Accepted)

## 결정
- chapchu-auth는 자체 회원가입/비밀번호 로그인을 갖지 않는다. `/oauth2/authorize` 요청이 미인증 상태면 로그인 페이지 대신 Spring Security의 `oauth2Login()`으로 즉시 Google 로그인으로 위임한다.
- `FederatedOidcUserService`(`OidcUserService` 확장)가 Google 로그인 성공 시점에 개입해 chapchu-api의 `users` 테이블에서 내부 사용자를 get-or-create하고, 그 결과(`user_id`, `role`)를 `OidcUser`의 claims에 추가로 실어 둔다.
- `JwtClaimsCustomizer`(`OAuth2TokenCustomizer<JwtEncodingContext>`)가 실제 토큰 발급 시점에 그 claims를 읽어 `sub`을 Google 식별자가 아닌 내부 `user_id`로 덮어쓰고, `email`/`role` claim을 추가한다.

## 이유
- Spring Authorization Server 공식 샘플(federated-identity)과 동일한 구조 — 표준 패턴을 따라야 유지보수가 쉽다.
- chapchu-api는 JWT의 `sub`을 자신의 `users.user_id`로 신뢰해야 하므로, Google의 `sub`(Google 고유 ID)을 그대로 노출하면 안 된다.

## 에이전트 행동 지침
- `sub` claim을 Google ID로 되돌리지 마라. 항상 `users.user_id` (UUID v7 문자열)이어야 한다.
- 신규 클레임이 필요하면 `FederatedOidcUserService`에서 claims에 심고 `JwtClaimsCustomizer`에서 꺼내는 동일한 2단계 패턴을 따르라.
