# 002. ERROR 디스패치를 permitAll 하지 않아 400/404가 구글 로그인 리다이렉트로 바뀜

## 증상

잘못된 인가 요청을 보내면 **에러 메시지 없이 구글 로그인 화면으로 넘어간다.**

```
GET /oauth2/authorize?client_id=nonexistent-client&...
  → 302 Location: /oauth2/authorization/google;jsessionid=...
```

없는 정적 리소스도 마찬가지다.

```
GET /docs/nonexistent.html
  → 302 Location: /oauth2/authorization/google;jsessionid=...
```

프론트 입장에서는 "로그인은 되는 것 같은데 아무 일도 안 일어난다"로만 보이고,
무엇이 잘못됐는지 알 방법이 없다. 오타 URL 하나에 사용자가 구글 로그인으로 끌려간다.

## 원인

서버는 **정상적으로 400/404를 만들고 있었다.** 문제는 그 다음이다.

에러가 나면 서블릿 컨테이너가 `/error`로 forward 하는데, 이 ERROR 디스패치도 시큐리티 필터를 다시 탄다
(Spring Security 6부터 `shouldFilterAllDispatcherTypes` 기본값이 true).
`/error`는 `permitAll` 대상이 아니므로 `anyRequest().authenticated()`에 걸리고,
이 서버의 인증 진입점은 등록된 OAuth2 클라이언트가 구글 하나뿐이라 **곧바로 구글 로그인으로 리다이렉트**한다.

즉 **제대로 만들어진 에러 응답이 통째로 사라지고 로그인 리다이렉트로 바뀐다.**

로그를 보면 한 요청이 두 번 시큐리티를 타는 것이 보인다.

```
DEBUG FilterChainProxy : Securing GET /docs/nonexistent.html
DEBUG FilterChainProxy : Securing GET /error          ← ERROR 디스패치. 여기서 막힌다
```

## 교차검증

수정 유무만 다르게 하고 같은 요청을 보내 원인을 확정했다.

| 요청 | 수정 전 | 수정 후 |
|---|---|---|
| `/oauth2/authorize` 없는 client_id | 302 → google | **400** |
| `/oauth2/authorize` 미등록 redirect_uri | 302 → google | **400** |
| `/oauth2/authorize` response_type=token | 302 → google | **400** |
| `/docs/nonexistent.html` | 302 → google | **404** |
| `/some-protected-path` (보호 대상) | 302 → google | 302 → google (변화 없음, 정상) |
| 로그인 흐름 전체 | 정상 | 정상 (회귀 없음) |

`/actuator/health/nope`는 수정 전에도 404였다. actuator는 자체 핸들러가 응답을 만들어
`/error` forward를 타지 않기 때문이다. 즉 **`/error`를 거치는 경로만** 영향을 받는다.

## 해결

```java
.authorizeHttpRequests(auth ->
    auth.dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD)
        .permitAll()
        .requestMatchers("/actuator/health", "/actuator/health/**", "/docs/**").permitAll()
        .anyRequest().authenticated())
```

chapchu-api `docs/failures/022`와 같은 수정이다. 다만 증상은 이쪽이 더 나쁘다.
api에서는 404가 401로 바뀌는 데 그쳤지만, 여기서는 OAuth 에러가 로그인 플로우로 바뀌어 원인 추적이 불가능해진다.

## 테스트가 잡지 못했던 이유

**MockMvc는 에러 페이지 forward를 일으키지 않는다.** 404/400에서 멈추므로 ERROR 디스패치가 발생하지 않고,
따라서 이 버그를 재현하지 못한다. 실제로 chapchu-api에서 같은 이유로 놓쳤다.

`ErrorDispatchAccessTest`는 `RANDOM_PORT`로 **실제 서블릿 컨테이너**를 띄워 검증한다.
수정을 제거하면 4건 중 3건이 실패하는 것을 확인했다.

리다이렉트를 따라가는 클라이언트도 쓰면 안 된다. `TestRestTemplate`은 302를 따라가
**실제 구글까지 호출하고 200을 받아온다.** 그래서 보호 경로 검증만 `setInstanceFollowRedirects(false)`를 쓴다.

## 에이전트 행동 지침

- 시큐리티 설정을 새로 만들거나 고칠 때 **ERROR/FORWARD 디스패치를 permitAll 했는지 먼저 확인하라.**
- 시큐리티 동작을 MockMvc로만 검증하지 마라. 에러 응답이 걸린 검증은 반드시 실제 컨테이너에서 돌려라.
- "에러 없이 엉뚱한 곳으로 리다이렉트된다"는 신고를 받으면 **에러 디스패치를 가장 먼저 의심하라.**
  서버가 에러를 안 만든 것이 아니라, 만든 에러가 가려진 것일 수 있다.
