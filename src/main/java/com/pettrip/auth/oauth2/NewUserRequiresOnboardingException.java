package com.pettrip.auth.oauth2;

import org.springframework.security.core.AuthenticationException;

public class NewUserRequiresOnboardingException extends AuthenticationException {

  private final String registrationToken;

  public NewUserRequiresOnboardingException(String registrationToken) {
    super("New user must complete onboarding before authentication");
    this.registrationToken = registrationToken;
  }

  public String getRegistrationToken() {
    return registrationToken;
  }
}
