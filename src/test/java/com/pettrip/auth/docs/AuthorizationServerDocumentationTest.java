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
 * <p>BFF 전환 이후 FE는 chapchu-auth를 직접 호출하지 않는다. chapchu-api가 OAuth2 Client로서 인가 흐름 전체를 처리한다.
 * 이 테스트는 chapchu-auth의 공개 엔드포인트(OIDC 디스커버리, JWKS)와 chapchu-api가 내부적으로 사용하는 인가 엔드포인트를 문서화한다.
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
  void 미인증_인가요청은_구글_로그인으로_리다이렉트된다() throws Exception {
    mockMvc
        .perform(
            get("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", "chapchu-api")
                .queryParam("redirect_uri", "http://localhost:8080/auth/callback")
                .queryParam("scope", "openid profile email")
                .queryParam("state", "server-generated-state"))
        .andExpect(status().is3xxRedirection())
        .andExpect(
            result ->
                assertThat(result.getResponse().getRedirectedUrl())
                    .contains("/oauth2/authorization/google"))
        .andDo(
            document(
                "oauth2-authorize",
                queryParameters(
                    parameterWithName("response_type").description("`code` 고정"),
                    parameterWithName("client_id").description("`chapchu-api` 고정. chapchu-api가 Confidential Client로 등록되어 있다"),
                    parameterWithName("redirect_uri")
                        .description("chapchu-api의 콜백 엔드포인트. 서버에 등록된 값과 정확히 일치해야 한다"),
                    parameterWithName("scope").description("`openid profile email`"),
                    parameterWithName("state").description("CSRF 방지용 난수. chapchu-api가 생성하여 쿠키에 저장한다"))));
  }

  @Test
  void 보호된_리소스_미인증_접근시_구글_로그인으로_리다이렉트된다() throws Exception {
    mockMvc
        .perform(get("/some-protected-resource"))
        .andExpect(status().is3xxRedirection())
        .andExpect(
            result ->
                assertThat(result.getResponse().getRedirectedUrl())
                    .contains("/oauth2/authorization/google"))
        .andDo(document("login-page"));
  }
}
