param([string[]]$Services = @())
. (Join-Path $PSScriptRoot 'Common.ps1')
$runtime = Join-Path $script:EquiFlowRoot '.local\cluster'
$path = Join-Path $runtime 'processes.json'
if (-not (Test-Path -LiteralPath $runtime -PathType Container)) {
    Write-Host 'No managed cluster processes.'
    return
}
$lock = [IO.File]::Open((Join-Path $runtime 'operation.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
try {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Write-Host 'No managed cluster processes.'
        return
    }
    $entries = @((Get-Content -LiteralPath $path -Raw | ConvertFrom-Json))
    [array]::Reverse($entries)
    $remaining = @(foreach ($entry in $entries) {
        $processId = 0
        if (-not $entry.StartedAtUtcTicks -or -not [int]::TryParse($entry.Id, [ref]$processId)) { continue }
        $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if (-not $process -or $process.ProcessName -ne 'java') { continue }
        $startedAt = $process.StartTime
        if (-not $startedAt -or [string]$startedAt.ToUniversalTime().Ticks -ne $entry.StartedAtUtcTicks) { continue }
        if ($Services.Count -gt 0 -and $entry.Name -notin $Services) {
            $entry
            continue
        }
        try { Stop-Process -InputObject $process -Force }
        catch { if (-not $process.HasExited) { throw } }
        Write-Host "Stopped $($entry.Name)."
    })
    [array]::Reverse($remaining)
    ConvertTo-Json -InputObject $remaining | Set-Content -LiteralPath $path -Encoding ASCII
} finally {
    $lock.Dispose()
}
