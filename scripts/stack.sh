#!/usr/bin/env bash
#
# Sterowanie kontenerowa warstwa serwerowa LoyaltyClub (baza + backend).
# Wszystko dzieje sie w Dockerze - na hoscie nie sa potrzebne ani JDK,
# ani Maven.
#
# Uzycie: scripts/stack.sh <polecenie>
#
#   build   - zbudowanie obrazu backendu
#   test    - testy jednostkowe w kontenerze; raporty do target/docker-reports
#   sonar   - analiza SonarQube (wymaga SONAR_TOKEN w srodowisku)
#   up      - zbudowanie i uruchomienie calosci, z czekaniem na gotowosc
#   down    - zatrzymanie (dane bazy zostaja)
#   destroy - zatrzymanie razem z wolumenem bazy
#   logs    - biezace logi
#   ps      - stan uslug
#   smoke   - sprawdzenie, czy dzialajaca instancja odpowiada
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

COMPOSE="docker compose"
IMAGE="${BACKEND_IMAGE:-loyaltyclub/backend}:${IMAGE_TAG:-local}"
BACKEND_HOST_PORT="${BACKEND_HOST_PORT:-8089}"
# Czas na migracje Liquibase przy pierwszym starcie na pustej bazie.
READY_TIMEOUT="${READY_TIMEOUT:-180}"

log() { printf '\n==== %s\n' "$1"; }

require_docker() {
    docker info >/dev/null 2>&1 || {
        echo "BLAD: demon Dockera nie odpowiada." >&2
        exit 1
    }
}

# Czeka, az compose oznaczy backend jako healthy. Sonda siedzi w obrazie
# (HEALTHCHECK), wiec tu tylko odpytujemy stan kontenera.
wait_for_health() {
    local name="loyalty-backend" waited=0 status
    log "Czekam na gotowosc backendu (limit ${READY_TIMEOUT}s)"
    while [ "$waited" -lt "$READY_TIMEOUT" ]; do
        status="$(docker inspect -f '{{.State.Health.Status}}' "$name" 2>/dev/null || echo brak)"
        case "$status" in
            healthy)
                echo "Backend gotowy po ${waited}s."
                return 0
                ;;
            brak)
                echo "BLAD: kontener $name nie istnieje." >&2
                return 1
                ;;
        esac
        # Kontener, ktory sie wywrocil, nie stanie sie healthy - konczymy od razu.
        if [ "$(docker inspect -f '{{.State.Running}}' "$name" 2>/dev/null)" = "false" ]; then
            echo "BLAD: kontener $name przestal dzialac. Logi:" >&2
            docker logs --tail 60 "$name" >&2 || true
            return 1
        fi
        sleep 3
        waited=$((waited + 3))
    done
    echo "BLAD: backend nie zglosil gotowosci w ${READY_TIMEOUT}s. Logi:" >&2
    docker logs --tail 80 "$name" >&2 || true
    return 1
}

cmd_build() {
    require_docker
    log "Budowanie obrazu $IMAGE"
    docker build --target runtime -t "$IMAGE" .
}

cmd_test() {
    require_docker
    log "Testy jednostkowe w kontenerze"
    # --output wyciaga raporty z etapu test-reports do katalogu na hoscie,
    # zeby CI (junit, pokrycie) mial je poza obrazem.
    rm -rf target/docker-reports
    docker build --target test-reports --output "type=local,dest=target/docker-reports" .
    echo "Raporty: target/docker-reports"
}

cmd_sonar() {
    require_docker
    if [ -z "${SONAR_TOKEN:-}" ]; then
        echo "BLAD: brak zmiennej SONAR_TOKEN." >&2
        exit 1
    fi
    log "Analiza SonarQube"
    # --no-cache-filter wymusza ponowna analize takze wtedy, gdy warstwa
    # z wynikiem poprzedniego przebiegu siedzi jeszcze w cache.
    # -o type=cacheonly: interesuje nas wyslanie raportu, nie obraz.
    docker build \
        --target sonar \
        --no-cache-filter sonar \
        --secret id=sonar_token,env=SONAR_TOKEN \
        ${SONAR_HOST_URL:+--build-arg SONAR_HOST_URL="$SONAR_HOST_URL"} \
        -o type=cacheonly \
        .
}

cmd_up() {
    require_docker
    log "Budowanie i uruchamianie warstwy serwerowej"
    $COMPOSE up -d --build
    wait_for_health
    cmd_smoke
    log "Gotowe"
    echo "API:     http://localhost:${BACKEND_HOST_PORT}"
    echo "Swagger: http://localhost:${BACKEND_HOST_PORT}/swagger-ui.html"
}

cmd_down() {
    require_docker
    log "Zatrzymywanie (wolumen bazy zostaje)"
    $COMPOSE down
}

cmd_destroy() {
    require_docker
    log "Zatrzymywanie razem z danymi bazy"
    $COMPOSE down -v
}

cmd_logs() { require_docker; $COMPOSE logs -f --tail 100 "${@:-}"; }
cmd_ps()   { require_docker; $COMPOSE ps; }

cmd_smoke() {
    require_docker
    log "Test dymny"
    # Odpytujemy z wnetrza sieci kontenerow - sprawdza to takze te sciezke,
    # ktora realnie uzywa frontend.
    docker run --rm --network loyaltyclub-net curlimages/curl:8.11.1 \
        -fsS -o /dev/null -w 'health: HTTP %{http_code}\n' \
        http://loyalty-backend:8089/actuator/health
}

case "${1:-}" in
    build)   shift; cmd_build "$@" ;;
    test)    shift; cmd_test "$@" ;;
    sonar)   shift; cmd_sonar "$@" ;;
    up)      shift; cmd_up "$@" ;;
    down)    shift; cmd_down "$@" ;;
    destroy) shift; cmd_destroy "$@" ;;
    logs)    shift; cmd_logs "$@" ;;
    ps)      shift; cmd_ps "$@" ;;
    smoke)   shift; cmd_smoke "$@" ;;
    *)
        sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
        exit 1
        ;;
esac
