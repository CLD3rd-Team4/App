# Stage 1 is removed, assuming the build is done in the CI/CD pipeline

# Stage 2: Create the final, lightweight, and secure image
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Add labels for better container management
LABEL maintainer="mapzip-team"
LABEL service="review"
LABEL version="1.0.0"

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Create a non-root user to run the application
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copy the executable JAR from the CI/CD build context
# The CI/CD pipeline should place the JAR file in the context directory
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar

# Change ownership to the appuser
RUN chown -R appuser:appuser /app

# Switch to the non-root user
USER appuser

# Expose the HTTP and gRPC server ports
EXPOSE 8080
EXPOSE 50051

# Health check for the application
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Set environment variables for runtime configuration
ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV SERVER_PORT=8080
ENV GRPC_SERVER_PORT=50051

# Set the entrypoint to run the application with specified Java options
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]