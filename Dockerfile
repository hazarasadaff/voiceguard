
FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src/ src/

RUN ./mvnw -B package -DskipTests

EXPOSE 8080

CMD ["java", "-jar", "target/voiceguard-0.0.1-SNAPSHOT.jar"]
