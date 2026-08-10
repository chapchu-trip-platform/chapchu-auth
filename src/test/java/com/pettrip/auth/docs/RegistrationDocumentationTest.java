package com.pettrip.auth.docs;

import static com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pettrip.auth.oauth2.RegistrationTokenService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs(outputDir = "build/generated-snippets")
@Transactional
class RegistrationDocumentationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private RegistrationTokenService registrationTokenService;

  @Test
  void 신규_유저를_등록한다() throws Exception {
    String token =
        registrationTokenService.createToken("google-doc-test-user-001", "doc-test@example.com");
    String body =
        objectMapper.writeValueAsString(Map.of("registrationToken", token, "nickname", "햇살여행자"));

    mockMvc
        .perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andDo(
            document(
                "auth-register",
                requestFields(
                    fieldWithPath("registrationToken")
                        .description(
                            "온보딩 리다이렉트에서 `registration_token` 쿼리 파라미터로 받은 값."
                                + " HMAC-SHA256 서명 토큰, 10분 TTL"),
                    fieldWithPath("nickname").description("사용할 닉네임. 최대 30자"))));
  }
}
