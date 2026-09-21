package com.pettrip.auth.oauth2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pettrip.auth.user.AuthUserService;
import com.pettrip.auth.web.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 탈퇴 계정의 refresh_token 재발급을 막는다.
 *
 * <p>{@link FederatedOidcUserService}의 탈퇴 검사는 구글 로그인 때만 돈다. refresh_token 교환은 그 경로를 타지 않으므로, 막지 않으면
 * 탈퇴 전에 받아 둔 refresh token으로 최대 14일 동안 access token을 계속 받을 수 있다.
 *
 * <p>사용자 판정이 불가능한 경우(저장된 인가 정보가 없거나 claim이 비어 있음)는 통과시킨다. 여기서 막으면 판정 버그 하나가 전체 재발급을 잠가 버린다. 토큰 자체의
 * 유효성은 뒤따르는 토큰 엔드포인트가 판단한다.
 */
public class WithdrawnAccountTokenFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(WithdrawnAccountTokenFilter.class);

  private final OAuth2AuthorizationService authorizationService;
  private final AuthUserService authUserService;
  private final ObjectMapper objectMapper;

  public WithdrawnAccountTokenFilter(
      OAuth2AuthorizationService authorizationService,
      AuthUserService authUserService,
      ObjectMapper objectMapper) {
    this.authorizationService = authorizationService;
    this.authUserService = authUserService;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    UUID userId = withdrawnCandidate(request);
    if (userId == null) {
      filterChain.doFilter(request, response);
      return;
    }
    if (!authUserService.isWithdrawn(userId)) {
      filterChain.doFilter(request, response);
      return;
    }

    log.info("[탈퇴] refresh_token 재발급 차단 — userId={}", userId);
    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(
        response.getWriter(), new ErrorResponse("WITHDRAWN_ACCOUNT", "탈퇴한 계정입니다."));
  }

  /** refresh_token 교환 요청이면 그 토큰이 가리키는 내부 user_id를, 아니면 null을 돌려준다. */
  private UUID withdrawnCandidate(HttpServletRequest request) {
    if (!AuthorizationGrantTypes.isRefreshToken(request)) {
      return null;
    }
    String refreshToken = request.getParameter(OAuth2ParameterNames.REFRESH_TOKEN);
    if (refreshToken == null || refreshToken.isBlank()) {
      return null;
    }
    OAuth2Authorization authorization =
        authorizationService.findByToken(refreshToken, OAuth2TokenType.REFRESH_TOKEN);
    if (authorization == null) {
      return null;
    }
    return internalUserId(authorization);
  }

  /** 로그인 때 심어 둔 {@code internal_user_id} claim이 users.user_id다. */
  private UUID internalUserId(OAuth2Authorization authorization) {
    Authentication principal = authorization.getAttribute(Principal.class.getName());
    if (principal == null) {
      return null;
    }
    if (!(principal.getPrincipal() instanceof OidcUser oidcUser)) {
      return null;
    }
    Object claim = oidcUser.getClaims().get(FederatedOidcUserService.INTERNAL_USER_ID_CLAIM);
    if (claim == null) {
      return null;
    }
    try {
      return UUID.fromString(claim.toString());
    } catch (IllegalArgumentException e) {
      log.warn("[탈퇴] internal_user_id claim이 UUID가 아니다: {}", claim);
      return null;
    }
  }

  private static final class AuthorizationGrantTypes {
    private AuthorizationGrantTypes() {}

    static boolean isRefreshToken(HttpServletRequest request) {
      return "refresh_token".equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE));
    }
  }
}
