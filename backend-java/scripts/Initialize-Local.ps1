$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path $PSScriptRoot -Parent
$localEnvPath = Join-Path $backendRoot '.env.local'

function New-LocalPassword {
    $bytes = New-Object byte[] 32
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
    return [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
}

if (Test-Path -LiteralPath $localEnvPath) {
    $lines = [System.IO.File]::ReadAllLines($localEnvPath)
    if ($lines -notmatch '^REFURBISHED_JWT_SECRET=') {
        [System.IO.File]::AppendAllText($localEnvPath,
            [Environment]::NewLine + 'REFURBISHED_JWT_SECRET=' + (New-LocalPassword) + [Environment]::NewLine,
            [System.Text.Encoding]::ASCII)
        Write-Output 'Added a new local JWT secret to .env.local. Existing credentials were not changed.'
    } else {
        Write-Output '.env.local already contains a JWT secret; credentials were not changed.'
    }
    return
}

$content = @(
    '# Generated locally for refurbished-java-local only. Never commit this file.'
    ('REFURBISHED_DEV_ADMIN_PASSWORD=' + (New-LocalPassword))
    ('REFURBISHED_DEV_PASSWORD=' + (New-LocalPassword))
    ('REFURBISHED_TEST_ADMIN_PASSWORD=' + (New-LocalPassword))
    ('REFURBISHED_TEST_PASSWORD=' + (New-LocalPassword))
    ('REFURBISHED_JWT_SECRET=' + (New-LocalPassword))
)
[System.IO.File]::WriteAllLines($localEnvPath, $content, [System.Text.Encoding]::ASCII)
Write-Output 'Created .env.local with development-only database passwords and JWT secret. Values are not printed.'
