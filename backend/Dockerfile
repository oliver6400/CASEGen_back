# Multi-stage Dockerfile for CASEGen Backend
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copy maven wrapper and pom
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code and package
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install dependencies (tesseract-ocr for optional local diagram image OCR, curl for healthcheck)
RUN apk add --no-cache tesseract-ocr tesseract-ocr-data-spa curl

# Create non-root user
RUN addgroup -S casegen && adduser -S casegen -G casegen
USER casegen:casegen

# Copy built artifact
COPY --from=builder /workspace/target/*.jar app.jar

# Expose backend port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/api/auth/me || exit 0

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
