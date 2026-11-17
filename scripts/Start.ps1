param(
    [ValidateSet('demo','mysql')][string]$Profile = 'demo',
    [string]$ListenAddress = '127.0.0.1',
    [string]$MySqlHome,
    [string]$MySqlRuntimeDirectory,
    [ValidateRange(1,65535)][int]$MySqlPort = 3316,
    [switch]$RestoreDump,
    [switch]$OpenBrowser
)
& (Join-Path $PSScriptRoot 'Start-Cluster.ps1') @PSBoundParameters
