package com.pettrip.auth.user;

import com.pettrip.auth.oauth2.RegistrationTokenService;
import com.pettrip.auth.oauth2.RegistrationTokenService.RegistrationClaims;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RegistrationController {

  private final RegistrationTokenService registrationTokenService;
  private final AuthUserService authUserService;

  public RegistrationController(
      RegistrationTokenService registrationTokenService, AuthUserService authUserService) {
    this.registrationTokenService = registrationTokenService;
    this.authUserService = authUserService;
  }

  /** 닉네임만 받아 유저를 만든다. 선호 사항·반려동물까지 한 번에 받는 통합 가입은 chapchu-api가 처리한다. */
  @PostMapping("/auth/register")
  public ResponseEntity<Void> register(@RequestBody @Valid RegisterRequest req) {
    RegistrationClaims claims = registrationTokenService.validateToken(req.registrationToken());
    authUserService.createWithNickname(claims.googleUserId(), claims.email(), req.nickname());
    return ResponseEntity.ok().build();
  }

  /**
   * registration token을 검증만 하고 담긴 정보를 돌려준다. <b>아무것도 만들지 않는다.</b>
   *
   * <p>chapchu-api가 통합 회원가입({@code POST /auth/signup})을 처리할 때 쓴다. 유저·선호 사항·반려동물은 모두 api쪽 스키마라 api가
   * 한 트랜잭션으로 써야 하는데, 토큰을 서명한 HMAC 비밀키는 이 서버에만 있다.
   *
   * <p>비밀키를 두 레포에 복사하지 않는 이유: 키를 회전할 때 반드시 한쪽이 뒤처지고, 그러면 로그인한 신규 유저가 가입 단계에서 막힌다. 대신 이 엔드포인트는 <b>읽기
   * 전용</b>이라 api가 실패해도 되돌릴 상태가 남지 않는다. 같은 토큰으로 그대로 재시도할 수 있다.
   */
  @PostMapping("/auth/registration-token/verify")
  public VerifyResponse verify(@RequestBody @Valid VerifyRequest req) {
    RegistrationClaims claims = registrationTokenService.validateToken(req.registrationToken());
    return new VerifyResponse(claims.googleUserId(), claims.email());
  }

  public record RegisterRequest(
      @NotBlank String registrationToken, @NotBlank @Size(max = 30) String nickname) {}

  public record VerifyRequest(@NotBlank String registrationToken) {}

  public record VerifyResponse(String googleUserId, String email) {}
}
