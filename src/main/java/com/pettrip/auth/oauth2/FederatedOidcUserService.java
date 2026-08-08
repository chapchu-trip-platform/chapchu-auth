package com.pettrip.auth.oauth2;

import com.pettrip.auth.user.AuthUser;
import com.pettrip.auth.user.AuthUserService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * Google 로그인 성공 시 chapchu-api의 users 테이블에서 내부 user_id를 조회하고, 이후 토큰 발급 단계 ({@link
 * JwtClaimsCustomizer})에서 꺼내 쓸 수 있도록 claims에 실어 둔다.
 *
 * <p>신규 유저(DB에 없는 google_user_id)라면 {@link NewUserRequiresOnboardingException}을 던져 온보딩 플로우로 분기한다.
 * {@link OnboardingAuthenticationFailureHandler}가 이를 받아 FE 온보딩 페이지로 리다이렉트한다.
 */
@Service
public class FederatedOidcUserService extends OidcUserService {

  public static final String INTERNAL_USER_ID_CLAIM = "internal_user_id";
  public static final String ROLE_CLAIM = "role";

  private final AuthUserService authUserService;
  private final RegistrationTokenService registrationTokenService;

  public FederatedOidcUserService(
      AuthUserService authUserService, RegistrationTokenService registrationTokenService) {
    this.authUserService = authUserService;
    this.registrationTokenService = registrationTokenService;
  }

  @Override
  public OidcUser loadUser(OidcUserRequest userRequest) {
    OidcUser oidcUser = super.loadUser(userRequest);

    String googleUserId = oidcUser.getSubject();
    String email = oidcUser.getEmail();

    Optional<AuthUser> existing = authUserService.findByGoogleUserId(googleUserId);
    if (existing.isEmpty()) {
      String token = registrationTokenService.createToken(googleUserId, email);
      throw new NewUserRequiresOnboardingException(token);
    }

    AuthUser authUser = existing.get();
    Map<String, Object> claims = new LinkedHashMap<>(oidcUser.getClaims());
    claims.put(INTERNAL_USER_ID_CLAIM, authUser.getId().toString());
    claims.put(ROLE_CLAIM, authUser.getRole().name());

    OidcUserInfo userInfo = new OidcUserInfo(claims);
    return new DefaultOidcUser(oidcUser.getAuthorities(), oidcUser.getIdToken(), userInfo);
  }
}
