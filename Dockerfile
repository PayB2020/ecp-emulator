# Сборка: jar собирается внутри образа — на хосте не нужны JDK/Maven.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# Рантайм: только JRE 21 и boot-jar.
FROM eclipse-temurin:21-jre
COPY --from=build /build/target/ecp-emulator-*.jar /ecp.jar
EXPOSE 9094
ENTRYPOINT ["java", "-jar", "/ecp.jar"]
