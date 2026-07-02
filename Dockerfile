FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x mvnw && ./mvnw -q -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app
ARG JAR_FILE=/workspace/target/*.jar
COPY --from=build ${JAR_FILE} app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
