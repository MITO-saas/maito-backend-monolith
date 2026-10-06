# Stage 1: Builder
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace/app

# Copy Maven wrapper and configuration
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies offline layer
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B || true

# Copy source files and build packaged application
COPY src src
RUN ./mvnw clean package -DskipTests

# Extract Spring Boot 3.3.x layered JAR
RUN java -Djarmode=layertools -jar target/*.jar extract

# Stage 2: Production Runner
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Create unprivileged system group and user (UID/GID 10001)
RUN addgroup -g 10001 -S appgroup && \
    adduser -u 10001 -S appuser -G appgroup

# Copy extracted layers with strict non-root ownership
COPY --from=builder --chown=appuser:appgroup /workspace/app/dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /workspace/app/spring-boot-loader/ ./
COPY --from=builder --chown=appuser:appgroup /workspace/app/snapshot-dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /workspace/app/application/ ./

USER appuser
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]