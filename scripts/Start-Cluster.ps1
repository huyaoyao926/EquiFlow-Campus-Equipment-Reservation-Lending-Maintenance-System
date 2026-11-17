param(
    [ValidateSet('demo','mysql')][string]$Profile = 'demo',
    [string]$ListenAddress = '127.0.0.1',
    [string]$MySqlHome,
    [string]$MySqlRuntimeDirectory,
    [ValidateRange(1,65535)][int]$MySqlPort = 3316,
    [switch]$RestoreDump,
    [switch]$OpenBrowser
)
. (Join-Path $PSScriptRoot 'Common.ps1')
if ($RestoreDump -and $Profile -ne 'mysql') { throw '-RestoreDump requires -Profile mysql.' }
$bindAddress = [Net.IPAddress]::Parse($ListenAddress)
$java = Get-Command java.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $java) { throw 'Install Java 21 and add its bin directory to PATH.' }
$release = Join-Path $script:EquiFlowRoot 'runtime'
$ports = @{
    gateway = 8086
    lending = 8091
    identity = 8092
    notification = 8093
    maintenance = 8094
    registry = 28761
    config = 28888
    kafka = 19092
    controller = 19093
    zipkin = 19411
}
$serviceJars = @{}
foreach ($name in @('identity','lending','maintenance','notification','registry','config','gateway')) {
    $serviceJars[$name] = Join-Path $release "services\equiflow-$name-2.0.0.jar"
}
$runtime = Join-Path $script:EquiFlowRoot '.local\cluster'
$logs = Join-Path $runtime 'logs'
New-Item -ItemType Directory -Path $logs -Force | Out-Null
$processFile = Join-Path $runtime 'processes.json'
$launchFile = Join-Path $runtime 'launch.json'
$script:Managed = @()
function Get-ManagedProcess {
    param($Entry)
    $processId = 0
    if (-not $Entry.StartedAtUtcTicks -or -not [int]::TryParse($Entry.Id, [ref]$processId)) { return }
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if ($process -and $process.ProcessName -eq 'java' -and $process.StartTime -and
        [string]$process.StartTime.ToUniversalTime().Ticks -eq $Entry.StartedAtUtcTicks) {
        return $process
    }
}
function Assert-PortAvailable {
    param([int]$Port, $Address = [Net.IPAddress]::Loopback)
    $listener = [Net.Sockets.TcpListener]::new($Address, $Port)
    $listener.ExclusiveAddressUse = $true
    try { $listener.Start() }
    catch { throw "Cannot listen on $($Address):$Port. Check that the port and address are available." }
    finally { $listener.Stop() }
}
function Save-Managed {
    $temporary = "$processFile.tmp"
    [IO.File]::WriteAllText($temporary, (ConvertTo-Json -InputObject @($script:Managed)), [Text.Encoding]::ASCII)
    if ([IO.File]::Exists($processFile)) { [IO.File]::Replace($temporary, $processFile, "$processFile.bak") }
    else { [IO.File]::Move($temporary, $processFile) }
}
function Start-Java {
    param([string]$Name,[string[]]$JavaArgs,[int]$Port,$Address = [Net.IPAddress]::Loopback)
    $old = $script:Managed | Where-Object { $_.Name -eq $Name } | Select-Object -First 1
    if ($old -and (Get-ManagedProcess $old)) { Write-Host "$Name already running."; return }
    Assert-PortAvailable -Port $Port -Address $Address
    $heap=if ($Name -in @('kafka','zipkin')) { '-Xmx512m' } else { '-Xmx256m' }
    $arguments = @('-Dfile.encoding=UTF-8','-Duser.language=en','-Xms64m',$heap) + $JavaArgs
    $quoted = ($arguments | ForEach-Object { '"' + $_.Replace('"','\"') + '"' }) -join ' '
    $process = Start-Process -FilePath $java.Source -ArgumentList $quoted -WorkingDirectory $script:EquiFlowRoot -RedirectStandardOutput (Join-Path $logs "$Name.log") -RedirectStandardError (Join-Path $logs "$Name.err.log") -WindowStyle Hidden -PassThru
    $script:Managed = @($script:Managed | Where-Object { $_.Name -ne $Name }) + [pscustomobject]@{
        Name = $Name
        Id = $process.Id
        Port = $Port
        StartedAtUtcTicks = [string]$process.StartTime.ToUniversalTime().Ticks
    }
    Save-Managed
    Write-Host "Starting $Name on port $Port..."
}
function Wait-Http {
    param([string]$Url,[string]$Name)
    for ($attempt=0; $attempt -lt 160; $attempt++) {
        try { $response=Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3; if ($response.StatusCode -eq 200) { return } } catch {}
        $entry = $script:Managed | Where-Object { $_.Name -eq $Name } | Select-Object -First 1
        if ($entry -and -not (Get-ManagedProcess $entry)) { throw "$Name stopped. Check .local\cluster\logs\$Name.log." }
        Start-Sleep -Milliseconds 500
    }
    throw "$Name did not become ready. Check .local\cluster\logs."
}
$operationLock = [IO.File]::Open((Join-Path $runtime 'operation.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
$savedEnvironment = @{}
foreach ($name in @('SERVICE_KEY','DATABASE_URL','DATABASE_USER','DATABASE_PASSWORD')) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
Push-Location -LiteralPath $script:EquiFlowRoot
try {
    foreach ($jar in $serviceJars.Values) {
        if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw 'Run scripts\Build.ps1 before starting the cluster.' }
    }
    if (Test-Path -LiteralPath $processFile) {
        $script:Managed = @((Get-Content -LiteralPath $processFile -Raw | ConvertFrom-Json) | Where-Object { Get-ManagedProcess $_ })
    }
    $settings = [ordered]@{ Profile = $Profile; ListenAddress = $ListenAddress }
    if ($Profile -eq 'mysql') {
        $settings.MySqlHome = $MySqlHome
        $settings.MySqlRuntimeDirectory = $MySqlRuntimeDirectory
        $settings.MySqlPort = $MySqlPort
    }
    $launchSettings = ConvertTo-Json -InputObject $settings -Compress
    if ($script:Managed.Count -gt 0) {
        if (-not (Test-Path -LiteralPath $launchFile) -or
            [IO.File]::ReadAllText($launchFile) -cne $launchSettings -or $RestoreDump) {
            throw 'Stop EquiFlow before changing its startup settings or restoring a dump.'
        }
    } else {
        [IO.File]::WriteAllText($launchFile, $launchSettings)
    }
    foreach ($name in $ports.Keys) {
        if ($name -eq 'controller') { continue }
        if ($name -notin $script:Managed.Name) {
            if ($name -eq 'gateway') { Assert-PortAvailable -Port $ports[$name] -Address $bindAddress }
            else { Assert-PortAvailable -Port $ports[$name] }
        }
    }
    if ('kafka' -notin $script:Managed.Name) { Assert-PortAvailable -Port $ports.controller }
    New-Item -ItemType Directory -Path (Join-Path $script:EquiFlowRoot '.local\services') -Force | Out-Null
    $keyFile = Join-Path $runtime 'service-key.txt'
    if (-not (Test-Path -LiteralPath $keyFile)) { [IO.File]::WriteAllText($keyFile, [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')) }
    $env:SERVICE_KEY = [IO.File]::ReadAllText($keyFile)
    Remove-Item Env:DATABASE_URL,Env:DATABASE_USER,Env:DATABASE_PASSWORD -ErrorAction SilentlyContinue
    if ($Profile -eq 'mysql') {
        & (Join-Path $PSScriptRoot 'Start-MySql.ps1') -Port $MySqlPort -MySqlHome $MySqlHome -RuntimeDirectory $MySqlRuntimeDirectory
        & (Join-Path $PSScriptRoot 'Initialize-ServiceDatabases.ps1') -RestoreDump:$RestoreDump
        $db=Read-LocalMySql
    }
    $libs=Join-Path $release 'kafka'
    if (-not (Test-Path -LiteralPath (Join-Path $libs 'kafka_2.13-4.1.1.jar'))) {
        $libs=Join-Path $script:EquiFlowRoot '.local\kafka-libs'
        Invoke-ProjectMaven -Goals @('-f','tools/kafka/pom.xml','dependency:copy-dependencies',"-DoutputDirectory=$libs",'-DincludeScope=runtime')
    }
    $brokerConfig=Join-Path $runtime 'broker.properties'
    $brokerData='.local/cluster/kafka-data'
    @"
process.roles=broker,controller
node.id=1
controller.quorum.voters=1@127.0.0.1:$($ports.controller)
listeners=PLAINTEXT://127.0.0.1:$($ports.kafka),CONTROLLER://127.0.0.1:$($ports.controller)
advertised.listeners=PLAINTEXT://127.0.0.1:$($ports.kafka)
controller.listener.names=CONTROLLER
listener.security.protocol.map=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
inter.broker.listener.name=PLAINTEXT
log.dirs=$brokerData
num.partitions=3
offsets.topic.replication.factor=1
transaction.state.log.replication.factor=1
transaction.state.log.min.isr=1
group.initial.rebalance.delay.ms=0
log.retention.hours=168
"@ | Set-Content -LiteralPath $brokerConfig -Encoding ASCII
    $classpath = Join-Path $libs '*'
    if (-not (Test-Path -LiteralPath (Join-Path $runtime 'kafka-data\meta.properties'))) {
        $clusterId = & $java.Source -cp $classpath kafka.tools.StorageTool random-uuid
        if ($LASTEXITCODE -ne 0) { throw 'Kafka cluster ID generation failed.' }
        & $java.Source -cp $classpath kafka.tools.StorageTool format -t $clusterId -c $brokerConfig
        if ($LASTEXITCODE -ne 0) { throw 'Kafka storage initialization failed.' }
    }
    Start-Java -Name kafka -JavaArgs @('-cp',$classpath,'kafka.Kafka',$brokerConfig) -Port $ports.kafka
    $kafkaReady = $false
    for ($attempt=0; $attempt -lt 60; $attempt++) {
        $tcp=New-Object Net.Sockets.TcpClient
        try { $tcp.Connect('127.0.0.1',$ports.kafka); $kafkaReady = $true; break }
        catch { Start-Sleep -Milliseconds 500 }
        finally { $tcp.Dispose() }
    }
    if (-not $kafkaReady) { throw 'Kafka did not become ready. Check .local\cluster\logs\kafka.log.' }
    $zipkin=Join-Path $release 'zipkin\zipkin-server-3.5.1-exec.jar'
    if (-not (Test-Path -LiteralPath $zipkin)) {
        $zipkin=Join-Path $script:EquiFlowRoot '.local\zipkin\zipkin-server-3.5.1-exec.jar'
        Invoke-ProjectMaven -Goals @('dependency:copy','-Dartifact=io.zipkin:zipkin-server:3.5.1:jar:exec',"-DoutputDirectory=$(Split-Path -Parent $zipkin)",'-N')
    }
    Start-Java -Name zipkin -JavaArgs @('-jar',$zipkin,
        "--server.port=$($ports.zipkin)",'--armeria.ports[0].address=127.0.0.1',
        "--armeria.ports[0].port=$($ports.zipkin)",'--armeria.ports[0].protocols[0]=http',
        '--zipkin.storage.mem.max-spans=10000') -Port $ports.zipkin
    Wait-Http -Url "http://127.0.0.1:$($ports.zipkin)/health" -Name zipkin
    foreach ($name in 'registry','config') {
        $port = $ports[$name]
        $javaArgs = @('-jar',$serviceJars[$name],"--server.port=$port")
        if ($name -eq 'registry') { $javaArgs += '--eureka.server.use-read-only-response-cache=false' }
        if ($name -eq 'config') {
            $javaArgs += '--spring.cloud.config.server.native.search-locations=file:./backend/cluster-config/'
        }
        Start-Java -Name $name -JavaArgs $javaArgs -Port $port
        Wait-Http -Url "http://127.0.0.1:$port/actuator/health" -Name $name
    }
    $registryUrl = "http://127.0.0.1:$($ports.registry)"
    foreach ($group in @(@('identity','notification','maintenance'), @('lending'))) {
        foreach ($name in $group) {
            $port = $ports[$name]
            $javaArgs = @('-jar',$serviceJars[$name],"--server.port=$port",
                '--eureka.client.initial-instance-info-replication-interval-seconds=1')
            if ($Profile -eq 'mysql') {
                $env:DATABASE_URL="jdbc:mysql://127.0.0.1:$($db.Port)/equiflow_$($name)?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
                $env:DATABASE_USER=$db.User
                $env:DATABASE_PASSWORD=$db.Password
                if ($name -eq 'lending') { $javaArgs += '--spring.profiles.active=mysql' }
            }
            Start-Java -Name $name -JavaArgs $javaArgs -Port $port
        }
        foreach ($name in $group) {
            Wait-Http -Url "http://127.0.0.1:$($ports[$name])/actuator/health" -Name $name
            Wait-Http -Url "$registryUrl/eureka/apps/EQUIFLOW-$($name.ToUpperInvariant())-SERVICE" -Name $name
        }
    }
    Remove-Item Env:DATABASE_URL,Env:DATABASE_USER,Env:DATABASE_PASSWORD -ErrorAction SilentlyContinue
    Start-Java -Name gateway -JavaArgs @('-jar',$serviceJars['gateway'],"--server.address=$ListenAddress",
        "--server.port=$($ports.gateway)") -Port $ports.gateway -Address $bindAddress
    $browserHost = $ListenAddress
    if ($bindAddress.Equals([Net.IPAddress]::Any)) { $browserHost = '127.0.0.1' }
    if ($bindAddress.Equals([Net.IPAddress]::IPv6Any)) { $browserHost = '::1' }
    if ($bindAddress.AddressFamily -eq [Net.Sockets.AddressFamily]::InterNetworkV6) { $browserHost = "[$browserHost]" }
    $url = "http://$($browserHost):$($ports.gateway)"
    Wait-Http -Url "$url/api/config" -Name gateway
    Wait-Http -Url "$url/" -Name gateway
    Write-Host "EquiFlow is ready at $url"
    Write-Host 'Use the one-click Student Alex, Student Jordan or Administrator sign-in buttons.'
    Write-Host "Service registry: $registryUrl"
    Write-Host 'Stop with scripts\Stop-Cluster.ps1.'
    if ($OpenBrowser) { Start-Process $url -WindowStyle Hidden }
} catch {
    Write-Host 'Startup failed. Started services remain recorded for scripts\Stop-Cluster.ps1.'
    throw
} finally {
    foreach ($name in $savedEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process')
    }
    Pop-Location
    $operationLock.Dispose()
}
