package com.pettrip.auth.config;

import com.pettrip.auth.oauth2.FederatedOidcUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * chapchu-auth 자체 로그인 화면(= Google 로그인으로 위임)에 대한 필터체인. Authorization Server 엔드포인트 (/oauth2/**,
 * /.well-known/**)는 {@link AuthorizationServerConfig}가 우선순위(Order 1)로 처리한다.
 */
@Configuration
public class DefaultSecurityConfig {

  @Bean
  @Order(2)
  public SecurityFilterChain defaultSecurityFilterChain(
      HttpSecurity http, FederatedOidcUserService federatedOidcUserService) throws Exception {
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2Login(
            login ->
                login.userInfoEndpoint(
                    userInfo -> userInfo.oidcUserService(federatedOidcUserService)));

    return http.build();
  }
}
