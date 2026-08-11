# Step 1: Build the Java App using Maven
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Step 2: Run the Java App
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/MovieTicketApp-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
