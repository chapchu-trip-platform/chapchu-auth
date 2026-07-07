package com.pettrip.auth.oauth2;

import com.pettrip.auth.user.AuthUser;
import com.pettrip.auth.user.AuthUserService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * Google 로그인 성공 시 chapchu-api의 users 테이블에서 내부 user_id를 get-or-create하고, 이후 토큰 발급 단계 ({@link
 * JwtClaimsCustomizer})에서 꺼내 쓸 수 있도록 claims에 실어 둔다.
 */
@Service
public class FederatedOidcUserService extends OidcUserService {

  public static final String INTERNAL_USER_ID_CLAIM = "internal_user_id";
  public static final String ROLE_CLAIM = "role";

  private final AuthUserService authUserService;

  public FederatedOidcUserService(AuthUserService authUserService) {
    this.authUserService = authUserService;
  }

  @Override
  public OidcUser loadUser(OidcUserRequest userRequest) {
    OidcUser oidcUser = super.loadUser(userRequest);

    String googleUserId = oidcUser.getSubject();
    String email = oidcUser.getEmail();
    AuthUser authUser = authUserService.findOrCreate(googleUserId, email);

    Map<String, Object> claims = new LinkedHashMap<>(oidcUser.getClaims());
    claims.put(INTERNAL_USER_ID_CLAIM, authUser.getId().toString());
    claims.put(ROLE_CLAIM, authUser.getRole().name());

    OidcUserInfo userInfo = new OidcUserInfo(claims);
    return new DefaultOidcUser(oidcUser.getAuthorities(), oidcUser.getIdToken(), userInfo);
  }
}
