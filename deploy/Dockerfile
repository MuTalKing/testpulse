# Multi-stage build for testpulse-server. Build context is the repo root (see compose).
# Stage 1 builds the runnable distribution from source; stage 2 runs it on a slim JRE.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon :testpulse-server:installDist

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/testpulse-server/build/install/testpulse-server/ ./
EXPOSE 8080
ENTRYPOINT ["bin/testpulse-server"]
