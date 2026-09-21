package com.pettrip.auth.user;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * chapchu-api의 {@code users} 테이블을 공유 매핑한다. 스키마 소유권은 chapchu-api의 Flyway에 있으므로 이 엔티티는 로그인 흐름에 필요한
 * 컬럼만 매핑하고, DDL은 생성하지 않는다 (spring.jpa.hibernate.ddl-auto=none).
 */
@Entity
@Table(name = "users")
public class AuthUser {

  @Id
  @Column(name = "user_id")
  private UUID id = UuidCreator.getTimeOrderedEpoch();

  @Column(name = "google_user_id", unique = true)
  private String googleUserId;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(length = 30)
  private String nickname;

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private Role role;

  /** 탈퇴 여부. true면 토큰을 발급하지 않는다. 컬럼 소유권은 chapchu-api의 Flyway(V38)에 있다. */
  @Column(name = "is_withdrawn", nullable = false)
  private boolean isWithdrawn = false;

  protected AuthUser() {}

  public AuthUser(String email, String googleUserId, String nickname) {
    this.email = email;
    this.googleUserId = googleUserId;
    this.nickname = nickname;
    this.role = Role.USER;
  }

  public UUID getId() {
    return id;
  }

  public String getGoogleUserId() {
    return googleUserId;
  }

  public String getEmail() {
    return email;
  }

  public Role getRole() {
    return role;
  }

  public String getNickname() {
    return nickname;
  }

  public boolean isWithdrawn() {
    return isWithdrawn;
  }

  /** 테스트·관리 목적의 상태 전이. 실제 탈퇴 처리는 chapchu-api의 {@code PATCH /users/me}가 한다. */
  public void withdraw() {
    this.isWithdrawn = true;
  }
}
