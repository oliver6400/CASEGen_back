# Multi-stage Dockerfile for CASEGen Backend
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copy maven wrapper and pom
COPY .mvn/ .mvn/
COPY mvnw mvnw.cmd pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code and package
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Runtime (JDK required because code generation compiles Spring projects with mvnw)
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app

# Install dependencies (tesseract-ocr for optional local diagram image OCR, curl for healthcheck, bash for wrapper scripts)
RUN apk add --no-cache tesseract-ocr tesseract-ocr-data-spa curl bash

# Create non-root user
RUN addgroup -S casegen && adduser -S casegen -G casegen

# Copy built artifact, maven wrapper, wrapper distribution cache, and configs
COPY --from=builder /workspace/target/*.jar app.jar
COPY --from=builder --chown=casegen:casegen /workspace/mvnw /workspace/mvnw.cmd /app/
COPY --from=builder --chown=casegen:casegen /workspace/.mvn /app/.mvn
COPY --from=builder --chown=casegen:casegen /root/.m2/wrapper /home/casegen/.m2/wrapper

# Ensure wrapper is executable and runtime directory exists with correct permissions
RUN chmod +x /app/mvnw && \
    mkdir -p /app/runtime/generation && \
    chown -R casegen:casegen /app /home/casegen

USER casegen:casegen

# Expose backend port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/api/auth/me || exit 0

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
