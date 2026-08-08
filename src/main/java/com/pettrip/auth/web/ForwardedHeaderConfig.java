package com.pettrip.auth.web;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * 기본 {@link ForwardedHeaderFilter} 대신 {@link CloudflareForwardedSchemeFilter}를 등록한다.
 *
 * <p>Spring Boot는 {@code forward-headers-strategy: FRAMEWORK}일 때 {@code ForwardedHeaderFilter}를
 * {@code HIGHEST_PRECEDENCE}로 등록한다. 별도 필터를 앞에 세우려 해도 우선순위가 동률이라 순서를 보장할 수 없다. 그래서 앞에 끼워 넣는 대신 <b>그
 * 필터 자체를 우리 구현으로 교체한다.</b> Boot의 자동 등록은
 * {@code @ConditionalOnMissingFilterBean(ForwardedHeaderFilter.class)}이므로 이 빈이 있으면 물러난다.
 */
@Configuration
public class ForwardedHeaderConfig {

  @Bean
  public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
    FilterRegistrationBean<ForwardedHeaderFilter> registration =
        new FilterRegistrationBean<>(new CloudflareForwardedSchemeFilter());
    registration.setDispatcherTypes(
        DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }
}
