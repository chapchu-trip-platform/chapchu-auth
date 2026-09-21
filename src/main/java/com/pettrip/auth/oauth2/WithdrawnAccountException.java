package com.pettrip.auth.oauth2;

import org.springframework.security.core.AuthenticationException;

/**
 * 탈퇴한 계정({@code users.is_withdrawn = true})의 로그인 시도.
 *
 * <p>탈퇴는 되돌릴 수 있는 상태가 아니므로 재가입 안내 대신 차단만 한다. {@link OnboardingAuthenticationFailureHandler}가 403
 * {@code WITHDRAWN_ACCOUNT}로 바꾼다.
 */
public class WithdrawnAccountException extends AuthenticationException {

  public WithdrawnAccountException() {
    super("Withdrawn account cannot be authenticated");
  }
}
