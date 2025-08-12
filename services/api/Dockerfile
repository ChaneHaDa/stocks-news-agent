FROM gradle:8.5-jdk17-alpine AS build

WORKDIR /app

# Copy Gradle files
COPY build.gradle settings.gradle gradle.properties ./
COPY gradle ./gradle

# Download dependencies
RUN gradle dependencies --no-daemon

# Copy source code and build
COPY src ./src
RUN gradle bootJar --no-daemon

# Production stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy the built jar file
COPY --from=build /app/build/libs/news-agent-api.jar app.jar

EXPOSE 8000

CMD ["java", "-jar", "app.jar"]