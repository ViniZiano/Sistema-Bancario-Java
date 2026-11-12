# ---------- Etapa 1: build (Gradle gera o jar) ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app/JavaBank

# Wrapper e scripts primeiro: o Gradle baixado fica em cache enquanto eles não mudarem
COPY JavaBank/gradlew JavaBank/settings.gradle.kts JavaBank/build.gradle.kts ./
COPY JavaBank/gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon -q --version

COPY JavaBank/src ./src
RUN ./gradlew --no-daemon -q jar

# ---------- Etapa 2: runtime (só o JRE e o jar) ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Roda como usuário sem privilégios
RUN useradd --system --no-create-home appuser
COPY --from=build /app/JavaBank/build/libs/javabank.jar javabank.jar
USER appuser

# A hospedagem define a porta pela variável PORT (padrão 8080)
ENV PORT=8080
EXPOSE 8080
# -Xmx limita a memória: a demonstração tem tetos de valor para caber em planos gratuitos
ENTRYPOINT ["java", "-Xmx300m", "-cp", "javabank.jar", "br.com.vini.web.WebServer"]
