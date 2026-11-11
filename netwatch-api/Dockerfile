# NetWatch API — imagem de produção (Java 21).
# Build:  docker build -t netwatch-api ./netwatch-api

# ---------- 1. Compilação ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Dependências primeiro: ficam em cache enquanto o pom.xml não mudar.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src src
RUN ./mvnw -q -B -DskipTests package \
    && cp target/netwatch-api-*.jar /app/app.jar

# ---------- 2. Execução ----------
FROM eclipse-temurin:21-jre

# libpcap: captura de pacotes (só funciona com rede do host + permissões; ver docker-compose.linux-host.yml)
# curl: verificação de saúde do container
RUN apt-get update \
    && apt-get install -y --no-install-recommends libpcap0.8 curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /app/app.jar app.jar

ENV NETWATCH_PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fs http://localhost:${NETWATCH_PORT}/actuator/health || exit 1

# Roda como root para permitir a captura de pacotes quando o container recebe NET_RAW/NET_ADMIN.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
