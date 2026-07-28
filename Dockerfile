FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src

RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

RUN useradd --system chapchu
COPY --from=build /workspace/build/libs/*.jar app.jar
USER chapchu

EXPOSE 9000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
