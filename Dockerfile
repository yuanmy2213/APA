FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml ./
COPY frontend ./frontend
COPY server ./server
RUN mvn -B -DskipTests package
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/target/finance-access-review-1.2.0.jar /app/review.jar
ENV BIND_ADDRESS=0.0.0.0
EXPOSE 8080
ENTRYPOINT ["java","-Xms128m","-Xmx1g","-jar","/app/review.jar","--spring.profiles.active=prod"]
