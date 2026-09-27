FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
# fontes pro servidor desenhar o icone do app (iniciais da barbearia)
RUN apk add --no-cache fontconfig ttf-dejavu
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# plano gratuito do Render tem 512 MB: deixa a JVM usar 75% em vez dos 25% padrao
ENTRYPOINT ["java", "-Djava.awt.headless=true", "-XX:MaxRAMPercentage=75", "-XX:+UseSerialGC", "-Xss512k", "-jar", "app.jar", "--server.port=${PORT:8080}"]
