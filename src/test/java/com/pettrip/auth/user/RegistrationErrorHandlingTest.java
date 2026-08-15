package com.pettrip.auth.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pettrip.auth.oauth2.RegistrationTokenService;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 온보딩에서 무엇이 잘못됐든 전부 500이 나가던 문제를 막는다.
 *
 * <p>프론트는 "닉네임이 중복이니 다시 입력하세요"와 "세션이 만료됐으니 다시 로그인하세요"를 구분할 수 없었다. 응답 본문도 {@code Internal Server
 * Error}뿐이라 로그를 봐야만 원인을 알 수 있었다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegistrationErrorHandlingTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private RegistrationTokenService registrationTokenService;

  private String body(String token, String nickname) throws Exception {
    Map<String, String> map = new HashMap<>();
    map.put("registrationToken", token);
    map.put("nickname", nickname);
    return objectMapper.writeValueAsString(map);
  }

  private void register(String token, String nickname, int expectedStatus, String expectedCode)
      throws Exception {
    mockMvc
        .perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(token, nickname)))
        .andExpect(status().is(expectedStatus))
        .andExpect(jsonPath("$.code").value(expectedCode));
  }

  @Test
  @DisplayName("형식이 깨진 토큰은 401")
  void malformedToken() throws Exception {
    register("점이-없는-문자열", "닉네임A", 401, "INVALID_REGISTRATION_TOKEN");
  }

  @Test
  @DisplayName("서명이 맞지 않는 토큰은 401")
  void badSignature() throws Exception {
    String token = registrationTokenService.createToken("google-err-001", "err1@example.com");
    String tampered = token.substring(0, token.indexOf('.')) + ".다른서명";

    register(tampered, "닉네임B", 401, "INVALID_REGISTRATION_TOKEN");
  }

  @Test
  @DisplayName("닉네임이 비어 있으면 400")
  void blankNickname() throws Exception {
    String token = registrationTokenService.createToken("google-err-002", "err2@example.com");

    register(token, "", 400, "INVALID_REQUEST");
  }

  /** users.nickname은 VARCHAR(30)이다. 검증이 없으면 DB까지 내려가 500이 된다. */
  @Test
  @DisplayName("닉네임이 30자를 넘으면 400")
  void tooLongNickname() throws Exception {
    String token = registrationTokenService.createToken("google-err-003", "err3@example.com");

    register(token, "가".repeat(31), 400, "INVALID_REQUEST");
  }

  @Test
  @DisplayName("이미 가입된 계정으로 다시 가입하면 409")
  void alreadyRegistered() throws Exception {
    String token = registrationTokenService.createToken("google-err-004", "err4@example.com");

    mockMvc
        .perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(token, "처음닉네임")))
        .andExpect(status().isOk());

    register(token, "두번째닉네임", 409, "ALREADY_REGISTERED");
  }
}
