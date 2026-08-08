package com.pettrip.auth.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class AuthUserRepositoryTest {

  @Autowired private AuthUserRepository authUserRepository;

  @Test
  @DisplayName("google_user_id로 저장된 사용자를 조회한다")
  void savesAndFindsByGoogleUserId() {
    authUserRepository.save(new AuthUser("user@example.com", "google-789", "테스트닉"));

    var found = authUserRepository.findByGoogleUserId("google-789");

    assertThat(found).isPresent();
    assertThat(found.get().getEmail()).isEqualTo("user@example.com");
  }
}
