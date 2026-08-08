package com.pettrip.auth.user;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthUserService {

  private final AuthUserRepository authUserRepository;

  public AuthUserService(AuthUserRepository authUserRepository) {
    this.authUserRepository = authUserRepository;
  }

  @Transactional(readOnly = true)
  public Optional<AuthUser> findByGoogleUserId(String googleUserId) {
    return authUserRepository.findByGoogleUserId(googleUserId);
  }

  @Transactional
  public AuthUser createWithNickname(String googleUserId, String email, String nickname) {
    return authUserRepository.save(new AuthUser(email, googleUserId, nickname));
  }
}
