FROM gradle:8-jdk21 AS build
WORKDIR /app
COPY server/ .
RUN gradle clean build -x check -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/server.jar .
EXPOSE 8080
CMD ["java", "-jar", "server.jar"]
