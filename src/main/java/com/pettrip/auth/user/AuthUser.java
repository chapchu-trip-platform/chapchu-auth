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

  @Enumerated(EnumType.STRING)
  @Column(length = 20)
  private Role role;

  @Enumerated(EnumType.STRING)
  @Column(name = "account_status", length = 20)
  private AccountStatus accountStatus;

  protected AuthUser() {}

  public AuthUser(String email, String googleUserId) {
    this.email = email;
    this.googleUserId = googleUserId;
    this.role = Role.USER;
    this.accountStatus = AccountStatus.ACTIVE;
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

  public AccountStatus getAccountStatus() {
    return accountStatus;
  }
}
