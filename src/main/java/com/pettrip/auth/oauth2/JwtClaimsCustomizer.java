package com.pettrip.auth.oauth2;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

/**
 * chapchu-api 리소스 서버가 신뢰하는 JWT 계약: {@code sub} claim은 Google 식별자가 아니라 chapchu-api의 users.user_id
 * (UUID v7) 여야 한다. {@link FederatedOidcUserService}가 심어 둔 내부 user_id로 sub을 교체하고, email/role claim을
 * 추가한다.
 */
@Component
public class JwtClaimsCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

  @Override
  public void customize(JwtEncodingContext context) {
    if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())
        && !"id_token".equals(context.getTokenType().getValue())) {
      return;
    }

    Authentication principal = context.getPrincipal();
    if (!(principal.getPrincipal() instanceof OidcUser oidcUser)) {
      return;
    }

    Object internalUserId =
        oidcUser.getClaims().get(FederatedOidcUserService.INTERNAL_USER_ID_CLAIM);
    Object role = oidcUser.getClaims().get(FederatedOidcUserService.ROLE_CLAIM);

    context
        .getClaims()
        .subject(String.valueOf(internalUserId))
        .claim("email", oidcUser.getEmail())
        .claim("role", role);
  }
}
