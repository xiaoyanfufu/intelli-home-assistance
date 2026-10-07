$ErrorActionPreference='Stop'
$workspace=Split-Path $PSScriptRoot -Parent
$containerName='intelli-home-it-rabbit'
$existing=docker ps -a --filter "name=^/$containerName$" --format '{{.ID}}'
if($existing) { throw 'Test container already exists; inspect it before running verification.' }
docker run -d --name $containerName --user 100:101 --tmpfs /var/lib/rabbitmq:uid=100,gid=101,mode=0700 -p 127.0.0.1:5673:5672 -e RABBITMQ_DEFAULT_USER=intelli -e RABBITMQ_DEFAULT_PASS=intelli123 rabbitmq:4-management-alpine
if($LASTEXITCODE -ne 0) { throw 'Unable to start test Broker' }
try {
    $ready=$false
    for($attempt=0;$attempt -lt 60;$attempt++) {
        if((docker inspect --format '{{.State.Running}}' $containerName) -ne 'true') {
            docker logs --tail 15 $containerName
            throw 'Test Broker exited during startup'
        }
        docker exec $containerName rabbitmq-diagnostics -q ping 2>$null
        if($LASTEXITCODE -eq 0) { $ready=$true; break }
        Start-Sleep -Seconds 2
    }
    if(!$ready) { throw 'Test Broker did not become ready' }
    Push-Location (Join-Path $workspace 'intelli-home')
    try {
        mvn clean test '-Dhome.integration=true' '-Dspring.rabbitmq.port=5673' '-Dhome.brokerFaults=true'
        if($LASTEXITCODE -ne 0) { throw 'Java verification failed' }
    } finally { Pop-Location }
    Push-Location (Join-Path $workspace 'intelli-home-agent')
    try {
        & ./.venv/Scripts/python.exe -m pytest -q
        if($LASTEXITCODE -ne 0) { throw 'Python verification failed' }
    } finally { Pop-Location }
} finally {
    $paused=docker inspect --format '{{.State.Paused}}' $containerName
    if($paused -eq 'true') { docker unpause $containerName | Out-Null }
    docker rm -f $containerName | Out-Null
}
