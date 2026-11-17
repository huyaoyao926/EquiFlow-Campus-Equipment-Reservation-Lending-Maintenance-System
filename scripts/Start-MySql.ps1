param(
    [ValidateRange(1,65535)][int]$Port = 3316,
    [string]$MySqlHome = '',
    [string]$RuntimeDirectory = '',
    [Net.IPAddress]$DockerBindAddress
)
. (Join-Path $PSScriptRoot 'Common.ps1')

if (-not $MySqlHome) { $MySqlHome = $env:MYSQL_HOME }
if (-not $MySqlHome) {
    $installedServer = Get-Command mysqld.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($installedServer) { $MySqlHome = Split-Path -Parent (Split-Path -Parent $installedServer.Source) }
}
if (-not $MySqlHome) {
    foreach ($version in @('8.4','8.0')) {
        $installation = Join-Path $env:ProgramFiles "MySQL\MySQL Server $version"
        if (Test-Path -LiteralPath (Join-Path $installation 'bin\mysqld.exe') -PathType Leaf) {
            $MySqlHome = $installation
            break
        }
    }
}
if (-not $MySqlHome) { throw 'Install MySQL Server 8.0 or 8.4, or supply -MySqlHome.' }
$MySqlHome = [IO.Path]::GetFullPath($MySqlHome)
$server = Join-Path $MySqlHome 'bin\mysqld.exe'
$mysql = Join-Path $MySqlHome 'bin\mysql.exe'
$admin = Join-Path $MySqlHome 'bin\mysqladmin.exe'
foreach ($executable in @($server,$mysql,$admin)) {
    if (-not (Test-Path -LiteralPath $executable -PathType Leaf)) { throw "Missing MySQL program: $executable" }
}
$versionOutput = & $server --no-defaults --version
if ($LASTEXITCODE -ne 0 -or $versionOutput -notmatch '\b(?:8\.0|8\.4)\.\d+\b') {
    throw 'The managed database requires MySQL Server 8.0 or 8.4.'
}

$projectDirectory = Join-Path $script:EquiFlowRoot '.local\mysql'
$hasher = [Security.Cryptography.SHA256]::Create()
try {
    $projectKey = [BitConverter]::ToString($hasher.ComputeHash([Text.Encoding]::UTF8.GetBytes($script:EquiFlowRoot))).Replace('-','').Substring(0,12).ToLowerInvariant()
} finally { $hasher.Dispose() }
$directory = if ($RuntimeDirectory) { [IO.Path]::GetFullPath($RuntimeDirectory) } else { Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) "EquiFlow\mysql-$projectKey-$Port" }
if ($directory -match '[^\x00-\x7F]') { throw 'Supply -RuntimeDirectory with an ASCII-only path.' }
$data = Join-Path $directory 'data'
$configuration = Join-Path $directory 'my.ini'
$credentialsPath = Join-Path $projectDirectory 'connection.json'
$clientPath = Join-Path $directory 'client.cnf'
$rootClientPath = Join-Path $directory 'root.cnf'
$bootstrap = Join-Path $directory 'bootstrap.sql'
$setupPending = Join-Path $directory 'setup.pending'
$pidPath = Join-Path $directory 'mysql.pid'
$utf8 = [Text.UTF8Encoding]::new($false)
$bindAddresses = '127.0.0.1'
if ($DockerBindAddress) {
    $localAddresses = [Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces() |
        ForEach-Object { $_.GetIPProperties().UnicastAddresses } |
        ForEach-Object { $_.Address }
    if ($DockerBindAddress.AddressFamily -ne [Net.Sockets.AddressFamily]::InterNetwork -or
        [Net.IPAddress]::IsLoopback($DockerBindAddress) -or $DockerBindAddress -notin $localAddresses) {
        throw 'The Docker bind address must be an IPv4 address assigned to this Windows host.'
    }
    $bindAddresses += ",$DockerBindAddress"
}

function Test-ManagedServer {
    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $state = & $mysql "--defaults-file=$rootClientPath" --host=127.0.0.1 "--port=$Port" --protocol=tcp --user=root --connect-timeout=2 --batch --raw --skip-column-names --execute='SELECT @@datadir, @@port' 2>$null
        if ($LASTEXITCODE -ne 0) { return $false }
        $fields = ([string]$state).Split([char]9)
        if ($fields.Count -ne 2 -or $fields[1] -ne [string]$Port) { return $false }
        if ([IO.Path]::GetFullPath($fields[0]).TrimEnd('\') -ne [IO.Path]::GetFullPath($data).TrimEnd('\')) { return $false }
        $null = & $mysql "--defaults-file=$clientPath" --host=127.0.0.1 "--port=$Port" --protocol=tcp --user=equiflow --connect-timeout=2 --execute='SELECT 1' 2>$null
        return $LASTEXITCODE -eq 0
    } finally { $ErrorActionPreference = $previousErrorAction }
}

function Read-ManagedPassword {
    param([string]$Path,[string]$User)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf) -or (Get-Item -LiteralPath $Path).LinkType) {
        throw "Missing managed MySQL client configuration: $Path"
    }
    $lines = Get-Content -LiteralPath $Path
    if ("user=$User" -notin $lines -or "port=$Port" -notin $lines) {
        throw "The MySQL client configuration does not match this user and port: $Path"
    }
    $passwordLine = @($lines | Where-Object { $_ -match '^password=[0-9a-f]{32}$' })
    if ($passwordLine.Count -ne 1) { throw "Invalid managed MySQL credentials in $Path." }
    return $passwordLine[0].Substring('password='.Length)
}

New-Item -ItemType Directory -Path $directory -Force | Out-Null
foreach ($path in @($directory,$data)) {
    if ((Test-Path -LiteralPath $path) -and (Get-Item -LiteralPath $path).LinkType) { throw "Unexpected MySQL directory link: $path" }
}
$lockPath = Join-Path $directory 'startup.lock'
$lock = [IO.File]::Open($lockPath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::Write,[IO.FileShare]::None)
try {
    $existingData = Test-Path -LiteralPath (Join-Path $data 'mysql') -PathType Container
    $probe = [Net.Sockets.TcpClient]::new()
    try { $occupied = $probe.ConnectAsync('127.0.0.1',$Port).Wait(500) -and $probe.Connected } catch { $occupied = $false } finally { $probe.Dispose() }

    if ($existingData) {
        if (-not (Test-Path -LiteralPath $configuration -PathType Leaf) -or (Get-Item -LiteralPath $configuration).LinkType) {
            throw "Existing MySQL data has no managed server configuration: $directory"
        }
        $format = [IO.Path]::GetFullPath($data).Replace('\','/')
        $configLines = Get-Content -LiteralPath $configuration
        if (('datadir="{0}"' -f $format) -notin $configLines -or "port=$Port" -notin $configLines -or
            ('basedir="{0}"' -f $MySqlHome.Replace('\','/')) -notin $configLines) {
            throw "The managed MySQL server configuration does not match this installation, runtime path and port: $configuration"
        }
        $password = Read-ManagedPassword -Path $clientPath -User 'equiflow'
        $rootPassword = Read-ManagedPassword -Path $rootClientPath -User 'root'
        if ($occupied -and -not (Test-ManagedServer)) { throw "Port $Port belongs to a different MySQL instance or service." }
        $bindLine = "bind-address=$bindAddresses"
        if ($bindLine -notin $configLines -and (!$occupied -or $DockerBindAddress)) {
            if ($occupied) { throw 'Stop the managed project MySQL server before changing its bind address.' }
            [IO.File]::WriteAllLines($configuration, ($configLines -replace '^bind-address=.*$', $bindLine), $utf8)
        }
    } else {
        if ($occupied) { throw "Port $Port is occupied by another service." }
        if ((Test-Path -LiteralPath $data) -and (Get-ChildItem -LiteralPath $data -Force | Select-Object -First 1)) {
            throw "MySQL data directory is not empty: $data"
        }
        $password = [guid]::NewGuid().ToString('N')
        $rootPassword = [guid]::NewGuid().ToString('N')
        $baseUnix = $MySqlHome.Replace('\','/')
        $dataUnix = $data.Replace('\','/')
        $pidUnix = $pidPath.Replace('\','/')
        $logUnix = (Join-Path $directory 'mysql-error.log').Replace('\','/')
        $configText = @"
[mysqld]
basedir="$baseUnix"
datadir="$dataUnix"
port=$Port
bind-address=$bindAddresses
mysqlx=0
skip-log-bin
pid-file="$pidUnix"
log-error="$logUnix"
character-set-server=utf8mb4
collation-server=utf8mb4_0900_ai_ci
"@
        [IO.File]::WriteAllText($configuration,$configText,$utf8)
        $sql = @"
ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPassword';
CREATE DATABASE IF NOT EXISTS equiflow CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS equiflow_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'equiflow'@'localhost' IDENTIFIED BY '$password';
GRANT ALL PRIVILEGES ON equiflow.* TO 'equiflow'@'localhost';
GRANT ALL PRIVILEGES ON equiflow_test.* TO 'equiflow'@'localhost';
"@
        [IO.File]::WriteAllText($bootstrap,$sql,$utf8)
        foreach ($pair in @(@($clientPath,'equiflow',$password),@($rootClientPath,'root',$rootPassword))) {
            $content = "[client]`nuser=$($pair[1])`npassword=$($pair[2])`nhost=127.0.0.1`nport=$Port`nprotocol=tcp`ndefault-character-set=utf8mb4`n"
            [IO.File]::WriteAllText($pair[0],$content,$utf8)
        }
        New-Item -ItemType Directory -Path $data -Force | Out-Null
        [IO.File]::WriteAllText($setupPending,'pending',$utf8)
        $previousErrorAction = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            & $server "--defaults-file=$configuration" --initialize-insecure --console *> (Join-Path $directory 'initialize.log')
        } finally { $ErrorActionPreference = $previousErrorAction }
        if ($LASTEXITCODE -ne 0) { throw "MySQL initialization failed. Check $directory\initialize.log." }
    }

    if (-not $occupied) {
        $arguments = @(( '--defaults-file="{0}"' -f $configuration))
        if (Test-Path -LiteralPath $setupPending -PathType Leaf) {
            if (-not (Test-Path -LiteralPath $bootstrap -PathType Leaf) -or (Get-Item -LiteralPath $bootstrap).LinkType) {
                throw "Missing managed MySQL bootstrap: $bootstrap"
            }
            $arguments += ( '--init-file="{0}"' -f $bootstrap)
        }
        $process = Start-Process -FilePath $server -ArgumentList $arguments -WindowStyle Hidden -PassThru
        try {
            $ready = $false
            for ($attempt = 0; $attempt -lt 60; $attempt++) {
                Start-Sleep -Milliseconds 500
                if ($process.HasExited) { throw "MySQL stopped during startup. Check $directory\mysql-error.log." }
                if (Test-ManagedServer) { $ready = $true; break }
            }
            if (-not $ready) { throw "MySQL did not accept authenticated connections. Check $directory\mysql-error.log." }
        } catch {
            if (-not $process.HasExited) { Stop-Process -InputObject $process -Force -ErrorAction SilentlyContinue }
            throw
        }
    }
    if (Test-Path -LiteralPath $setupPending -PathType Leaf) { Remove-Item -LiteralPath $setupPending }

    $connection = [pscustomobject]@{ User = 'equiflow'; Password = $password; RootPassword = $rootPassword; Port = $Port; MySqlHome = $MySqlHome; RuntimeDirectory = $directory }
    New-Item -ItemType Directory -Path $projectDirectory -Force | Out-Null
    [IO.File]::WriteAllText($credentialsPath,($connection | ConvertTo-Json),$utf8)
    Write-Host "Project MySQL is ready on 127.0.0.1:$Port."
} finally { $lock.Dispose() }
