# chapchu-auth

PetTrip 인증 서버. Spring Authorization Server로 구현하며, Google OAuth2 로그인을 위임받아 chapchu-api가 신뢰하는 자체 JWT(Access/Refresh Token)를 발급한다.

세부 아키텍처 결정은 [`docs/decisions/`](docs/decisions/) 참고 (chapchu-api의 `docs/decisions/008-auth-server-separation.md`, `docs/decisions/004-auth-storage-embedding.md`와 대응).

## 흐름

```
브라우저/앱 → chapchu-auth /oauth2/authorize (client_id=chapchu-front)
                ↓ (미인증 시) 로그인 페이지 → Google OAuth2 로그인으로 위임
                ↓ Google 로그인 성공
        chapchu-auth: chapchu-api users 테이블에서 google_user_id로 get-or-create
                ↓
        chapchu-auth: 자체 JWT 발급 (sub = users.user_id, UUID v7)
                ↓
        chapchu-api (Resource Server): issuer-uri 메타데이터 + JWKS로 서명 검증만 수행 (chapchu-auth 호출 없음)
```

## 로컬 실행

필요한 환경변수:

| 변수 | 설명 |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | chapchu-api와 **동일한** Postgres (users 테이블 공유) |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Google Cloud Console에서 발급한 OAuth 2.0 클라이언트 |
| `AUTH_SERVER_URL` | 이 서버 자신의 issuer URL. 로컬은 `http://localhost:9000`. chapchu-api의 `issuer-uri`와 반드시 동일해야 함 |
| `FRONT_REDIRECT_URI` | 프론트가 인가 코드를 받을 콜백 URL (기본값 `http://localhost:3000/login/callback`). **쉼표로 구분해 여러 개** 등록 가능 — 로컬 개발 주소와 배포 주소를 함께 넣으면 된다 |
| `AUTH_RSA_PRIVATE_KEY`, `AUTH_RSA_PUBLIC_KEY` | (선택) JWT 서명용 고정 RSA 키. 비우면 매 재시작마다 새로 생성됨 — **로컬 개발에서만 비워두고 운영에서는 반드시 채울 것** |

Google Cloud Console에서 OAuth 클라이언트 생성 시 **승인된 리디렉션 URI**에 아래를 등록해야 한다:
- 로컬: `http://localhost:9000/login/oauth2/code/google`
- 운영: `${AUTH_SERVER_URL}/login/oauth2/code/google`

```bash
export DB_URL=jdbc:postgresql://localhost:5432/chapchu
export DB_USERNAME=chapchu
export DB_PASSWORD=chapchu
export GOOGLE_CLIENT_ID=...
export GOOGLE_CLIENT_SECRET=...
export AUTH_SERVER_URL=http://localhost:9000
export FRONT_REDIRECT_URI=http://localhost:3000/login/callback

./gradlew bootRun
```

메타데이터 확인: `curl http://localhost:9000/.well-known/oauth-authorization-server`

## 빌드/테스트

```bash
./gradlew spotlessApply check
```

## API 문서

로그인·회원등록 흐름과 각 엔드포인트 명세는 REST Docs로 관리한다 (`docs/decisions/005` 참고).

- 배포본: https://auth.chapchu.site/docs/index.html
- 로컬 생성: `./gradlew asciidoctorDocs` → `build/docs/asciidoc/index.html`
- OpenAPI 3.0 JSON: `./gradlew openapi3` → `build/api-spec/openapi3.json`

`bootJar`가 HTML을 `static/docs`로 넣으므로 서버를 띄우면 바로 열람할 수 있다.

> **회원가입 API는 따로 없다.** 처음 로그인하는 구글 계정은 `AuthUserService.findOrCreate()`가
> 그 자리에서 `users`에 등록한다. 이때 `nickname`은 비어 있으므로, 프론트는 로그인 직후
> chapchu-api의 `GET /users/me`로 확인하고 비어 있으면 닉네임 등록 화면으로 보내야 한다.

## Docker

```bash
docker build -t ghcr.io/chapchu-trip-platform/chapchu-auth:latest .
docker push ghcr.io/chapchu-trip-platform/chapchu-auth:latest
```

## k3s 배포 (단일 EC2)

1. EC2에 SSH로 접속해 `infra/k3s/install-k3s.sh` 실행 (k3s 단일 노드 설치)
2. 로컬에서 kubeconfig를 받아 `KUBECONFIG`로 지정 (스크립트 출력 참고)
3. `k8s/namespace.yaml` 적용
4. `k8s/secret.example.yaml`을 복사해 `k8s/secret.yaml`을 만들고 실제 값 채운 뒤 적용 (커밋 금지, `.gitignore`에 포함됨)
5. `k8s/configmap.yaml`의 `AUTH_SERVER_URL`/`FRONT_REDIRECT_URI`를 EC2 퍼블릭 IP(또는 도메인)로 채운 뒤 적용
   - `FRONT_REDIRECT_URI`에 **실제로 쓰는 콜백 주소를 빠짐없이** 넣어라 (쉼표 구분). 등록되지 않은 주소로 인가 요청이 오면 에러 메시지 없이 구글 로그인만 거친 뒤 아무 데도 도달하지 못해 원인 파악이 어렵다
6. `k8s/deployment.yaml`, `k8s/service.yaml` 적용

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
```

접속 확인: `http://<EC2_PUBLIC_IP>:30900/.well-known/oauth-authorization-server`

도메인이 생기면 `k8s/ingress.yaml.example`을 참고해 Traefik Ingress(k3s 기본 포함)로 전환하고 `service.yaml`을 `ClusterIP`로 바꿔라.

## 알려진 한계 (추후 개선)

- `RegisteredClientRepository`가 인메모리 — 파드 재시작 시 클라이언트 등록이 리셋됨. 등록 클라이언트가 늘어나면 별도 테이블(JDBC 구현)로 전환 필요.
- RSA 키를 Secret으로 고정하지 않으면 파드 재시작마다 키가 바뀌어 기존 발급 토큰이 전부 무효화됨.
- k3s 단일 노드 + 단일 replica — 가용성 연습용 구성이며 실제 무중단 배포를 보장하지 않음.
