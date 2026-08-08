package com.pettrip.auth.user;

import com.pettrip.auth.oauth2.RegistrationTokenService;
import com.pettrip.auth.oauth2.RegistrationTokenService.RegistrationClaims;
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

  @PostMapping("/auth/register")
  public ResponseEntity<Void> register(@RequestBody RegisterRequest req) {
    RegistrationClaims claims = registrationTokenService.validateToken(req.registrationToken());
    authUserService.createWithNickname(claims.googleUserId(), claims.email(), req.nickname());
    return ResponseEntity.ok().build();
  }

  public record RegisterRequest(String registrationToken, String nickname) {}
}
