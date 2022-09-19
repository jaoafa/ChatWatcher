FROM maven:3 as builder

WORKDIR /build
COPY pom.xml /build/pom.xml
RUN mvn -B package; echo ""

COPY src /build/src
RUN mvn -B package

FROM amazoncorretto:17-alpine

WORKDIR /app

COPY --from=builder /build/target/ChatWatcherRecorder-jar-with-dependencies.jar .

ENTRYPOINT []
CMD ["java", "-jar", "ChatWatcherRecorder-jar-with-dependencies.jar"]