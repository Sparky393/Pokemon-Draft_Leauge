# Preserve Maven 3.9.11; use only its Maven distribution, not its older Java/OS.
FROM maven:3.9.11-eclipse-temurin-21 AS maven-distribution

FROM eclipse-temurin:21-jdk-noble AS build
COPY --from=maven-distribution /usr/share/maven /usr/share/maven
ENV MAVEN_HOME=/usr/share/maven
ENV PATH="${MAVEN_HOME}/bin:${PATH}"

# Refresh build-time TLS trust before Maven downloads dependencies.
COPY docker/update-ca-trust.sh /tmp/update-ca-trust.sh
RUN sh /tmp/update-ca-trust.sh && rm /tmp/update-ca-trust.sh

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-noble

COPY docker/update-ca-trust.sh /tmp/update-ca-trust.sh
RUN sh /tmp/update-ca-trust.sh && rm /tmp/update-ca-trust.sh

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
