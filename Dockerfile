# Build stage
FROM eclipse-temurin:17-jdk-focal AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests

# Run stage
FROM eclipse-temurin:17-jre-focal
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8085
CMD ["java", "-jar", "app.jar"]
