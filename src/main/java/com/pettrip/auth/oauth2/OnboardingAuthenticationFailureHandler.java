package com.pettrip.auth.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

@Component
public class OnboardingAuthenticationFailureHandler implements AuthenticationFailureHandler {

  private static final String CLIENT_ID = "chapchu-api";

  private final RegisteredClientRepository registeredClientRepository;
  private final RequestCache requestCache = new HttpSessionRequestCache();
  private final AuthenticationFailureHandler fallback =
      new SimpleUrlAuthenticationFailureHandler("/login?error");

  public OnboardingAuthenticationFailureHandler(
      RegisteredClientRepository registeredClientRepository) {
    this.registeredClientRepository = registeredClientRepository;
  }

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException, ServletException {
    if (exception instanceof NewUserRequiresOnboardingException onboardingEx) {
      String redirectUri = resolveRedirectUri(request, response);
      if (redirectUri == null) {
        fallback.onAuthenticationFailure(request, response, exception);
        return;
      }
      response.sendRedirect(
          redirectUri + "?registration_token=" + onboardingEx.getRegistrationToken());
      return;
    }
    fallback.onAuthenticationFailure(request, response, exception);
  }

  private String resolveRedirectUri(HttpServletRequest request, HttpServletResponse response) {
    SavedRequest saved = requestCache.getRequest(request, response);
    if (saved == null) return null;

    String[] values = saved.getParameterMap().get("redirect_uri");
    if (values == null || values.length == 0) return null;

    String uri = values[0];
    return isAllowed(uri) ? uri : null;
  }

  private boolean isAllowed(String uri) {
    RegisteredClient client = registeredClientRepository.findByClientId(CLIENT_ID);
    return client != null && client.getRedirectUris().contains(uri);
  }
}
