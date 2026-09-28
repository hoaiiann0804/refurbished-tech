FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY target/refurbished-backend-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar", "--spring.profiles.active=staging"]
