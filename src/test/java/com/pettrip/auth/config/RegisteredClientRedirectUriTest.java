package com.pettrip.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * 콜백 URL을 쉼표로 여러 개 등록할 수 있는지 검증한다.
 *
 * <p>등록되지 않은 {@code redirect_uri}로 인가 요청이 오면 에러 없이 구글 로그인으로 넘어간 뒤 아무 데도 도달하지 못한다. 원인을 알려주는 메시지가 없어
 * 로컬 개발 주소와 배포 주소를 동시에 등록해 두는 것이 중요하다.
 */
class RegisteredClientRedirectUriTest {

  @Nested
  @ExtendWith(SpringExtension.class)
  @SpringBootTest
  @TestPropertySource(
      properties =
          "chapchu-auth.client.front-redirect-uri="
              + "http://localhost:3000/login/callback, https://chapchu.site/login/callback")
  class 여러_개_등록 {

    @Autowired private RegisteredClientRepository registeredClientRepository;

    @Test
    void 쉼표로_구분한_콜백_URL이_모두_등록된다() {
      RegisteredClient client = registeredClientRepository.findByClientId("chapchu-front");

      assertThat(client).isNotNull();
      assertThat(client.getRedirectUris())
          .containsExactlyInAnyOrder(
              "http://localhost:3000/login/callback", "https://chapchu.site/login/callback");
    }
  }

  @Nested
  @ExtendWith(SpringExtension.class)
  @SpringBootTest
  class 한_개만_등록 {

    @Autowired private RegisteredClientRepository registeredClientRepository;

    @Test
    void 콜백_URL이_하나면_그대로_등록된다() {
      RegisteredClient client = registeredClientRepository.findByClientId("chapchu-front");

      assertThat(client.getRedirectUris()).containsExactly("http://localhost:3000/login/callback");
    }
  }

  @Test
  void 콜백_URL이_비어_있으면_기동에_실패한다() {
    assertThatThrownBy(() -> new AuthorizationServerConfig().registeredClientRepository("  ,  "))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("최소 한 개의 콜백 URL");
  }
}
