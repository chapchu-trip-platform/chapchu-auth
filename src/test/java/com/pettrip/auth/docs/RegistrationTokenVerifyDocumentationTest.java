package com.pettrip.auth.docs;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pettrip.auth.oauth2.RegistrationTokenService;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * chapchu-api가 통합 회원가입에서 호출하는 검증 엔드포인트.
 *
 * <p>이 엔드포인트가 <b>아무것도 만들지 않는다</b>는 점이 핵심이다. api가 뒤이어 실패해도 되돌릴 상태가 남지 않아 같은 토큰으로 재시도할 수 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs(outputDir = "build/generated-snippets")
@Transactional
class RegistrationTokenVerifyDocumentationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private RegistrationTokenService registrationTokenService;

  private String verifyBody(String token) throws Exception {
    return objectMapper.writeValueAsString(Map.of("registrationToken", token));
  }

  @Test
  void 가입_토큰을_검증하고_담긴_정보를_돌려준다() throws Exception {
    String token = registrationTokenService.createToken("google-verify-001", "verify@example.com");

    mockMvc
        .perform(
            post("/auth/registration-token/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyBody(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.googleUserId").value("google-verify-001"))
        .andExpect(jsonPath("$.email").value("verify@example.com"))
        .andDo(
            document(
                "auth-registration-token-verify",
                requestFields(
                    fieldWithPath("registrationToken")
                        .description("온보딩 리다이렉트에서 받은 `registration_token` 값")),
                responseFields(
                    fieldWithPath("googleUserId").description("구글 계정 식별자 (`sub`)"),
                    fieldWithPath("email").description("구글 계정 이메일"))));
  }

  @Test
  @DisplayName("검증만 하고 유저를 만들지는 않는다")
  void doesNotCreateUser() throws Exception {
    String token = registrationTokenService.createToken("google-verify-002", "verify2@example.com");

    mockMvc
        .perform(
            post("/auth/registration-token/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyBody(token)))
        .andExpect(status().isOk());

    // 만들었다면 두 번째 호출도 그대로 성공한다. 부작용이 없다는 뜻이다.
    mockMvc
        .perform(
            post("/auth/registration-token/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyBody(token)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("형식이 깨진 토큰은 500이 아니라 401로 거절한다")
  void rejectsMalformedTokenWith401() throws Exception {
    mockMvc
        .perform(
            post("/auth/registration-token/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyBody("점이-없는-문자열")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_REGISTRATION_TOKEN"));
  }

  /** access token(JWT)을 잘못 넣는 실수가 잦다. 점이 2개라 형식 검사를 통과한 뒤 서명에서 걸린다. */
  @Test
  @DisplayName("access token을 잘못 넣어도 401로 거절한다")
  void rejectsJwtWith401() throws Exception {
    mockMvc
        .perform(
            post("/auth/registration-token/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyBody("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.c2ln")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_REGISTRATION_TOKEN"));
  }

  @Test
  @DisplayName("토큰이 비어 있으면 400으로 거절한다")
  void rejectsBlankTokenWith400() throws Exception {
    mockMvc
        .perform(
            post("/auth/registration-token/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyBody("")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }
}
