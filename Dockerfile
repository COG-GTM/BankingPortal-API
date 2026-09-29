FROM openjdk:11-jdk-slim
RUN groupadd --system app && useradd --system --gid app --no-create-home app
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
