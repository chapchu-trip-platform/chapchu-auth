package com.pettrip.auth.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Cloudflare가 알려주는 원래 프로토콜을 {@code X-Forwarded-Proto}에 복원한다.
 *
 * <p>docs/failures/020 참고: 트래픽은 {@code Client → (HTTPS) Cloudflare → (HTTP) Traefik → (HTTP)
 * Spring} 으로 흐른다. Cloudflare는 {@code X-Forwarded-Proto: https}를 붙이지만 <b>Traefik이 자신이 받은 연결
 * 프로토콜(HTTP) 기준으로 이 헤더를 덮어쓴다.</b> 그래서 {@code forward-headers-strategy: FRAMEWORK}가 잘못된 값을 신뢰하게 되고,
 * 리다이렉트 URL이 {@code http://}로 생성되며 세션 쿠키에 {@code Secure}가 붙지 않는다.
 *
 * <p>{@code CF-Visitor}는 Cloudflare가 붙이는 별도 헤더라 Traefik이 건드리지 않는다. 이 값이 https면 {@code
 * X-Forwarded-Proto}를 https로 되돌려, 뒤따르는 {@code ForwardedHeaderFilter}가 올바른 스킴으로 동작하게 한다.
 *
 * <p>스킴이 https로 인식되면 리다이렉트 URL과 쿠키 {@code Secure} 플래그가 함께 해결된다. Tomcat은 {@code request.isSecure()}를
 * 기준으로 세션 쿠키에 {@code Secure}를 붙이기 때문이다.
 *
 * <p>Cloudflare를 거치지 않는 요청(로컬 개발, 클러스터 내부 호출)에는 {@code CF-Visitor}가 없으므로 아무 일도 하지 않는다.
 */
public class CloudflareForwardedSchemeFilter extends ForwardedHeaderFilter {

  static final String CF_VISITOR_HEADER = "CF-Visitor";
  static final String FORWARDED_PROTO_HEADER = "X-Forwarded-Proto";
  private static final String HTTPS_MARKER = "\"scheme\":\"https\"";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    HttpServletRequest corrected =
        indicatesHttps(request.getHeader(CF_VISITOR_HEADER))
            ? new HttpsForwardedRequest(request)
            : request;

    // 스킴을 교정한 뒤 표준 ForwardedHeaderFilter 동작(host/port/prefix 처리)에 넘긴다.
    super.doFilterInternal(corrected, response, filterChain);
  }

  private static boolean indicatesHttps(String cfVisitor) {
    return cfVisitor != null && cfVisitor.replace(" ", "").contains(HTTPS_MARKER);
  }

  /**
   * {@code X-Forwarded-Proto}만 https로 바꿔 보여주는 래퍼. 나머지 동작은 원본 그대로다.
   *
   * <p>{@code ForwardedHeaderFilter}는 헤더를 {@code getHeaders()}/{@code getHeaderNames()}로 열거해서 읽는다.
   * {@code getHeader()}만 덮어쓰면 반영되지 않으므로 셋 다 맞춰야 한다.
   */
  private static final class HttpsForwardedRequest extends HttpServletRequestWrapper {

    private HttpsForwardedRequest(HttpServletRequest request) {
      super(request);
    }

    @Override
    public String getHeader(String name) {
      if (FORWARDED_PROTO_HEADER.equalsIgnoreCase(name)) {
        return "https";
      }
      return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      if (FORWARDED_PROTO_HEADER.equalsIgnoreCase(name)) {
        return Collections.enumeration(List.of("https"));
      }
      return super.getHeaders(name);
    }

    /** 원본에 헤더가 아예 없을 수도 있으므로 이름 목록에도 반드시 넣어준다. */
    @Override
    public Enumeration<String> getHeaderNames() {
      Set<String> names = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
      names.removeIf(FORWARDED_PROTO_HEADER::equalsIgnoreCase);
      names.add(FORWARDED_PROTO_HEADER);
      return Collections.enumeration(names);
    }
  }
}
