<#
.SYNOPSIS
    Sterowanie kontenerowa warstwa serwerowa LoyaltyClub (baza + backend).

.DESCRIPTION
    Odpowiednik scripts/stack.sh dla Windowsa. Calosc budowania i uruchamiania
    dzieje sie w Dockerze - na hoscie nie sa potrzebne ani JDK, ani Maven.

.PARAMETER Command
    build   - zbudowanie obrazu backendu
    test    - testy jednostkowe w kontenerze; raporty do target/docker-reports
    sonar   - analiza SonarQube (wymaga SONAR_TOKEN w srodowisku)
    up      - zbudowanie i uruchomienie calosci, z czekaniem na gotowosc
    down    - zatrzymanie (dane bazy zostaja)
    destroy - zatrzymanie razem z wolumenem bazy
    logs    - biezace logi
    ps      - stan uslug
    smoke   - sprawdzenie, czy dzialajaca instancja odpowiada

.EXAMPLE
    .\scripts\stack.ps1 up
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('build', 'test', 'sonar', 'up', 'down', 'destroy', 'logs', 'ps', 'smoke')]
    [string]$Command = 'up'
)

$ErrorActionPreference = 'Stop'

$RootDir = Split-Path -Parent $PSScriptRoot
Set-Location $RootDir

$Image = "$(if ($env:BACKEND_IMAGE) { $env:BACKEND_IMAGE } else { 'loyaltyclub/backend' }):$(if ($env:IMAGE_TAG) { $env:IMAGE_TAG } else { 'local' })"
$BackendPort = if ($env:BACKEND_HOST_PORT) { $env:BACKEND_HOST_PORT } else { '8089' }
# Czas na migracje Liquibase przy pierwszym starcie na pustej bazie.
$ReadyTimeout = if ($env:READY_TIMEOUT) { [int]$env:READY_TIMEOUT } else { 180 }

function Write-Step([string]$Text) { Write-Host "`n==== $Text" -ForegroundColor Cyan }

function Assert-LastExitCode([string]$What) {
    if ($LASTEXITCODE -ne 0) { throw "$What zakonczylo sie kodem $LASTEXITCODE" }
}

function Assert-Docker {
    docker info *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Demon Dockera nie odpowiada.' }
}

# Czeka, az kontener backendu zglosi stan healthy. Sonda siedzi w obrazie
# (HEALTHCHECK), wiec tu tylko odpytujemy stan kontenera.
function Wait-Health {
    $name = 'loyalty-backend'
    $waited = 0
    Write-Step "Czekam na gotowosc backendu (limit ${ReadyTimeout}s)"
    while ($waited -lt $ReadyTimeout) {
        $status = docker inspect -f '{{.State.Health.Status}}' $name 2>$null
        if ($LASTEXITCODE -ne 0) { throw "Kontener $name nie istnieje." }
        if ($status -eq 'healthy') {
            Write-Host "Backend gotowy po ${waited}s."
            return
        }
        # Kontener, ktory sie wywrocil, nie stanie sie healthy - konczymy od razu.
        $running = docker inspect -f '{{.State.Running}}' $name 2>$null
        if ($running -eq 'false') {
            docker logs --tail 60 $name
            throw "Kontener $name przestal dzialac."
        }
        Start-Sleep -Seconds 3
        $waited += 3
    }
    docker logs --tail 80 $name
    throw "Backend nie zglosil gotowosci w ${ReadyTimeout}s."
}

function Invoke-Smoke {
    Assert-Docker
    Write-Step 'Test dymny'
    # Odpytujemy z wnetrza sieci kontenerow - sprawdza to takze te sciezke,
    # ktora realnie uzywa frontend.
    docker run --rm --network loyaltyclub-net curlimages/curl:8.11.1 `
        -fsS -o /dev/null -w 'health: HTTP %{http_code}\n' `
        http://loyalty-backend:8089/actuator/health
    Assert-LastExitCode 'Test dymny'
}

switch ($Command) {
    'build' {
        Assert-Docker
        Write-Step "Budowanie obrazu $Image"
        docker build --target runtime -t $Image .
        Assert-LastExitCode 'docker build'
    }
    'test' {
        Assert-Docker
        Write-Step 'Testy jednostkowe w kontenerze'
        # --output wyciaga raporty z etapu test-reports do katalogu na hoscie,
        # zeby CI (junit, pokrycie) mial je poza obrazem.
        if (Test-Path 'target/docker-reports') { Remove-Item -Recurse -Force 'target/docker-reports' }
        docker build --target test-reports --output 'type=local,dest=target/docker-reports' .
        Assert-LastExitCode 'Testy'
        Write-Host 'Raporty: target/docker-reports'
    }
    'sonar' {
        Assert-Docker
        if (-not $env:SONAR_TOKEN) { throw 'Brak zmiennej SONAR_TOKEN.' }
        Write-Step 'Analiza SonarQube'
        # --no-cache-filter wymusza ponowna analize takze wtedy, gdy warstwa
        # z wynikiem poprzedniego przebiegu siedzi jeszcze w cache.
        # -o type=cacheonly: interesuje nas wyslanie raportu, nie obraz.
        $sonarArgs = @(
            'build', '--target', 'sonar', '--no-cache-filter', 'sonar',
            '--secret', 'id=sonar_token,env=SONAR_TOKEN',
            '-o', 'type=cacheonly'
        )
        if ($env:SONAR_HOST_URL) { $sonarArgs += @('--build-arg', "SONAR_HOST_URL=$env:SONAR_HOST_URL") }
        $sonarArgs += '.'
        docker @sonarArgs
        Assert-LastExitCode 'Analiza SonarQube'
    }
    'up' {
        Assert-Docker
        Write-Step 'Budowanie i uruchamianie warstwy serwerowej'
        docker compose up -d --build
        Assert-LastExitCode 'docker compose up'
        Wait-Health
        Invoke-Smoke
        Write-Step 'Gotowe'
        Write-Host "API:     http://localhost:$BackendPort"
        Write-Host "Swagger: http://localhost:$BackendPort/swagger-ui.html"
    }
    'down' {
        Assert-Docker
        Write-Step 'Zatrzymywanie (wolumen bazy zostaje)'
        docker compose down
    }
    'destroy' {
        Assert-Docker
        Write-Step 'Zatrzymywanie razem z danymi bazy'
        docker compose down -v
    }
    'logs'  { Assert-Docker; docker compose logs -f --tail 100 }
    'ps'    { Assert-Docker; docker compose ps }
    'smoke' { Invoke-Smoke }
}
