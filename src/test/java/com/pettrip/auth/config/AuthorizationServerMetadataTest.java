package com.pettrip.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthorizationServerMetadataTest {

  @Autowired private TestRestTemplate restTemplate;

  @Test
  @DisplayName("/.well-known/oauth-authorization-server가 issuer 메타데이터를 노출한다")
  void exposesAuthorizationServerMetadata() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/.well-known/oauth-authorization-server", String.class);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).contains("\"issuer\":\"http://localhost:9000\"");
  }

  @Test
  @DisplayName("JWKS 엔드포인트가 공개 키를 노출한다")
  void exposesJwks() {
    ResponseEntity<String> response = restTemplate.getForEntity("/oauth2/jwks", String.class);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(response.getBody()).contains("\"keys\"");
  }
}
