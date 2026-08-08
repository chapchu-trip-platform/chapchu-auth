# 003. Traefik이 X-Forwarded-Proto를 덮어써 HTTPS 요청이 http 리다이렉트를 낳음

## 증상

HTTPS로 들어온 인가 요청이 **HTTP로 리다이렉트**된다.

```
GET https://auth.chapchu.site/oauth2/authorize?...
  → 302 Location: http://auth.chapchu.site/login          ← https여야 한다
     Set-Cookie: JSESSIONID=...; Path=/; HttpOnly          ← Secure 없음
```

로그인 도중 평문 구간이 생기고, 세션 쿠키가 그 위로 오간다.
HTTPS-Only 모드를 쓰는 브라우저에서는 경고나 차단이 날 수 있다.

## 원인

```
Client → (HTTPS) → Cloudflare → (HTTP) → Traefik → (HTTP) → Spring
```

Cloudflare는 `X-Forwarded-Proto: https`를 붙이지만, **Traefik이 자신이 받은 연결 프로토콜(HTTP) 기준으로 이 헤더를 덮어쓴다.**
`server.forward-headers-strategy: FRAMEWORK`는 덮어쓰인 `http`를 신뢰하므로 리다이렉트 URL이 http로 생성된다.

chapchu-api `docs/failures/020`에서 같은 원인으로 Google redirect_uri가 깨졌고, 그때는 `GOOGLE_REDIRECT_URI`
환경변수로 그 URL만 고정해 막았다. 앱이 스스로 만드는 나머지 URL은 그대로 남아 있었다.

## 해결

### 1. Cloudflare가 알려주는 원래 스킴을 복원

`CF-Visitor`는 Cloudflare가 붙이는 별도 헤더라 **Traefik이 건드리지 않는다.**

```
CF-Visitor: {"scheme":"https"}
```

이 값이 https면 `X-Forwarded-Proto`를 https로 되돌린 뒤 표준 처리에 넘긴다.

### 2. 필터를 앞에 세우지 말고 교체한다

처음에는 `ForwardedHeaderFilter` 앞에 별도 필터를 두려 했으나 **동작하지 않았다.**
Spring Boot는 `ForwardedHeaderFilter`를 `Ordered.HIGHEST_PRECEDENCE`로 등록하는데,
우리 필터도 그보다 앞설 수는 없어(=같은 값) 순서가 보장되지 않았다.

그래서 **필터 자체를 우리 구현으로 교체했다.** Boot의 자동 등록은
`@ConditionalOnMissingFilterBean(ForwardedHeaderFilter.class)` 조건이라 우리 빈이 있으면 물러난다.

### 3. 헤더 래퍼는 세 메서드를 모두 덮어야 한다

`getHeader()`만 덮어썼을 때도 동작하지 않았다. `ForwardedHeaderFilter`는 헤더를
`getHeaders()` / `getHeaderNames()`로 **열거해서** 읽는다. 원본에 헤더가 아예 없을 수도 있으므로
이름 목록에도 넣어야 한다.

### 4. 쿠키 Secure는 별도 설정

스킴을 교정해도 **세션 쿠키에 `Secure`가 붙지 않았다.** Tomcat은 래핑된 요청이 아니라
실제 커넥터를 기준으로 판단하기 때문이다. 명시적으로 켜야 한다.

```yaml
server.servlet.session.cookie:
  secure: ${SESSION_COOKIE_SECURE:false}   # 운영 configmap에서 true
  same-site: lax
```

로컬은 http라서 켜면 쿠키가 전송되지 않아 로그인이 안 된다. 그래서 기본값은 false다.
`SameSite`는 `lax`여야 한다. OAuth 콜백은 최상위 GET 이동이라 lax로도 쿠키가 전달되지만
`strict`는 흐름을 깨뜨린다.

## 검증

로컬에서 운영 조건을 재현해 확인했다.

| 요청 | Location | 쿠키 |
|---|---|---|
| 로컬 개발 (CF 헤더 없음) | `http://localhost:9000/login` | 변화 없음 |
| 운영 재현 (CF-Visitor https + XFP http) | **`https://auth.chapchu.site/login`** | **`Secure; HttpOnly; SameSite=Lax`** |
| Cloudflare에 평문 접속 (CF-Visitor http) | `http://auth.chapchu.site/login` | — |

로그인 흐름 회귀 없음(`/login` 200, 구글 위임 302, 잘못된 인가 요청 400).
교정 로직을 제거하면 테스트가 실패하는 것을 확인했다.

## 에이전트 행동 지침

- 리버스 프록시 뒤에서 URL이 잘못 생성되면 **어느 홉이 헤더를 덮어쓰는지** 먼저 확인하라.
  프록시가 여러 겹이면 마지막 프록시가 이긴다.
- Spring Boot가 `HIGHEST_PRECEDENCE`로 등록하는 필터보다 앞서려 하지 마라. **교체가 답이다.**
- 요청 헤더를 래핑할 때는 `getHeader()`만으로 부족하다. `getHeaders()`, `getHeaderNames()`까지 맞춰라.
- 스킴 교정과 쿠키 `Secure`는 별개다. 하나 고쳤다고 다른 하나가 따라오지 않는다.
