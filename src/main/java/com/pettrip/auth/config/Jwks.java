package com.pettrip.auth.config;

import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

/**
 * JWKS용 RSA 키 쌍을 만든다. {@code AUTH_RSA_PRIVATE_KEY}/{@code AUTH_RSA_PUBLIC_KEY} 환경변수(PKCS8/X509,
 * Base64, PEM 헤더 제외)가 주어지면 그 키를 그대로 사용해 재시작 후에도 기존 발급 토큰의 서명 검증이 깨지지 않도록 한다. 값이 없으면 (로컬 개발용) 매번 새
 * 키를 생성한다 — k3s 운영 환경에서는 반드시 고정 키를 Secret으로 주입하라.
 */
final class Jwks {

  private Jwks() {}

  static RSAKey loadOrGenerateRsa(String base64PrivateKey, String base64PublicKey) {
    if (base64PrivateKey != null
        && !base64PrivateKey.isBlank()
        && base64PublicKey != null
        && !base64PublicKey.isBlank()) {
      return loadRsa(base64PrivateKey, base64PublicKey);
    }
    return generateRsa();
  }

  private static RSAKey loadRsa(String base64PrivateKey, String base64PublicKey) {
    try {
      KeyFactory keyFactory = KeyFactory.getInstance("RSA");
      RSAPrivateKey privateKey =
          (RSAPrivateKey)
              keyFactory.generatePrivate(
                  new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64PrivateKey)));
      RSAPublicKey publicKey =
          (RSAPublicKey)
              keyFactory.generatePublic(
                  new X509EncodedKeySpec(Base64.getDecoder().decode(base64PublicKey)));
      return new RSAKey.Builder(publicKey)
          .privateKey(privateKey)
          .keyID(UUID.nameUUIDFromBytes(base64PublicKey.getBytes()).toString())
          .build();
    } catch (Exception e) {
      throw new IllegalStateException("AUTH_RSA_PRIVATE_KEY/AUTH_RSA_PUBLIC_KEY 파싱 실패", e);
    }
  }

  private static RSAKey generateRsa() {
    KeyPair keyPair = generateRsaKeyPair();
    RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
    RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
    return new RSAKey.Builder(publicKey)
        .privateKey(privateKey)
        .keyID(UUID.randomUUID().toString())
        .build();
  }

  private static KeyPair generateRsaKeyPair() {
    try {
      KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
      keyPairGenerator.initialize(2048);
      return keyPairGenerator.generateKeyPair();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
