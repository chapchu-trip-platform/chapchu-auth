# 001. Dockerfile의 `-x test`가 REST Docs 빌드를 깨뜨린다

## 증상

REST Docs 도입(PR #8) 직후 main 배포가 실패했다. 로컬 `./gradlew clean build`는 통과했는데 Docker 빌드만 깨졌다.

```
Reason: An input file was expected to be present but it doesn't exist.
> Task :asciidoctorDocs FAILED
ERROR: process "/bin/sh -c chmod +x gradlew && ./gradlew bootJar --no-daemon -x test" did not complete successfully
```

## 원인

태스크 의존 관계가 이렇다.

```
bootJar -> asciidoctorDocs -> test
                 |
                 +-- inputs.dir build/generated-snippets  (test가 만든다)
```

REST Docs 스니펫은 **테스트가 실행되면서** 생성된다.
Dockerfile이 `-x test`로 테스트를 제외하니 `build/generated-snippets`가 만들어지지 않았고,
`asciidoctorDocs`가 선언한 입력 디렉터리가 없어 Gradle이 빌드를 중단했다.

로컬 검증에서 놓친 이유는 `./gradlew clean build`만 돌렸기 때문이다. `build`는 `test`를 포함하므로 이 경로를 재현하지 못한다.

## 해결

Dockerfile에서 `-x test`를 제거했다.

```dockerfile
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon
```

부수 효과로 테스트 게이트가 하나 생겼다. `deploy.yml`에는 별도 테스트 단계가 없어서
**이 Docker 빌드가 이 레포의 유일한 테스트 실행 지점**이다. 테스트를 건너뛰면 검증 없이 배포된다.

## 대안을 쓰지 않은 이유

`inputs.dir(snippetsDir).optional(true)`나 `onlyIf { snippetsDir.exists() }`로 넘길 수도 있었다.
그러나 그러면 스니펫이 없는 채로 문서가 생성돼 **`include`가 해소되지 않은 문서가 조용히 배포된다.**
chapchu-api에서 실제로 이 일이 벌어져 배포된 문서에 `Unresolved directive` 17개가 노출된 적이 있다.
빌드가 시끄럽게 실패하는 편이 낫다.

## 에이전트 행동 지침

- 빌드 산출물을 입력으로 받는 태스크를 추가하면, **CI/Dockerfile이 그 산출물을 만드는 태스크를 건너뛰지 않는지** 확인하라.
- 로컬 검증은 `./gradlew clean build`만으로 충분하지 않다. **배포 파이프라인이 실제로 실행하는 명령**을 그대로 돌려보라.
  이 레포는 `./gradlew bootJar --no-daemon`이다.
