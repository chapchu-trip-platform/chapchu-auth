package com.pettrip.auth.docs;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 인증 서버의 공개 엔드포인트를 REST Docs 스니펫으로 남긴다.
 *
 * <p>로그인/회원등록은 브라우저 리다이렉트 기반 OAuth2 표준 흐름이라 단일 REST 호출로 문서화되지 않는다. 그래서 흐름을 구성하는 각 엔드포인트를 개별적으로
 * 문서화하고, 이들을 어떤 순서로 호출하는지는 {@code src/docs/asciidoc/index.adoc}에서 설명한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs(outputDir = "build/generated-snippets")
class AuthorizationServerDocumentationTest {

  @Autowired private MockMvc mockMvc;

  /**
   * 디스커버리·JWKS 응답은 OIDC/OAuth2 표준이 정의한 고정 스키마이고 Spring Authorization Server 버전에 따라 필드가 늘어난다. 필드
   * 서술자를 달면 라이브러리를 올릴 때마다 테스트가 깨지므로, 응답 본문 자체를 스니펫으로 남기고 해석은 표준 스펙에 맡긴다.
   */
  @Test
  void OIDC_디스커버리_문서를_노출한다() throws Exception {
    mockMvc
        .perform(get("/.well-known/openid-configuration"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.issuer").value("http://localhost:9000"))
        .andExpect(jsonPath("$.code_challenge_methods_supported[0]").value("S256"))
        .andDo(document("oidc-discovery"));
  }

  @Test
  void JWKS로_서명_검증용_공개키를_노출한다() throws Exception {
    mockMvc
        .perform(get("/oauth2/jwks"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
        .andExpect(jsonPath("$.keys[0].kid").exists())
        .andDo(document("oauth2-jwks"));
  }

  @Test
  void 미인증_인가요청은_로그인_페이지로_리다이렉트된다() throws Exception {
    mockMvc
        .perform(
            get("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", "chapchu-front")
                .queryParam("redirect_uri", "http://localhost:3000/login/callback")
                .queryParam("scope", "openid profile email")
                .queryParam("state", "front-generated-state")
                .queryParam("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                .queryParam("code_challenge_method", "S256"))
        .andExpect(status().is3xxRedirection())
        .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).endsWith("/login"))
        .andDo(
            document(
                "oauth2-authorize",
                queryParameters(
                    parameterWithName("response_type").description("`code` 고정"),
                    parameterWithName("client_id").description("`chapchu-front` 고정"),
                    parameterWithName("redirect_uri")
                        .description("인가 코드를 받을 프론트 콜백 URL. 서버에 등록된 값과 정확히 일치해야 한다"),
                    parameterWithName("scope").description("`openid profile email`"),
                    parameterWithName("state").description("CSRF 방지용 난수. 콜백에서 그대로 되돌려준다"),
                    parameterWithName("code_challenge")
                        .description("PKCE 챌린지. `BASE64URL(SHA256(code_verifier))`"),
                    parameterWithName("code_challenge_method").description("`S256` 고정"))));
  }

  @Test
  void 미인증_접근시_구글_로그인으로_리다이렉트된다() throws Exception {
    mockMvc
        .perform(get("/login"))
        .andExpect(status().is3xxRedirection())
        .andExpect(
            result ->
                assertThat(result.getResponse().getRedirectedUrl())
                    .contains("/oauth2/authorization/google"))
        .andDo(document("login-page"));
  }
}
