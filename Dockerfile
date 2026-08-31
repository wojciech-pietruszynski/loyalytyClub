# syntax=docker/dockerfile:1.7
#
# Budowanie i uruchamianie backendu LoyaltyClub w calosci w Dockerze.
# Etapy:
#   deps    - pobranie zaleznosci Mavena (osobna warstwa, zeby zmiana kodu
#             nie unieważniała cache zaleznosci)
#   test    - testy jednostkowe + raport JaCoCo; cel dla potoku CI
#   sonar   - analiza SonarQube na wynikach etapu test
#   build   - zbudowanie wykonywalnego JAR-a
#   runtime - obraz produkcyjny z samym JRE
#
# Uzycie:
#   docker build -t loyaltyclub/backend:local .
#   docker build --target test .            # sama bramka testowa

# ---------------------------------------------------------------- deps
FROM maven:3.9-eclipse-temurin-25 AS deps
WORKDIR /build

# Najpierw sam pom - warstwa z zaleznosciami przebudowuje sie tylko wtedy,
# gdy zmieni sie opis projektu, a nie przy kazdej zmianie w src/.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2/repository,sharing=locked \
    mvn -B -ntp -DskipTests dependency:go-offline

COPY src ./src

# ---------------------------------------------------------------- test
# Osobny cel, zeby CI mogl uruchomic testy bez budowania obrazu wykonawczego.
# Wyniki (surefire, jacoco) trafiaja do /build/target i sa wyciagane przez
# skrypt CI poleceniem "docker build --target test --output".
FROM deps AS test
RUN --mount=type=cache,target=/root/.m2/repository,sharing=locked \
    mvn -B -ntp test

FROM scratch AS test-reports
COPY --from=test /build/target/surefire-reports /surefire-reports
COPY --from=test /build/target/site/jacoco /jacoco

# --------------------------------------------------------------- sonar
# Analiza dziedziczy system plikow etapu "test", wiec korzysta ze skompilowanych
# klas i gotowego raportu JaCoCo zamiast uruchamiac testy po raz drugi.
# Token idzie przez sekret BuildKita, a nie ARG - ARG zostalby w historii obrazu.
#
#   docker build --target sonar --secret id=sonar_token,env=SONAR_TOKEN .
FROM test AS sonar
# Puste = adres z pom.xml.
ARG SONAR_HOST_URL=
RUN --mount=type=cache,target=/root/.m2/repository,sharing=locked \
    --mount=type=secret,id=sonar_token \
    mvn -B -ntp sonar:sonar \
        -Dsonar.token="$(cat /run/secrets/sonar_token)" \
        ${SONAR_HOST_URL:+-Dsonar.host.url=${SONAR_HOST_URL}}

# --------------------------------------------------------------- build
FROM deps AS build
# Domyslnie pomijamy testy - w potoku odpowiada za nie cel "test",
# a lokalnie liczy sie czas budowania obrazu.
ARG SKIP_TESTS=true
RUN --mount=type=cache,target=/root/.m2/repository,sharing=locked \
    mvn -B -ntp clean package -DskipTests=${SKIP_TESTS} \
    && cp target/*.jar /build/app.jar

# ------------------------------------------------------------- runtime
FROM eclipse-temurin:25-jre-alpine AS runtime

# Aplikacja nie potrzebuje roota; wlasny uzytkownik ogranicza skutki
# ewentualnego wyjscia poza proces.
RUN addgroup -S app && adduser -S -G app app

WORKDIR /app
COPY --from=build --chown=app:app /build/app.jar /app/app.jar

USER app

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC" \
    SERVER_PORT=8089

EXPOSE 8089

# Sonda actuatora: kontener jest "healthy" dopiero, gdy Spring wystawi
# UP. Dzieki temu docker compose potrafi czekac na gotowosc backendu,
# a nie tylko na otwarty port.
HEALTHCHECK --interval=10s --timeout=5s --start-period=90s --retries=12 \
    CMD wget -qO /dev/null http://127.0.0.1:8089/actuator/health || exit 1

# exec => java dostaje PID 1 i odbiera SIGTERM przy "docker stop".
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
