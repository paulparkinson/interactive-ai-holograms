FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
RUN mvn -B -f pom.xml -q dependency:go-offline
COPY src ./src
RUN mvn -B -f pom.xml -q package -DskipTests

FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY --from=build /build/target/spring-app-0.0.1-SNAPSHOT.jar ./app.jar
EXPOSE 8080
CMD ["java","-jar","/app/app.jar"]
