# Stage 1: Build the application using a slim base image
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml first for better layer caching
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy the source code
COPY src ./src

# Build the application and create the JAR file
RUN mvn clean install -DskipTests

# Stage 2: Create the final, lightweight, and secure image
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Add labels for better container management
LABEL maintainer="mapzip-team"
LABEL service="schedule"
LABEL version="1.0.0"

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Create a non-root user to run the application
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copy the executable JAR from the build stage
COPY --from=build /app/target/schedule-0.0.1-SNAPSHOT.jar ./app.jar

# Change ownership to the appuser
RUN chown -R appuser:appuser /app

# Switch to the non-root user
USER appuser

# Expose the HTTP and gRPC server ports
EXPOSE 8084
EXPOSE 9090

# Health check for the application
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8084/actuator/health || exit 1

# Set environment variables for runtime configuration
ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV SERVER_PORT=8084
ENV GRPC_SERVER_PORT=9090

# Set the entrypoint to run the application with specified Java options
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]