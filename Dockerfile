FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /build
COPY pom.xml /build/pom.xml
RUN mvn -B dependency:go-offline

COPY src /build/src
RUN mvn -B package

FROM eclipse-temurin:25.0.3_9-jre

WORKDIR /app

COPY --from=builder /build/target/ChatWatcher-jar-with-dependencies.jar .

ENTRYPOINT []
CMD ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "ChatWatcher-jar-with-dependencies.jar"]
