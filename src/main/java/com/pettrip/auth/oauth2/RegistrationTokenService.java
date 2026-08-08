package com.pettrip.auth.oauth2;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 신규 유저 온보딩 전 임시 상태를 전달하는 단수명 토큰. HMAC-SHA256 서명으로 변조를 방지하고 10분 TTL을 강제한다.
 *
 * <p>토큰 형식: {@code base64url(googleUserId|email|expiresAt)}.{@code base64url(hmac)}
 */
@Service
public class RegistrationTokenService {

  private static final long TTL_MS = 10 * 60 * 1000L;
  private static final String HMAC_ALGO = "HmacSHA256";

  private final byte[] secretKey;

  public RegistrationTokenService(
      @Value("${chapchu-auth.registration-token.secret:}") String secret) {
    if (secret == null || secret.isBlank()) {
      this.secretKey = generateRandomKey();
    } else {
      this.secretKey = secret.getBytes(StandardCharsets.UTF_8);
    }
  }

  public String createToken(String googleUserId, String email) {
    long expiresAt = System.currentTimeMillis() + TTL_MS;
    String payload = googleUserId + "|" + email + "|" + expiresAt;
    String payloadB64 = base64Encode(payload);
    String sig = sign(payloadB64);
    return payloadB64 + "." + sig;
  }

  public RegistrationClaims validateToken(String token) {
    String[] parts = token.split("\\.", 2);
    if (parts.length != 2) {
      throw new InvalidRegistrationTokenException("malformed token");
    }
    String payloadB64 = parts[0];
    String sig = parts[1];
    if (!sig.equals(sign(payloadB64))) {
      throw new InvalidRegistrationTokenException("invalid signature");
    }
    String payload = base64Decode(payloadB64);
    String[] fields = payload.split("\\|", 3);
    if (fields.length != 3) {
      throw new InvalidRegistrationTokenException("malformed payload");
    }
    long expiresAt = Long.parseLong(fields[2]);
    if (System.currentTimeMillis() > expiresAt) {
      throw new InvalidRegistrationTokenException("token expired");
    }
    return new RegistrationClaims(fields[0], fields[1]);
  }

  private String sign(String data) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGO);
      mac.init(new SecretKeySpec(secretKey, HMAC_ALGO));
      byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    } catch (Exception e) {
      throw new IllegalStateException("HMAC signing failed", e);
    }
  }

  private static String base64Encode(String s) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(s.getBytes(StandardCharsets.UTF_8));
  }

  private static String base64Decode(String s) {
    return new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8);
  }

  private static byte[] generateRandomKey() {
    byte[] key = new byte[32];
    new java.security.SecureRandom().nextBytes(key);
    return key;
  }

  public record RegistrationClaims(String googleUserId, String email) {}

  public static class InvalidRegistrationTokenException extends RuntimeException {
    public InvalidRegistrationTokenException(String reason) {
      super("Invalid registration token: " + reason);
    }
  }
}
