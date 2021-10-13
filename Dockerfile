FROM openjdk:11-slim as builder

WORKDIR /src
COPY . /src

# https://docs.spring.io/spring-boot/docs/current/reference/html/deployment.html#deployment.containers
RUN ./gradlew --no-daemon bootJar && \
  mkdir /build && \
  cd /build && \
  jar -xf /src/build/libs/*.jar && \
  rm -f /build/BOOT-INF/classes/application-dev.properties


FROM openjdk:11-jre-slim

RUN groupadd -r app && useradd --no-log-init -r -g app app
USER app:app
WORKDIR /app
EXPOSE 8080

COPY --from=builder /build /app

ENTRYPOINT ["java", "org.springframework.boot.loader.JarLauncher"]
