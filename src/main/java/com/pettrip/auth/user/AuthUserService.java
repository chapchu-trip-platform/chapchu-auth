package com.pettrip.auth.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthUserService {

  private final AuthUserRepository authUserRepository;

  public AuthUserService(AuthUserRepository authUserRepository) {
    this.authUserRepository = authUserRepository;
  }

  @Transactional
  public AuthUser findOrCreate(String googleUserId, String email) {
    return authUserRepository
        .findByGoogleUserId(googleUserId)
        .orElseGet(() -> authUserRepository.save(new AuthUser(email, googleUserId)));
  }
}
