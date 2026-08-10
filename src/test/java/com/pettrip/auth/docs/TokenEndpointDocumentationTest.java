package com.pettrip.auth.docs;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields;
import static org.springframework.restdocs.request.RequestDocumentation.formParameters;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import com.pettrip.auth.oauth2.FederatedOidcUserService;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 토큰 엔드포인트를 문서화한다.
 *
 * <p>BFF 전환 이후 chapchu-api는 Confidential Client로서 Basic Auth로 클라이언트 인증을 한다. PKCE는 사용하지 않는다.
 *
 * <p>인가 코드는 브라우저에서 구글 로그인을 마쳐야 발급되므로 테스트에서 재현할 수 없다. 대신 로그인이 끝난 시점의 {@link OAuth2Authorization}을 직접
 * 저장해 두고 코드 교환만 수행한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs(outputDir = "build/generated-snippets")
class TokenEndpointDocumentationTest {

  private static final String CLIENT_ID = "chapchu-api";
  private static final String CLIENT_SECRET = "test-api-client-secret";
  private static final String REDIRECT_URI = "http://localhost:8080/auth/callback";
  private static final String AUTHORIZATION_CODE = "aB3xK9pQ7mR2vN5t";
  private static final String GOOGLE_SUBJECT = "104219371049213741023";
  private static final UUID INTERNAL_USER_ID =
      UUID.fromString("0198f3a0-1234-7000-8000-000000000001");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private RegisteredClientRepository registeredClientRepository;
  @Autowired private OAuth2AuthorizationService authorizationService;

  @Test
  void 인가코드를_액세스_토큰으로_교환한다() throws Exception {
    saveAuthorizationCode();

    String basicAuth =
        Base64.getEncoder()
            .encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes());

    MvcResult result =
        mockMvc
            .perform(
                post("/oauth2/token")
                    .header("Authorization", "Basic " + basicAuth)
                    .param(OAuth2ParameterNames.GRANT_TYPE, "authorization_code")
                    .param(OAuth2ParameterNames.CODE, AUTHORIZATION_CODE)
                    .param(OAuth2ParameterNames.REDIRECT_URI, REDIRECT_URI))
            .andExpect(status().isOk())
            .andDo(
                document(
                    "oauth2-token",
                    formParameters(
                        parameterWithName(OAuth2ParameterNames.GRANT_TYPE)
                            .description("`authorization_code` 고정"),
                        parameterWithName(OAuth2ParameterNames.CODE)
                            .description("chapchu-auth가 /auth/callback으로 전달한 인가 코드"),
                        parameterWithName(OAuth2ParameterNames.REDIRECT_URI)
                            .description("chapchu-api의 콜백 엔드포인트. 인가 요청 때 보낸 값과 동일해야 한다")),
                    relaxedResponseFields(
                        fieldWithPath("access_token")
                            .description("JWT. chapchu-api가 FE 콜백 URL의 fragment로 전달한다"),
                        fieldWithPath("refresh_token")
                            .description("chapchu-api가 HttpOnly 쿠키로 저장. /auth/refresh 호출 시 사용"),
                        fieldWithPath("token_type").description("`Bearer`"),
                        fieldWithPath("expires_in").description("access token 잔여 유효시간(초). 30분"),
                        fieldWithPath("scope").description("승인된 scope"),
                        fieldWithPath("id_token").description("OIDC ID 토큰"))))
            .andReturn();

    assertAccessTokenSubjectIsInternalUserId(result);
    assertRefreshTokenIsIssued(result);
  }

  /** chapchu-api ADR 016 계약: JWT의 sub은 users.user_id여야 한다. */
  private void assertAccessTokenSubjectIsInternalUserId(MvcResult result) throws Exception {
    Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    String accessToken = (String) body.get("access_token");

    SignedJWT jwt = SignedJWT.parse(accessToken);

    assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(INTERNAL_USER_ID.toString());
    assertThat(jwt.getJWTClaimsSet().getSubject()).isNotEqualTo(GOOGLE_SUBJECT);
    assertThat(jwt.getJWTClaimsSet().getStringClaim("role")).isEqualTo("USER");
  }

  /** Confidential Client는 refresh_token이 발급된다. */
  private void assertRefreshTokenIsIssued(MvcResult result) throws Exception {
    Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);

    assertThat(body.containsKey("refresh_token")).isTrue();
  }

  /** 구글 로그인을 막 끝낸 상태(= 인가 코드가 발급된 직후)를 재현한다. */
  private void saveAuthorizationCode() {
    RegisteredClient client = registeredClientRepository.findByClientId(CLIENT_ID);
    Instant issuedAt = Instant.now();

    OAuth2AuthorizationRequest authorizationRequest =
        OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri("http://localhost:9000/oauth2/authorize")
            .clientId(CLIENT_ID)
            .redirectUri(REDIRECT_URI)
            .scopes(Set.of("openid", "profile", "email"))
            .state("server-generated-state")
            .build();

    OAuth2AuthorizationCode code =
        new OAuth2AuthorizationCode(
            AUTHORIZATION_CODE, issuedAt, issuedAt.plus(Duration.ofMinutes(5)));

    authorizationService.save(
        OAuth2Authorization.withRegisteredClient(client)
            .principalName(INTERNAL_USER_ID.toString())
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizedScopes(Set.of("openid", "profile", "email"))
            .token(code)
            .attribute(OAuth2AuthorizationRequest.class.getName(), authorizationRequest)
            .attribute(Principal.class.getName(), googlePrincipal())
            .build());
  }

  /** {@code FederatedOidcUserService}가 구글 로그인 직후 만들어 두는 principal과 같은 모양. */
  private Authentication googlePrincipal() {
    Instant now = Instant.now();
    OidcIdToken idToken =
        OidcIdToken.withTokenValue("google-id-token")
            .issuer("https://accounts.google.com")
            .subject(GOOGLE_SUBJECT)
            .issuedAt(now)
            .expiresAt(now.plus(Duration.ofHours(1)))
            .claim("email", "tester@chapchu.site")
            .claim(FederatedOidcUserService.INTERNAL_USER_ID_CLAIM, INTERNAL_USER_ID.toString())
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
