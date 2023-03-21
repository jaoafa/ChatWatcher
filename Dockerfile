FROM maven:3 as builder

WORKDIR /build
COPY pom.xml /build/pom.xml
RUN mvn -B package; echo ""

COPY src /build/src
RUN mvn -B package

FROM openjdk:17-alpine

WORKDIR /app

COPY --from=builder /build/target/ChatWatcher-jar-with-dependencies.jar .

ENTRYPOINT []
CMD ["java", "-jar", "ChatWatcher-jar-with-dependencies.jar"]