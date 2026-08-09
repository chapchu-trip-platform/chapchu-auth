package com.pettrip.auth.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class OnboardingAuthenticationFailureHandler implements AuthenticationFailureHandler {

  private final String frontOnboardingUri;
  private final AuthenticationFailureHandler fallback =
      new SimpleUrlAuthenticationFailureHandler("/login?error");

  public OnboardingAuthenticationFailureHandler(
      @Value("${chapchu-auth.client.front-onboarding-uri}") String frontOnboardingUri) {
    this.frontOnboardingUri = frontOnboardingUri.split(",")[0].trim();
  }

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException, ServletException {
    if (exception instanceof NewUserRequiresOnboardingException onboardingEx) {
      String redirectUrl =
          frontOnboardingUri + "?registration_token=" + onboardingEx.getRegistrationToken();
      response.sendRedirect(redirectUrl);
      return;
    }
    fallback.onAuthenticationFailure(request, response, exception);
  }
}
