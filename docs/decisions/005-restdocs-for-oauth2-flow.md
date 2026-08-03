# 005. OAuth2 흐름을 REST Docs로 문서화

## 상태
- [x] 확정됨 (Accepted)

## 배경

프론트에서 "회원등록 기능이 없는 것 같다"는 문의가 나왔다. 실제로는 있다.
`AuthUserService.findOrCreate()`가 구글 로그인 시 `users` 테이블을 get-or-create 하므로,
**처음 로그인하는 계정은 그 자리에서 자동으로 회원등록된다.**

문제는 그 사실을 확인할 문서가 어디에도 없었다는 점이다.

- chapchu-api의 REST Docs에는 로그인/회원가입이 없다. 인증이 이 레포로 분리돼 있으니 당연한 결과다 (ADR 001, chapchu-api ADR 008).
- chapchu-auth에는 REST Docs 자체가 없었다.

결과적으로 "어느 문서에도 없으니 기능이 없다"는 오해가 생겼다.

## 결정

chapchu-auth에도 chapchu-api와 동일한 방식으로 REST Docs를 도입한다.

- 문서화 테스트(`src/test/java/com/pettrip/auth/docs/`)가 스니펫을 생성한다.
- `src/docs/asciidoc/index.adoc`이 스니펫을 엮어 최종 문서를 만든다.
- `com.epages.restdocs-api-spec`으로 OpenAPI 3.0 JSON도 함께 뽑는다 (chapchu-api PR #57과 동일).
- `bootJar`가 결과 HTML을 `static/docs`로 넣어 `https://auth.chapchu.site/docs/index.html`로 서빙한다.

### 표준 엔드포인트는 필드 서술자를 달지 않는다

디스커버리(`/.well-known/openid-configuration`)와 JWKS 응답은 OIDC/OAuth2 표준이 정의한 스키마이고,
Spring Authorization Server 버전이 올라가면 필드가 늘어난다.
필드 서술자를 달면 라이브러리를 올릴 때마다 테스트가 깨지므로 **응답 본문 스니펫만 남기고 해석은 표준 스펙에 맡긴다.**

우리가 정의한 계약(`/oauth2/token`, `/oauth2/authorize`)에는 서술자를 전부 단다.

### 흐름 설명을 문서 앞에 둔다

로그인·회원등록은 브라우저 리다이렉트 기반이라 개별 엔드포인트 명세만으로는 사용법을 알 수 없다.
`index.adoc` 최상단에 순서도를 두고 자동 회원등록 시점과 닉네임 후속 처리를 명시한다.

### HTML 변환은 Gradle 안에서 처리한다

chapchu-api는 `org.asciidoctor.jvm.convert` 플러그인이 Gradle 9를 지원하지 않아
(chapchu-api `docs/failures/004`) CI에서 asciidoctor CLI를 직접 설치해 실행한다.

chapchu-auth는 대신 `asciidoctorj-cli`를 별도 configuration으로 잡고 `JavaExec`로 띄운다.
플러그인을 쓰지 않으므로 Gradle 9에서 동작하고, **CI 워크플로 수정 없이 `./gradlew build`만으로 문서가 만들어진다.**

JRuby가 JNI를 쓰므로 `--enable-native-access=ALL-UNNAMED`를 붙여 Java 25 경고를 없앴다.

## 부수 변경

- `OAuth2AuthorizationService`를 빈으로 노출했다. 기존에도 내부적으로 같은 구현(`InMemoryOAuth2AuthorizationService`)을
  쓰고 있었으나 빈이 아니어서 주입할 수 없었다. 문서화 테스트가 "로그인을 마친 상태"를 주입하려면 필요하다. 동작 변화는 없다.
- `/docs/**`를 `permitAll`에 추가했다. 문서가 로그인 뒤에 숨으면 의미가 없다.

## 발견된 문제 (별도 처리 필요)

문서화 과정에서 **refresh token이 발급되지 않는다**는 사실이 드러났다.

`chapchu-front`는 `ClientAuthenticationMethod.NONE`인 public client인데,
Spring Authorization Server의 `OAuth2RefreshTokenGenerator.isPublicClientForAuthorizationCodeGrant()`가
authorization_code 그랜트를 쓰는 public client에 대해 refresh token 생성을 건너뛴다.

`RegisteredClient`에 선언된 `REFRESH_TOKEN` 그랜트와 `refreshTokenTimeToLive(14일)`는 동작하지 않는 설정이며,
**유저는 30분마다 재로그인해야 한다.**

해결하려면 클라이언트를 confidential로 바꾸거나 BFF를 두어야 하는데, 이는 프론트 아키텍처가 걸린 판단이라
이 ADR에서 결정하지 않고 별도 이슈로 넘긴다. 현재 문서와 테스트는 **실제 동작대로** 기록해 두었다.

## 에이전트 행동 지침

- 엔드포인트를 추가하면 `src/test/java/com/pettrip/auth/docs/`에 문서화 테스트를 함께 작성하고
  `index.adoc`에 절을 추가하라. 스니펫만 만들고 adoc에 넣지 않으면 문서에 나타나지 않는다.
- 표준 OAuth2/OIDC 엔드포인트에는 필드 서술자를 달지 마라. 위의 이유로 깨진다.
- 토큰 응답 형태를 바꾸는 변경(클라이언트 인증 방식 변경 등)을 하면
  `TokenEndpointDocumentationTest`가 실패한다. 테스트를 맞추기 전에 `index.adoc`의 경고 문구부터 갱신하라.
