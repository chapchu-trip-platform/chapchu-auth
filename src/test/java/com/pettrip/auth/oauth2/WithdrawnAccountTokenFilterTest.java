package com.pettrip.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pettrip.auth.user.AuthUserService;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/**
 * 리프레시 경로는 {@link FederatedOidcUserService}를 타지 않는다. 그래서 로그인 차단만으로는 탈퇴자가 최대 14일 동안 토큰을 계속 받는다. 이
 * 필터가 그 구멍을 막는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WithdrawnAccountTokenFilterTest {

  private static final String REFRESH_TOKEN = "refresh-token-value";
  private static final UUID USER_ID = UUID.fromString("0198f3a0-1234-7000-8000-000000000001");

  @Mock private OAuth2AuthorizationService authorizationService;
  @Mock private AuthUserService authUserService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private WithdrawnAccountTokenFilter filter() {
    return new WithdrawnAccountTokenFilter(authorizationService, authUserService, objectMapper);
  }

  @Test
  void 탈퇴한_계정의_리프레시는_403으로_막는다() throws Exception {
    when(authorizationService.findByToken(REFRESH_TOKEN, OAuth2TokenType.REFRESH_TOKEN))
        .thenReturn(authorization());
    when(authUserService.isWithdrawn(USER_ID)).thenReturn(true);

    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter().doFilter(refreshRequest(), response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString()).contains("WITHDRAWN_ACCOUNT");
    assertThat(chain.getRequest()).as("체인으로 넘어가면 안 된다").isNull();
  }

  @Test
  void 정상_계정의_리프레시는_통과시킨다() throws Exception {
    when(authorizationService.findByToken(REFRESH_TOKEN, OAuth2TokenType.REFRESH_TOKEN))
        .thenReturn(authorization());
    when(authUserService.isWithdrawn(USER_ID)).thenReturn(false);

    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter().doFilter(refreshRequest(), response, chain);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  void refresh_token_교환이_아니면_건드리지_않는다() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/token");
    request.setParameter(OAuth2ParameterNames.GRANT_TYPE, "authorization_code");

    MockFilterChain chain = new MockFilterChain();
    filter().doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  void 인가정보를_찾을_수_없으면_통과시킨다() throws Exception {
    when(authorizationService.findByToken(REFRESH_TOKEN, OAuth2TokenType.REFRESH_TOKEN))
        .thenReturn(null);

    MockFilterChain chain = new MockFilterChain();
    filter().doFilter(refreshRequest(), new MockHttpServletResponse(), chain);

    assertThat(chain.getRequest()).as("토큰 유효성은 토큰 엔드포인트가 판단한다").isNotNull();
  }

  private MockHttpServletRequest refreshRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/token");
    request.setParameter(OAuth2ParameterNames.GRANT_TYPE, "refresh_token");
    request.setParameter(OAuth2ParameterNames.REFRESH_TOKEN, REFRESH_TOKEN);
    return request;
  }

  /** 구글 로그인을 마친 뒤 저장되는 인가 정보와 같은 모양. */
  private OAuth2Authorization authorization() {
    RegisteredClient client =
        RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("chapchu-api")
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .build();

    Instant now = Instant.now();
    return OAuth2Authorization.withRegisteredClient(client)
        .principalName(USER_ID.toString())
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .token(new OAuth2RefreshToken(REFRESH_TOKEN, now, now.plus(Duration.ofDays(14))))
        .attribute(Principal.class.getName(), googlePrincipal())
        .build();
  }

  private Authentication googlePrincipal() {
    Instant now = Instant.now();
    OidcIdToken idToken =
        OidcIdToken.withTokenValue("google-id-token")
            .issuer("https://accounts.google.com")
            .subject("104219371049213741023")
            .issuedAt(now)
            .expiresAt(now.plus(Duration.ofHours(1)))
            .claim("email", "tester@chapchu.site")
            .claim(FederatedOidcUserService.INTERNAL_USER_ID_CLAIM, USER_ID.toString())
            .claim(FederatedOidcUserService.ROLE_CLAIM, "USER")
            .build();

    OidcUser oidcUser =
        new DefaultOidcUser(
            AuthorityUtils.createAuthorityList("ROLE_USER"),
            idToken,
            new OidcUserInfo(idToken.getClaims()));

    return new UsernamePasswordAuthenticationToken(oidcUser, null, oidcUser.getAuthorities());
  }
}
