package com.pettrip.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpURLConnection;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 에러 응답이 로그인 리다이렉트로 바뀌지 않는지 검증한다.
 *
 * <p>404나 400이 발생하면 서블릿 컨테이너가 {@code /error}로 forward 하는데, 이 ERROR 디스패치도 시큐리티 필터를 다시 탄다. permitAll
 * 해두지 않으면 {@code anyRequest().authenticated()}에 걸려, 이 서버에서는 <b>구글 로그인으로 302 리다이렉트</b>가 나간다. 즉 정상적으로
 * 만들어진 400/404 응답이 통째로 사라진다.
 *
 * <p><b>이 테스트는 반드시 실제 서블릿 컨테이너에서 돌려야 한다.</b> MockMvc는 에러 페이지 forward를 일으키지 않아 이 버그를 재현하지 못한다
 * (chapchu-api {@code docs/failures/022}에서 같은 이유로 놓쳤다). 그래서 {@code RANDOM_PORT} + {@link
 * TestRestTemplate} 조합을 쓴다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorDispatchAccessTest {

  private static final String VALID_PKCE =
      "&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256";

  @Autowired private TestRestTemplate restTemplate;

  @LocalServerPort private int port;

  @Test
  @DisplayName("잘못된 인가 요청은 로그인 리다이렉트가 아니라 400을 반환한다")
  void invalidAuthorizationRequestReturnsBadRequest() {
    ResponseEntity<String> response =
        restTemplate.getForEntity(
            "/oauth2/authorize?response_type=code&client_id=nonexistent-client&scope=openid"
                + "&redirect_uri=http://localhost:3000/login/callback"
                + VALID_PKCE,
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("등록되지 않은 redirect_uri도 400을 반환한다")
  void unregisteredRedirectUriReturnsBadRequest() {
    ResponseEntity<String> response =
        restTemplate.getForEntity(
            "/oauth2/authorize?response_type=code&client_id=chapchu-front&scope=openid"
                + "&redirect_uri=https://unregistered.example/callback"
                + VALID_PKCE,
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("공개 경로의 없는 리소스는 로그인 리다이렉트가 아니라 404를 반환한다")
  void missingPublicResourceReturnsNotFound() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/docs/nonexistent.html", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  /** 이 수정이 인증 자체를 열어버리지 않았는지 확인한다. 리다이렉트를 따라가면 실제 구글까지 호출하게 되므로 따라가지 않는 클라이언트를 쓴다. */
  @Test
  @DisplayName("보호된 경로는 여전히 인증을 요구한다")
  void protectedPathStillRequiresAuthentication() throws Exception {
    HttpURLConnection connection =
        (HttpURLConnection)
            URI.create("http://localhost:" + port + "/some-protected-path")
                .toURL()
                .openConnection();
    connection.setInstanceFollowRedirects(false);

    assertThat(connection.getResponseCode()).isEqualTo(302);
    assertThat(connection.getHeaderField("Location")).contains("/oauth2/authorization/google");
  }
}
