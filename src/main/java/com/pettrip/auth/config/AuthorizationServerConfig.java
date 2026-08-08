package com.pettrip.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class AuthorizationServerConfig {

  @Bean
  @Order(1)
  public SecurityFilterChain authorizationServerSecurityFilterChain(
      HttpSecurity http,
      @Value("${chapchu-auth.client.front-redirect-uri}") String frontRedirectUris)
      throws Exception {
    OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
    http.getConfigurer(OAuth2AuthorizationServerConfigurer.class).oidc(Customizer.withDefaults());

    http.exceptionHandling(
        exceptions ->
            exceptions.defaultAuthenticationEntryPointFor(
                new LoginUrlAuthenticationEntryPoint("/login"),
                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));

    http.cors(cors -> cors.configurationSource(authServerCorsSource(frontRedirectUris)));

    return http.build();
  }

  private static CorsConfigurationSource authServerCorsSource(String frontRedirectUris) {
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
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/oauth2/token", config);
    source.registerCorsConfiguration("/.well-known/**", config);
    return source;
  }

  /**
   * 프론트 콜백 URL은 쉼표로 구분해 여러 개 등록할 수 있다 (로컬 개발 주소 + 배포 주소 동시 운용).
   *
   * <p>등록되지 않은 {@code redirect_uri}로 인가 요청이 오면 <b>에러 없이</b> 구글 로그인으로 넘어간 뒤 아무 데도 도달하지 못한다. 원인을 알려주는
   * 메시지가 없어 디버깅이 매우 어려우므로, 사용할 콜백 주소는 빠짐없이 등록해 두어야 한다.
   */
  @Bean
  public RegisteredClientRepository registeredClientRepository(
      @Value("${chapchu-auth.client.front-redirect-uri}") String frontRedirectUris) {
    RegisteredClient.Builder frontClientBuilder =
        RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("chapchu-front")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            .scope(OidcScopes.EMAIL)
            .clientSettings(
                ClientSettings.builder()
                    .requireAuthorizationConsent(false)
                    .requireProofKey(true)
                    .build())
            .tokenSettings(
                TokenSettings.builder()
                    .accessTokenTimeToLive(Duration.ofMinutes(30))
                    .refreshTokenTimeToLive(Duration.ofDays(14))
                    .reuseRefreshTokens(false)
                    .build());

    parseRedirectUris(frontRedirectUris).forEach(frontClientBuilder::redirectUri);

    return new InMemoryRegisteredClientRepository(frontClientBuilder.build());
  }

  private static List<String> parseRedirectUris(String value) {
    List<String> uris =
        Arrays.stream(value.split(",")).map(String::trim).filter(uri -> !uri.isEmpty()).toList();

    if (uris.isEmpty()) {
      throw new IllegalStateException(
          "chapchu-auth.client.front-redirect-uri 가 비어 있다. 최소 한 개의 콜백 URL을 등록해야 한다.");
    }
    return uris;
  }

  /**
   * 인가 상태 저장소. 선언하지 않아도 Authorization Server가 내부적으로 동일한 구현을 쓰지만, 빈으로 노출해야 문서화 테스트에서 "로그인을 마친 상태"를
   * 주입할 수 있다. 단일 인스턴스 배포라 in-memory로 충분하며, 다중 레플리카로 늘릴 때 JDBC 구현으로 교체해야 한다.
   */
  @Bean
  public OAuth2AuthorizationService authorizationService() {
    return new InMemoryOAuth2AuthorizationService();
  }

  @Bean
  public JWKSource<SecurityContext> jwkSource(
      @Value("${chapchu-auth.jwt.rsa-private-key:}") String rsaPrivateKey,
      @Value("${chapchu-auth.jwt.rsa-public-key:}") String rsaPublicKey) {
    RSAKey rsaKey = Jwks.loadOrGenerateRsa(rsaPrivateKey, rsaPublicKey);
    JWKSet jwkSet = new JWKSet(rsaKey);
    return new ImmutableJWKSet<>(jwkSet);
  }

  @Bean
  public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
    return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
  }

  @Bean
  public AuthorizationServerSettings authorizationServerSettings(
      @Value("${chapchu-auth.issuer-uri}") String issuerUri) {
    return AuthorizationServerSettings.builder().issuer(issuerUri).build();
  }
}
