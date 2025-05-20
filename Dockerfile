FROM maven:3.9.4-eclipse-temurin-17 AS build

WORKDIR /app
COPY . .

RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jdk
WORKDIR /app

# Copy the built JAR
COPY --from=build /app/target/appointment-booking-0.0.1-SNAPSHOT.jar app.jar

# ✅ Create models directory and copy the model file into container
RUN mkdir -p /models
COPY src/main/resources/models/urgency_predictor.onnx /models/urgency_predictor.onnx

CMD ["java", "-jar", "app.jar"]
