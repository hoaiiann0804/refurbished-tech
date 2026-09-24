$ErrorActionPreference = 'Stop'
$localEnvPath = Join-Path (Split-Path $PSScriptRoot -Parent) '.env.local'
if (-not (Test-Path -LiteralPath $localEnvPath)) {
    throw 'Run scripts/Initialize-Local.ps1 first.'
}

$requiredNames = @('REFURBISHED_DEV_PASSWORD', 'REFURBISHED_TEST_PASSWORD', 'REFURBISHED_JWT_SECRET')
$loadedNames = @()
foreach ($line in [System.IO.File]::ReadAllLines($localEnvPath)) {
    if ($line -match '^(REFURBISHED_(DEV|TEST)_PASSWORD)=([a-f0-9]{64})$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[3], 'Process')
        $loadedNames += $Matches[1]
    }
    if ($line -match '^(REFURBISHED_JWT_SECRET)=([a-f0-9]{64})$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
        $loadedNames += $Matches[1]
    }
}
foreach ($name in $requiredNames) {
    if ($loadedNames -notcontains $name) { throw "Missing or invalid local variable: $name" }
}
Write-Output 'Loaded application passwords and JWT secret; bootstrap/OAuth credentials were not loaded.'
