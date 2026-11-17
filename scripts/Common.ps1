$ErrorActionPreference = 'Stop'
$script:EquiFlowRoot = Split-Path -Parent $PSScriptRoot
$script:EquiFlowBackend = Join-Path $script:EquiFlowRoot 'backend'

function Invoke-ProjectMaven {
    param([string[]]$Goals, [switch]$Offline)
    $settings = Join-Path $script:EquiFlowBackend '.mvn\settings.xml'
    $cache = Join-Path $script:EquiFlowRoot '.local\m2'
    $mavenArguments = @('-B', '-s', $settings, '-gs', $settings, "-Dmaven.repo.local=$cache")
    if ($Offline) { $mavenArguments += '-o' }
    $mavenArguments += $Goals
    Push-Location -LiteralPath $script:EquiFlowBackend
    try {
        & mvn.cmd @mavenArguments
        if ($LASTEXITCODE -ne 0) { throw "Maven failed with exit code $LASTEXITCODE." }
    } finally { Pop-Location }
}

function Read-LocalMySql {
    $path = Join-Path $script:EquiFlowRoot '.local\mysql\connection.json'
    if (-not (Test-Path -LiteralPath $path)) { throw 'Local MySQL is not configured. Run scripts\Start-MySql.ps1 first.' }
    return Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
}
