FROM maven:3 as builder

WORKDIR /build
COPY pom.xml /build/pom.xml
RUN mvn -B package; echo ""

COPY src /build/src
RUN mvn -B package

FROM amazoncorretto:17-alpine

RUN apk update && \
  apk add --no-cache \
  docker-cli

WORKDIR /app

COPY --from=builder /build/target/ChatWatcher-jar-with-dependencies.jar .

ENTRYPOINT []
CMD ["java", "-jar", "ChatWatcher-jar-with-dependencies.jar"]