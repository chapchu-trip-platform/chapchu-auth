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

  /**
   * 신규 유저를 만든다.
   *
   * <p>{@code save} 대신 {@code saveAndFlush}를 쓰는 이유: UNIQUE 위반(이미 가입된 계정, 닉네임 중복)을 이 호출 안에서 터뜨려야 예외
   * 핸들러가 409로 바꿀 수 있다. {@code save}만 하면 위반이 트랜잭션 커밋 시점까지 미뤄지고, 이 메서드가 더 큰 트랜잭션에 묶이면 커밋이 컨트롤러 바깥에서
   * 일어나 500으로 새어나간다.
   */
  @Transactional
  public AuthUser createWithNickname(String googleUserId, String email, String nickname) {
    return authUserRepository.saveAndFlush(new AuthUser(email, googleUserId, nickname));
  }
}
