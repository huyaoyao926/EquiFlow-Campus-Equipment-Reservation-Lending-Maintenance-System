$ErrorActionPreference = 'Stop'

function New-PortableSecret {
    $bytes = New-Object byte[] 32
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    } finally {
        $generator.Dispose()
    }

    return [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
}

Push-Location -LiteralPath $PSScriptRoot
try {
    if (-not (Test-Path -LiteralPath '.env.portable')) {
        $settings = @(
            'EQUIFLOW_MYSQL_HOST=mysql'
            'EQUIFLOW_MYSQL_PORT=3306'
            'EQUIFLOW_DATABASE_USER=equiflow'
            "EQUIFLOW_DATABASE_PASSWORD=$(New-PortableSecret)"
            "EQUIFLOW_MYSQL_ROOT_PASSWORD=$(New-PortableSecret)"
            "EQUIFLOW_SERVICE_KEY=$(New-PortableSecret)"
            'EQUIFLOW_HTTP_PORT=8087'
        )
        $settings | Set-Content -LiteralPath '.env.portable' -Encoding Ascii
    }

    $compose = @('--env-file', '.env.portable', '-f', 'compose.yaml', '-f', 'compose.portable.yaml')
    & docker compose @compose build registry
    if ($LASTEXITCODE -ne 0) { throw 'The EquiFlow image build failed.' }

    & docker compose @compose up -d --no-build --wait
    if ($LASTEXITCODE -ne 0) { throw 'EquiFlow did not start successfully.' }

    Write-Output 'EquiFlow is ready at http://127.0.0.1:8087'
} finally {
    Pop-Location
}
