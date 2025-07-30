# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy the Maven project files
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline

# Copy the source code
COPY src ./src

# Build the application and create the JAR file
# gRPC-related files will also be generated during this process.
RUN mvn clean install -DskipTests

# Stage 2: Create the final, lightweight image
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy the executable JAR from the build stage
COPY --from=build /app/target/schedule-0.0.1-SNAPSHOT.jar ./app.jar

# Expose the gRPC server port
EXPOSE 9090

# Set the entrypoint to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
