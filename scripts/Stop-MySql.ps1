. (Join-Path $PSScriptRoot 'Common.ps1')

$connection = Read-LocalMySql
$mysql = Join-Path $connection.MySqlHome 'bin\mysql.exe'
$admin = Join-Path $connection.MySqlHome 'bin\mysqladmin.exe'
$configuration = Join-Path $connection.RuntimeDirectory 'root.cnf'
$datadir = Join-Path $connection.RuntimeDirectory 'data'
$lockPath = Join-Path $connection.RuntimeDirectory 'startup.lock'
$argsForServer = @("--defaults-file=$configuration",'--host=127.0.0.1',"--port=$($connection.Port)",'--protocol=tcp','--user=root','--connect-timeout=3')
$lock = [IO.File]::Open($lockPath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::Write,[IO.FileShare]::None)
try {
    $response = & $mysql @argsForServer --batch --raw --skip-column-names --execute='SELECT @@datadir, @@port'
    $fields = ([string]$response).Split([char]9)
    if ($LASTEXITCODE -ne 0 -or $fields.Count -ne 2 -or $fields[1] -ne [string]$connection.Port -or
        [IO.Path]::GetFullPath($fields[0]).TrimEnd('\') -ne [IO.Path]::GetFullPath($datadir).TrimEnd('\')) {
        throw 'The connected MySQL server is not the managed project instance.'
    }
    & $admin @argsForServer shutdown
    if ($LASTEXITCODE -ne 0) { throw 'Could not stop the project MySQL server.' }
    Write-Host 'Project MySQL stopped.'
} finally { $lock.Dispose() }
