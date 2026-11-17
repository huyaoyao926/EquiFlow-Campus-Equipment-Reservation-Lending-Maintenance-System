param([switch]$RestoreDump)
. (Join-Path $PSScriptRoot 'Common.ps1')

$db = Read-LocalMySql
$mysql = Join-Path $db.MySqlHome 'bin\mysql.exe'
$client = Join-Path $db.RuntimeDirectory 'root.cnf'
$datadir = Join-Path $db.RuntimeDirectory 'data'
$pending = Join-Path $db.RuntimeDirectory 'dump-import.pending'
$complete = Join-Path $db.RuntimeDirectory 'dump-import.done'
$lockPath = Join-Path $db.RuntimeDirectory 'startup.lock'
$connectionArgs = @("--defaults-file=$client",'--host=127.0.0.1',"--port=$($db.Port)",'--protocol=tcp','--user=root','--connect-timeout=3')
$schemaNames = @('equiflow_identity','equiflow_lending','equiflow_maintenance','equiflow_notification')

$lock = [IO.File]::Open($lockPath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::Write,[IO.FileShare]::None)
try {
    $response = & $mysql @connectionArgs --batch --raw --skip-column-names --execute='SELECT @@datadir, @@port'
    $fields = ([string]$response).Split([char]9)
    if ($LASTEXITCODE -ne 0 -or $fields.Count -ne 2 -or $fields[1] -ne [string]$db.Port -or
        [IO.Path]::GetFullPath($fields[0]).TrimEnd('\') -ne [IO.Path]::GetFullPath($datadir).TrimEnd('\')) {
        throw 'The connected MySQL server is not the managed project instance.'
    }
    if ((Test-Path -LiteralPath $pending) -and -not (Test-Path -LiteralPath $complete)) {
        throw 'A previous dump import did not finish. Use a new empty -MySqlRuntimeDirectory for another import.'
    }
    if ($RestoreDump -and -not (Test-Path -LiteralPath $complete)) {
        $dump = Join-Path $script:EquiFlowBackend 'database\equiflow-microservices.sql'
        if (-not (Test-Path -LiteralPath $dump -PathType Leaf)) { throw "Database dump is missing: $dump" }
        $quotedSchemas = ($schemaNames | ForEach-Object { "'$_'" }) -join ','
        $count = & $mysql @connectionArgs --batch --skip-column-names "--execute=SELECT COUNT(*) FROM information_schema.tables WHERE table_schema IN ($quotedSchemas)"
        if ($LASTEXITCODE -ne 0) { throw 'Could not verify that the service databases are empty.' }
        if ($count -ne '0') { throw 'The service databases already contain tables. Supply a new empty -MySqlRuntimeDirectory to restore the dump.' }

        [IO.File]::WriteAllText($pending,'pending')
        $importArgs = @(( '--defaults-file="{0}"' -f $client),'--host=127.0.0.1',"--port=$($db.Port)",'--protocol=tcp','--user=root','--connect-timeout=3','--default-character-set=utf8mb4')
        $import = Start-Process -FilePath $mysql -ArgumentList $importArgs -RedirectStandardInput $dump -NoNewWindow -Wait -PassThru
        if ($import.ExitCode -ne 0) { throw "Database import failed with exit code $($import.ExitCode)." }
        [IO.File]::WriteAllText($complete,'restored')
        Remove-Item -LiteralPath $pending
        Write-Host 'The bundled dump was restored into four service databases.'
    }

    $sql = ($schemaNames | ForEach-Object {
        "CREATE DATABASE IF NOT EXISTS $_ CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL PRIVILEGES ON $_.* TO 'equiflow'@'localhost'; "
    }) -join ''
    & $mysql @connectionArgs "--execute=$sql"
    if ($LASTEXITCODE -ne 0) { throw 'Could not initialize the four service databases.' }
} finally { $lock.Dispose() }
