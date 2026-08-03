FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src

# -x test 를 쓰면 안 된다. bootJar -> asciidoctorDocs -> test 순으로 의존하며,
# REST Docs 스니펫은 test가 만든다. 테스트를 건너뛰면 스니펫 디렉터리가 없어 빌드가 깨진다.
# deploy.yml에 별도 테스트 단계가 없으므로, 이 단계가 이 레포의 유일한 테스트 게이트이기도 하다.
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

RUN useradd --system chapchu
COPY --from=build /workspace/build/libs/*.jar app.jar
USER chapchu

EXPOSE 9000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
