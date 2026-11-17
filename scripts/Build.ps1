param([switch]$SkipTests, [switch]$Offline, [switch]$LendingOnly)
. (Join-Path $PSScriptRoot 'Common.ps1')
$buildState = Join-Path $script:EquiFlowRoot '.local'

function Publish-ServiceJars {
    $runtime = Join-Path $script:EquiFlowRoot 'runtime'
    $published = Join-Path $runtime 'services'
    $cluster = Join-Path $buildState 'cluster'
    foreach ($directory in @($runtime, $cluster)) {
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
    }
    foreach ($directory in @($buildState, $runtime, $cluster, $published)) {
        if ((Test-Path -LiteralPath $directory) -and (Get-Item -LiteralPath $directory).LinkType) {
            throw "Unexpected link at $directory."
        }
    }
    $staging = Join-Path $buildState ('service-build-' + [guid]::NewGuid().ToString('N'))
    $next = Join-Path $staging 'next'
    $previous = Join-Path $staging 'previous'
    New-Item -ItemType Directory -Path $next | Out-Null
    $keepPrevious = $false
    try {
        foreach ($service in @('identity', 'lending', 'maintenance', 'notification', 'registry', 'config', 'gateway')) {
            $jar = Join-Path $script:EquiFlowBackend "services\$service\target\equiflow-$service-2.0.0.jar"
            Copy-Item -LiteralPath $jar -Destination $next
        }
        $operationLock = [IO.File]::Open((Join-Path $cluster 'operation.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
        try {
            $processFile = Join-Path $cluster 'processes.json'
            if (Test-Path -LiteralPath $processFile) {
                $entries = @((Get-Content -LiteralPath $processFile -Raw | ConvertFrom-Json))
                foreach ($entry in $entries) {
                    $processId = 0
                    if (-not $entry.StartedAtUtcTicks -or -not [int]::TryParse($entry.Id, [ref]$processId)) { continue }
                    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
                    if ($process -and $process.ProcessName -eq 'java' -and $process.StartTime -and
                        [string]$process.StartTime.ToUniversalTime().Ticks -eq $entry.StartedAtUtcTicks) {
                        throw 'Stop EquiFlow before replacing its executable JARs.'
                    }
                }
            }
            if ([IO.Directory]::Exists($published)) {
                [IO.Directory]::Move($published, $previous)
                $keepPrevious = $true
            }
            try {
                [IO.Directory]::Move($next, $published)
                $keepPrevious = $false
            } catch {
                if ($keepPrevious) {
                    [IO.Directory]::Move($previous, $published)
                    $keepPrevious = $false
                }
                throw
            }
        } finally { $operationLock.Dispose() }
    } finally {
        if (-not $keepPrevious) {
            $resolved = (Resolve-Path -LiteralPath $staging).Path
            if ($resolved -ne [IO.Path]::GetFullPath($staging) -or (Get-Item -LiteralPath $staging).LinkType) {
                throw 'Unexpected service build staging path.'
            }
            Remove-Item -LiteralPath $resolved -Recurse -Force
        }
    }
}

New-Item -ItemType Directory -Path $buildState -Force | Out-Null
if ((Get-Item -LiteralPath $buildState).LinkType) { throw "Unexpected link at $buildState." }
$buildLock = [IO.File]::Open((Join-Path $buildState 'build.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
Push-Location -LiteralPath $script:EquiFlowRoot
try {
    Push-Location -LiteralPath (Join-Path $script:EquiFlowRoot 'frontend')
    try {
        if (-not (Test-Path -LiteralPath 'node_modules')) {
            $npmArguments = @('ci', '--ignore-scripts', '--registry=https://registry.npmjs.org/', '--no-audit', '--no-fund')
            if ($Offline) { $npmArguments += '--offline' }
            & npm.cmd @npmArguments
            if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
        }
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
    } finally { Pop-Location }
    $static = Join-Path $script:EquiFlowBackend 'src\main\resources\static'
    New-Item -ItemType Directory -Path $static -Force | Out-Null
    $assets = Join-Path $static 'assets'
    if (Test-Path -LiteralPath $assets) {
        $resolved = (Resolve-Path -LiteralPath $assets).Path
        if ($resolved -ne [IO.Path]::GetFullPath($assets) -or (Get-Item -LiteralPath $assets).LinkType) { throw 'Unexpected generated assets path.' }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
    Get-ChildItem -LiteralPath (Join-Path $script:EquiFlowRoot 'frontend\dist') -Force | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination $static -Recurse -Force
    }
    & node (Join-Path $PSScriptRoot 'Normalize-Assets.mjs')
    if ($LASTEXITCODE -ne 0) { throw 'Asset normalization failed.' }
    & node (Join-Path $PSScriptRoot 'Verify-Source.mjs')
    if ($LASTEXITCODE -ne 0) { throw 'Source verification failed.' }
    $goals = @('clean', 'package')
    if ($LendingOnly) { $goals += @('-pl', 'services/lending', '-am') }
    if ($SkipTests) { $goals += '-DskipTests' }
    Invoke-ProjectMaven -Goals $goals -Offline:$Offline
    if ($LendingOnly) {
        Write-Host 'Lending build complete: backend\services\lending\target\equiflow-lending-2.0.0.jar'
        return
    }
    Publish-ServiceJars
    Write-Host 'Build complete: seven executable microservice JARs under runtime\services.'
} finally {
    Pop-Location
    $buildLock.Dispose()
}
