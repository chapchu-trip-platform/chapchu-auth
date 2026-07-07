# 004. JWT 서명용 RSA 키는 k8s Secret으로 고정, 없으면 임시 생성

## 상태
- [x] 확정됨 (Accepted)

## 결정
- `Jwks.loadOrGenerateRsa`: `AUTH_RSA_PRIVATE_KEY`/`AUTH_RSA_PUBLIC_KEY` 환경변수(PKCS8/X509, Base64)가 있으면 그 키로 JWKS를 구성한다. 없으면 애플리케이션 시작 시 새 RSA 키 쌍을 생성한다(로컬 개발용).
- k3s 배포에서는 `k8s/secret.example.yaml`을 통해 이 두 값을 반드시 채워 넣어야 한다.

## 이유
- 키를 코드/설정에 하드코딩하지 않으면서도, 파드가 재시작될 때마다 새 키가 생성되어 이미 발급된 JWT가 전부 서명 검증에 실패하는 문제를 막아야 한다.
- 로컬 개발에서는 매번 키를 발급받는 번거로움 없이 바로 실행 가능해야 한다.

## 에이전트 행동 지침
- 운영(k3s) 배포 시 `AUTH_RSA_PRIVATE_KEY`/`AUTH_RSA_PUBLIC_KEY`가 비어 있는 채로 배포하지 마라 — 재시작마다 전체 로그인 세션이 깨진다.
- 키 생성 명령은 `k8s/secret.example.yaml`의 주석 참고: `openssl genpkey ... | openssl pkcs8 -topk8 -nocrypt | base64 -w0`.
