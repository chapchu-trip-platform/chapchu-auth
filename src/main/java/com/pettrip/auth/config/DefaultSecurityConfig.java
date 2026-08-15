package com.pettrip.auth.config;

import com.pettrip.auth.oauth2.FederatedOidcUserService;
import com.pettrip.auth.oauth2.OnboardingAuthenticationFailureHandler;
import jakarta.servlet.DispatcherType;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * chapchu-auth 자체 로그인 화면(= Google 로그인으로 위임)에 대한 필터체인. Authorization Server 엔드포인트 (/oauth2/**,
 * /.well-known/**)는 {@link AuthorizationServerConfig}가 우선순위(Order 1)로 처리한다.
 */
@Configuration
public class DefaultSecurityConfig {

  @Bean
  @Order(2)
  public SecurityFilterChain defaultSecurityFilterChain(
      HttpSecurity http,
      FederatedOidcUserService federatedOidcUserService,
      OnboardingAuthenticationFailureHandler onboardingFailureHandler)
      throws Exception {
    http.authorizeHttpRequests(
            auth ->
                auth.dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD)
                    .permitAll()
                    .requestMatchers(
                        "/actuator/health",
                        "/actuator/health/**",
                        "/docs/**",
                        "/auth/register",
                        "/auth/registration-token/verify")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .cors(Customizer.withDefaults())
        .csrf(
            csrf ->
                csrf.ignoringRequestMatchers("/auth/register", "/auth/registration-token/verify"))
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                    new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/google")))
        .oauth2Login(
            login ->
                login
                    .userInfoEndpoint(
                        userInfo -> userInfo.oidcUserService(federatedOidcUserService))
                    .failureHandler(onboardingFailureHandler));

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource(
      @Value("${chapchu-auth.client.front-redirect-uri}") String frontRedirectUris) {
    List<String> origins =
        Arrays.stream(frontRedirectUris.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(
                uri -> {
                  try {
                    java.net.URI parsed = java.net.URI.create(uri);
                    return parsed.getScheme()
                        + "://"
                        + parsed.getHost()
                        + (parsed.getPort() == -1 ? "" : ":" + parsed.getPort());
                  } catch (Exception e) {
                    return uri;
                  }
                })
            .distinct()
            .toList();

    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(origins);
    config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(false);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/auth/register", config);
    return source;
  }
}
