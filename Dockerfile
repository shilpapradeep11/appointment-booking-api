FROM maven:3.9.4-eclipse-temurin-17 AS build

WORKDIR /app
COPY . .

RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jdk
WORKDIR /app

COPY --from=build /app/target/appointment-booking-0.0.1-SNAPSHOT.jar app.jar

CMD ["java", "-jar", "app.jar"]
