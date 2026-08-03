package com.pettrip.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** 공개돼야 하는 경로가 로그인 뒤로 숨지 않는지 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
class PublicEndpointAccessTest {

  @Autowired private MockMvc mockMvc;

  /**
   * index.html은 {@code asciidoctorDocs} -> {@code bootJar} 단계에서야 static 리소스로 들어가므로 테스트 시점에는 파일이 없다.
   * 따라서 200을 기대할 수 없고, "로그인을 요구당하지 않는다"는 것만 검증한다. 302(로그인 리다이렉트)나 401이면 실패한다.
   */
  @Test
  void API_문서_경로는_인증을_요구하지_않는다() throws Exception {
    mockMvc
        .perform(get("/docs/index.html"))
        .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotIn(302, 401, 403));
  }

  @Test
  void 헬스체크는_로그인_없이_호출할_수_있다() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }
}
