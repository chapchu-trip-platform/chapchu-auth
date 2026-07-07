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
  @DisplayName("기존 google_user_id가 있으면 새로 생성하지 않고 그대로 반환한다")
  void returnsExistingUser() {
    AuthUser existing = new AuthUser("user@example.com", "google-123");
    when(authUserRepository.findByGoogleUserId("google-123")).thenReturn(Optional.of(existing));

    AuthUserService service = new AuthUserService(authUserRepository);
    AuthUser result = service.findOrCreate("google-123", "user@example.com");

    assertThat(result).isSameAs(existing);
    verify(authUserRepository, never()).save(any());
  }

  @Test
  @DisplayName("google_user_id가 없으면 새 사용자를 생성해 저장한다")
  void createsNewUserWhenMissing() {
    when(authUserRepository.findByGoogleUserId("google-456")).thenReturn(Optional.empty());
    when(authUserRepository.save(any(AuthUser.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    AuthUserService service = new AuthUserService(authUserRepository);
    AuthUser result = service.findOrCreate("google-456", "new@example.com");

    assertThat(result.getGoogleUserId()).isEqualTo("google-456");
    assertThat(result.getEmail()).isEqualTo("new@example.com");
    assertThat(result.getRole()).isEqualTo(Role.USER);
    assertThat(result.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
  }
}
