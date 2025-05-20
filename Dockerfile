FROM maven:3.9.4-eclipse-temurin-17 AS build

WORKDIR /app
COPY . .

RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jdk
WORKDIR /app

# Copy the built JAR
COPY --from=build /app/target/appointment-booking-0.0.1-SNAPSHOT.jar app.jar

# ✅ Copy the ONNX model directory if needed
COPY src/main/resources/models/urgency_predictor.onnx /app/models/urgency_predictor.onnx

CMD ["java", "-jar", "app.jar"]
