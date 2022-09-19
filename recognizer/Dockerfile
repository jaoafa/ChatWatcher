FROM maven:3 as builder

WORKDIR /build
COPY pom.xml /build/pom.xml
RUN mvn -B package; echo ""

COPY src /build/src
RUN mvn -B package

FROM amazoncorretto:17-alpine

RUN apk update && \
  apk add --no-cache \
  libstdc++ opus

WORKDIR /app

COPY --from=builder /build/target/ChatWatcherRecognizer-jar-with-dependencies.jar .

ENTRYPOINT []
CMD ["java", "-jar", "ChatWatcherRecognizer-jar-with-dependencies.jar"]