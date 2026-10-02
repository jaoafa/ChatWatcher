FROM maven:3-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
COPY scripts/install-sherpa.sh scripts/install-sherpa.sh
RUN scripts/install-sherpa.sh
COPY src src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:25-jre
COPY --from=build /src/target/koemoji.jar /app/koemoji.jar
# models are downloaded on first start into /data/models (persisted with the /data volume)
ENV MODELS_DIR=/data/models VAD_MODEL=/data/models/silero_vad.onnx AUDIO_DIR=/data/audio QUEUE_DB=/data/queue.db
HEALTHCHECK --interval=30s --timeout=10s --start-period=180s --retries=3 \
  CMD ["java","-cp","/app/koemoji.jar","koemoji.HealthCheck"]
ENTRYPOINT ["java","--enable-native-access=ALL-UNNAMED","-jar","/app/koemoji.jar"]
