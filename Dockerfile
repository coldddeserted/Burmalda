# Burmalda: one container with the Spring Boot backend + the static frontend.
# Build:  docker build -t burmalda .
# Run:    docker run -p 8080:8080 -e PAYPAL_CLIENT_ID=... -e PAYPAL_CLIENT_SECRET=... -e AI_API_KEY=... burmalda

# ---------- 1. build the backend jar ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend/pom.xml .
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
RUN mvn -q -B -DskipTests package && mv target/burmalda-backend-*.jar app.jar

# ---------- 2. run ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/app.jar app.jar
# the frontend lives in the repository root; the backend serves it from FRONTEND_DIR
COPY index.html login.html employee.html accountant.html ./frontend/
COPY css ./frontend/css
COPY js ./frontend/js
ENV FRONTEND_DIR=/app/frontend \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8080
# the H2 database is created in /app/data with fresh demo data on every start
CMD ["java", "-jar", "app.jar"]
