package com.pettrip.auth.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpURLConnection;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Cloudflare 뒤에서 리다이렉트가 https로 생성되는지 검증한다.
 *
 * <p>docs/failures/003 참고: Traefik이 {@code X-Forwarded-Proto}를 http로 덮어써서 인가 요청 리다이렉트가 {@code
 * http://}로 나가고 있었다.
 *
 * <p>실제 서블릿 컨테이너를 띄우고 리다이렉트를 따라가지 않는 클라이언트로 {@code Location} 헤더를 직접 확인한다. MockMvc는 필터 등록과 헤더 처리를 실제
 * 컨테이너와 동일하게 재현하지 못한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CloudflareForwardedSchemeFilterTest {

  private static final String AUTHORIZE =
      "/oauth2/authorize?response_type=code&client_id=chapchu-front&scope=openid&state=s"
          + "&redirect_uri=http://localhost:3000/login/callback"
          + "&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
          + "&code_challenge_method=S256";

  @LocalServerPort private int port;

  private HttpURLConnection get(String path, String... headers) throws Exception {
    HttpURLConnection connection =
        (HttpURLConnection) URI.create("http://localhost:" + port + path).toURL().openConnection();
    connection.setInstanceFollowRedirects(false);
    connection.setRequestProperty("Accept", "text/html");
    for (int i = 0; i < headers.length; i += 2) {
      connection.setRequestProperty(headers[i], headers[i + 1]);
    }
    connection.getResponseCode();
    return connection;
  }

  @Test
  @DisplayName("Cloudflare가 https를 알려주면 Traefik이 http로 덮어썼어도 https로 리다이렉트한다")
  void restoresHttpsFromCloudflareHeader() throws Exception {
    HttpURLConnection connection =
        get(
            AUTHORIZE,
            "CF-Visitor",
            "{\"scheme\":\"https\"}",
            "X-Forwarded-Proto",
            "http",
            "X-Forwarded-Host",
            "auth.chapchu.site");

    assertThat(connection.getHeaderField("Location")).startsWith("https://auth.chapchu.site/");
  }

  @Test
  @DisplayName("Cloudflare 헤더가 없으면 원래 스킴을 그대로 둔다 (로컬 개발)")
  void leavesSchemeAloneWithoutCloudflareHeader() throws Exception {
    HttpURLConnection connection = get(AUTHORIZE);

    assertThat(connection.getHeaderField("Location")).startsWith("http://localhost:");
  }

  @Test
  @DisplayName("Cloudflare에 평문으로 접속한 경우에는 https로 바꾸지 않는다")
  void keepsHttpWhenVisitorSchemeIsHttp() throws Exception {
    HttpURLConnection connection =
        get(
            AUTHORIZE,
            "CF-Visitor",
            "{\"scheme\":\"http\"}",
            "X-Forwarded-Proto",
            "http",
            "X-Forwarded-Host",
            "auth.chapchu.site");

    assertThat(connection.getHeaderField("Location")).startsWith("http://auth.chapchu.site/");
  }

  @Test
  @DisplayName("스킴을 교정해도 host 등 나머지 forwarded 처리는 그대로 동작한다")
  void stillAppliesOtherForwardedHeaders() throws Exception {
    HttpURLConnection connection =
        get(
            AUTHORIZE,
            "CF-Visitor",
            "{\"scheme\":\"https\"}",
            "X-Forwarded-Proto",
            "http",
            "X-Forwarded-Host",
            "auth.example.test");

    assertThat(connection.getHeaderField("Location")).contains("auth.example.test");
  }
}
