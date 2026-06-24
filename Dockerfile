FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/*.jar app.jar
ENV PORT=8080
EXPOSE ${PORT}
ENTRYPOINT ["java", "-jar", "app.jar"]
