param(
    [string]$Distribution = 'Ubuntu-24.04',
    [ValidateRange(1,65535)][int]$MySqlPort = 3316
)
. (Join-Path $PSScriptRoot '..\scripts\Common.ps1')
$env:WSL_UTF8 = '1'

$routes = & wsl.exe -d $Distribution --exec ip -4 route show default
if ($LASTEXITCODE -ne 0 -or ($routes -join ' ') -notmatch '\bdefault via (\d+(?:\.\d+){3})\b') {
    throw 'Could not determine the Windows gateway of the WSL Docker engine.'
}
$windowsHost = $Matches[1]
$route = & wsl.exe -d $Distribution --exec ip -4 route get $windowsHost
if ($LASTEXITCODE -ne 0 -or ($route -join ' ') -notmatch '\bsrc (\d+(?:\.\d+){3})\b') {
    throw 'Could not determine the WSL source address for MySQL.'
}
$wslAddress = $Matches[1]

& (Join-Path $script:EquiFlowRoot 'scripts\Start-MySql.ps1') -Port $MySqlPort -DockerBindAddress $windowsHost
& (Join-Path $script:EquiFlowRoot 'scripts\Initialize-ServiceDatabases.ps1') -RestoreDump

$db = Read-LocalMySql
$environmentFile = Join-Path $script:EquiFlowRoot '.local\docker-mysql.env'
$previous = @{}
if (Test-Path -LiteralPath $environmentFile -PathType Leaf) {
    foreach ($line in Get-Content -LiteralPath $environmentFile) {
        $pair = $line -split '=', 2
        if ($pair.Length -eq 2) { $previous[$pair[0]] = $pair[1] }
    }
}
$serviceKey = $previous['EQUIFLOW_SERVICE_KEY']
if ($serviceKey -notmatch '^[a-f0-9]{64}$') {
    $serviceKey = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
}
$password = $previous['EQUIFLOW_DATABASE_PASSWORD']
if ($password -notmatch '^[a-f0-9]{32}$') { $password = [guid]::NewGuid().ToString('N') }

$account = "'equiflow_docker'@'$wslAddress'"
$sql = "CREATE USER IF NOT EXISTS $account IDENTIFIED BY '$password' REQUIRE SSL; ALTER USER $account IDENTIFIED BY '$password' REQUIRE SSL;"
$schemaNames = 'equiflow_identity', 'equiflow_lending', 'equiflow_maintenance', 'equiflow_notification'
foreach ($schema in $schemaNames) {
    $sql += " GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON $schema.* TO $account;"
}
$mysql = Join-Path $db.MySqlHome 'bin\mysql.exe'
$client = Join-Path $db.RuntimeDirectory 'root.cnf'
$sql | & $mysql "--defaults-file=$client" --host=127.0.0.1 "--port=$MySqlPort" --protocol=tcp
if ($LASTEXITCODE -ne 0) { throw 'Could not provision the Docker MySQL account.' }

$settings = @(
    "EQUIFLOW_MYSQL_HOST=$windowsHost"
    "EQUIFLOW_MYSQL_PORT=$MySqlPort"
    'EQUIFLOW_DATABASE_USER=equiflow_docker'
    "EQUIFLOW_DATABASE_PASSWORD=$password"
    "EQUIFLOW_SERVICE_KEY=$serviceKey"
)
[IO.File]::WriteAllLines($environmentFile, $settings, [Text.Encoding]::ASCII)
Write-Host "MySQL is ready for the WSL Docker engine at $($windowsHost):$MySqlPort."
