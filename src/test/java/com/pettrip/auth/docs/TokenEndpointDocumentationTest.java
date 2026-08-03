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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
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
 * <p>인가 코드는 브라우저에서 구글 로그인을 마쳐야 발급되므로 테스트에서 재현할 수 없다. 대신 로그인이 끝난 시점의 {@link OAuth2Authorization}을 직접
 * 저장해 두고 코드 교환만 수행한다.
 *
 * <p>동시에 chapchu-api와의 계약도 검증한다: 발급된 access token의 {@code sub}은 구글 식별자가 아니라 {@code users.user_id}
 * (UUID v7)여야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs(outputDir = "build/generated-snippets")
class TokenEndpointDocumentationTest {

  private static final String CLIENT_ID = "chapchu-front";
  private static final String REDIRECT_URI = "http://localhost:3000/login/callback";
  private static final String AUTHORIZATION_CODE = "aB3xK9pQ7mR2vN5t";
  private static final String CODE_VERIFIER = "chapchu-front-pkce-code-verifier-0123456789-abcdefg";
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

    MvcResult result =
        mockMvc
            .perform(
                post("/oauth2/token")
                    .param(OAuth2ParameterNames.GRANT_TYPE, "authorization_code")
                    .param(OAuth2ParameterNames.CODE, AUTHORIZATION_CODE)
                    .param(OAuth2ParameterNames.REDIRECT_URI, REDIRECT_URI)
                    .param(OAuth2ParameterNames.CLIENT_ID, CLIENT_ID)
                    .param(PkceParameterNames.CODE_VERIFIER, CODE_VERIFIER))
            .andExpect(status().isOk())
            .andDo(
                document(
                    "oauth2-token",
                    formParameters(
                        parameterWithName(OAuth2ParameterNames.GRANT_TYPE)
                            .description("`authorization_code` 고정"),
                        parameterWithName(OAuth2ParameterNames.CODE)
                            .description("콜백 URL의 `code` 쿼리 파라미터로 받은 인가 코드"),
                        parameterWithName(OAuth2ParameterNames.REDIRECT_URI)
                            .description("인가 요청 때 보낸 값과 동일해야 한다"),
                        parameterWithName(OAuth2ParameterNames.CLIENT_ID)
                            .description("`chapchu-front` 고정. public client라 client_secret은 없다"),
                        parameterWithName(PkceParameterNames.CODE_VERIFIER)
                            .description("인가 요청 때 보낸 `code_challenge`의 원본 값")),
                    relaxedResponseFields(
                        fieldWithPath("access_token")
                            .description("chapchu-api 호출 시 `Authorization: Bearer`로 사용한다"),
                        fieldWithPath("token_type").description("`Bearer`"),
                        fieldWithPath("expires_in").description("access token 잔여 유효시간(초). 30분"),
                        fieldWithPath("scope").description("승인된 scope"),
                        fieldWithPath("id_token").description("OIDC ID 토큰"))))
            .andReturn();

    assertAccessTokenSubjectIsInternalUserId(result);
    assertRefreshTokenIsNotIssued(result);
  }

  /**
   * 현재 설정으로는 refresh token이 발급되지 않는다.
   *
   * <p>{@code chapchu-front}는 {@link
   * org.springframework.security.oauth2.core.ClientAuthenticationMethod#NONE} public client인데,
   * Spring Authorization Server의 {@code OAuth2RefreshTokenGenerator}는 authorization_code 그랜트를 쓰는
   * public client에 대해 refresh token 생성을 건너뛴다. 따라서 {@code RegisteredClient}에 선언된 {@code
   * REFRESH_TOKEN} 그랜트와 {@code refreshTokenTimeToLive(14일)}는 실제로는 동작하지 않는 설정이며, 유저는 30분마다 재로그인해야
   * 한다.
   *
   * <p>이 테스트는 그 사실을 고정한다. 클라이언트를 confidential로 바꾸는 등 정책이 바뀌면 여기서 실패하므로 문서도 함께 갱신하게 된다.
   */
  private void assertRefreshTokenIsNotIssued(MvcResult result) throws Exception {
    Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);

    assertThat(body.containsKey("refresh_token")).isFalse();
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

  /** 구글 로그인을 막 끝낸 상태(= 인가 코드가 발급된 직후)를 재현한다. */
  private void saveAuthorizationCode() throws Exception {
    RegisteredClient client = registeredClientRepository.findByClientId(CLIENT_ID);
    Instant issuedAt = Instant.now();

    OAuth2AuthorizationRequest authorizationRequest =
        OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri("http://localhost:9000/oauth2/authorize")
            .clientId(CLIENT_ID)
            .redirectUri(REDIRECT_URI)
            .scopes(Set.of("openid", "profile", "email"))
            .state("front-generated-state")
            .additionalParameters(
                Map.of(
                    PkceParameterNames.CODE_CHALLENGE,
                    codeChallenge(CODE_VERIFIER),
                    PkceParameterNames.CODE_CHALLENGE_METHOD,
                    "S256"))
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

  private static String codeChallenge(String verifier) throws Exception {
    byte[] digest =
        MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
  }
}
