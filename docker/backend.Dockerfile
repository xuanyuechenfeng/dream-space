FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /src
COPY dream_service/ ./
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre

ARG MODULE=api
WORKDIR /app

COPY --from=build /src/${MODULE}/target/*.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
