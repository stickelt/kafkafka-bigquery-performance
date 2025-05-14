FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradle gradle
COPY gradlew .
COPY build.gradle .
COPY settings.gradle .
COPY src src

RUN chmod +x gradlew

RUN ./gradlew build -x test

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

# Create a directory for credentials
RUN mkdir -p /app/config

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
