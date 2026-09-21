package com.pettrip.auth.oauth2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pettrip.auth.web.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
  private final ObjectMapper objectMapper;
  private final RequestCache requestCache = new HttpSessionRequestCache();
  private final AuthenticationFailureHandler fallback =
      new SimpleUrlAuthenticationFailureHandler("/login?error");

  public OnboardingAuthenticationFailureHandler(
      RegisteredClientRepository registeredClientRepository, ObjectMapper objectMapper) {
    this.registeredClientRepository = registeredClientRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException, ServletException {
    if (exception instanceof WithdrawnAccountException) {
      writeForbidden(response);
      return;
    }
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

  /**
   * 탈퇴 계정은 403으로 끊는다.
   *
   * <p>이 지점은 브라우저 리다이렉트 흐름이라 사용자는 JSON 본문을 그대로 보게 된다. 안내 화면이 필요하면 FE 콜백으로 에러 파라미터를 붙여 리다이렉트하는 방식으로
   * 바꿔야 한다.
   */
  private void writeForbidden(HttpServletResponse response) throws IOException {
    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(
        response.getWriter(), new ErrorResponse("WITHDRAWN_ACCOUNT", "탈퇴한 계정입니다."));
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
