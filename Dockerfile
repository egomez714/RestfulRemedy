# --- Stage 1: build the jar ---
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build

# Copy Maven wrapper + pom first so dependency download is cached
# across rebuilds when only source changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src ./src
RUN ./mvnw -B -DskipTests package

# --- Stage 2: runtime image ---
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as non-root for security.
RUN useradd --system --uid 1001 spring
USER spring

COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
