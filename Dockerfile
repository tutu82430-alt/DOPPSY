# ==============================================================================
# Stage 1: Build Spring Boot JAR with Maven
# ==============================================================================
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Cache Maven dependency downloads
COPY pom.xml ./
RUN mvn dependency:go-offline -B || true

# Copy source and build package skipping unit/integration tests
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==============================================================================
# Stage 2: Production JRE Alpine Runtime
# ==============================================================================
FROM eclipse-temurin:17-jre-alpine AS runner

WORKDIR /app

# Create unprivileged application user
RUN addgroup -S doppsy && adduser -S doppsy -G doppsy

# Copy generated executable jar from builder stage
COPY --from=builder /app/target/*.jar app.jar

RUN chown doppsy:doppsy app.jar
USER doppsy

# Expose default Spring Boot port
EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
