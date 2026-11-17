param(
    [ValidateSet('Start','Stop','Status')][string]$Action = 'Start',
    [string]$Distribution = 'Ubuntu-24.04',
    [string]$EngineDirectory = '/var/tmp/equiflow-docker-20261116',
    [switch]$Build
)
. (Join-Path $PSScriptRoot '..\scripts\Common.ps1')
$env:WSL_UTF8 = '1'

$clientArguments = @(
    '-d', $Distribution, '-u', 'root', '--exec', "$EngineDirectory/bin/docker"
    '--config', "$EngineDirectory/cli", '--host', "unix://$EngineDirectory/docker.sock"
)
$composeArguments = @(
    '-d', $Distribution, '-u', 'root', '--cd', $PSScriptRoot, '--exec'
    "$EngineDirectory/bin/docker", '--config', "$EngineDirectory/cli"
    '--host', "unix://$EngineDirectory/docker.sock"
    'compose', '--env-file', '../.local/docker-mysql.env'
)
$clientFile = Join-Path $script:EquiFlowRoot '.local\docker-mysql-wsl-client.json'
$client = $null
if (Test-Path -LiteralPath $clientFile -PathType Leaf) {
    $record = Get-Content -LiteralPath $clientFile -Raw | ConvertFrom-Json
    $process = Get-Process -Id $record.Id -ErrorAction SilentlyContinue
    if ($process -and $process.ProcessName -eq 'wsl' -and
        [string]$process.StartTime.ToUniversalTime().Ticks -eq $record.StartedAtUtcTicks) {
        $client = $process
    }
}

switch ($Action) {
    'Start' {
        & wsl.exe @clientArguments info --format '{{.ServerVersion}}'
        if ($LASTEXITCODE -ne 0) { throw 'The selected WSL Docker engine is unavailable.' }
        & (Join-Path $PSScriptRoot 'Prepare-DockerMySql.ps1') -Distribution $Distribution
        & wsl.exe @clientArguments image inspect --format '{{.Id}}' equiflow-source-app:2.0.0 2>$null
        if ($Build -or $LASTEXITCODE -ne 0) {
            & wsl.exe @composeArguments build registry
            if ($LASTEXITCODE -ne 0) { throw 'EquiFlow image build failed.' }
        }
        & wsl.exe @composeArguments up -d --no-build --wait --wait-timeout 360
        if ($LASTEXITCODE -ne 0) { throw 'EquiFlow did not become ready.' }
        if (!$client) {
            $arguments = $clientArguments + @('events', '--filter', 'label=com.docker.compose.project=equiflow-source-mysql')
            $quoted = $arguments | ForEach-Object { if ($_ -match '\s') { '"' + $_ + '"' } else { $_ } }
            $events = Join-Path $script:EquiFlowRoot '.local\docker-mysql-wsl-events.log'
            $eventErrors = Join-Path $script:EquiFlowRoot '.local\docker-mysql-wsl-events.err.log'
            $client = Start-Process -FilePath (Get-Command wsl.exe).Source -ArgumentList $quoted `
                -WindowStyle Hidden -RedirectStandardOutput $events -RedirectStandardError $eventErrors -PassThru
            if ($client.WaitForExit(1000)) {
                throw "The WSL Docker event listener stopped. Check $eventErrors."
            }
            @{ Id = $client.Id; StartedAtUtcTicks = [string]$client.StartTime.ToUniversalTime().Ticks } |
                ConvertTo-Json -Compress | Set-Content -LiteralPath $clientFile -Encoding ASCII
        }
    }
    'Stop' {
        & wsl.exe @composeArguments stop
        if ($LASTEXITCODE -ne 0) { throw 'Could not stop the EquiFlow containers.' }
        if ($client) { Stop-Process -InputObject $client -ErrorAction SilentlyContinue }
    }
    'Status' {
        & wsl.exe @composeArguments ps --all
        if ($LASTEXITCODE -ne 0) { throw 'Could not read the EquiFlow container status.' }
    }
}
