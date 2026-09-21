package com.pettrip.auth.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthUserServiceTest {

  @Mock private AuthUserRepository authUserRepository;

  @Test
  @DisplayName("기존 google_user_id가 있으면 존재하는 유저를 반환한다")
  void returnsExistingUser() {
    AuthUser existing = new AuthUser("user@example.com", "google-123", "닉네임");
    when(authUserRepository.findByGoogleUserId("google-123")).thenReturn(Optional.of(existing));

    AuthUserService service = new AuthUserService(authUserRepository);
    Optional<AuthUser> result = service.findByGoogleUserId("google-123");

    assertThat(result).contains(existing);
    verify(authUserRepository, never()).save(any());
  }

  @Test
  @DisplayName("google_user_id가 없으면 Optional.empty를 반환한다")
  void returnsEmptyWhenMissing() {
    when(authUserRepository.findByGoogleUserId("google-456")).thenReturn(Optional.empty());

    AuthUserService service = new AuthUserService(authUserRepository);
    Optional<AuthUser> result = service.findByGoogleUserId("google-456");

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("createWithNickname은 닉네임을 포함한 새 유저를 저장한다")
  void createsNewUserWithNickname() {
    // saveAndFlush 를 쓴다. UNIQUE 위반을 이 호출 안에서 터뜨려야 409로 바꿀 수 있기 때문이다.
    when(authUserRepository.saveAndFlush(any(AuthUser.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    AuthUserService service = new AuthUserService(authUserRepository);
    AuthUser result = service.createWithNickname("google-789", "new@example.com", "짱구");

    assertThat(result.getGoogleUserId()).isEqualTo("google-789");
    assertThat(result.getEmail()).isEqualTo("new@example.com");
    assertThat(result.getNickname()).isEqualTo("짱구");
    assertThat(result.getRole()).isEqualTo(Role.USER);
    assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
  }
}
