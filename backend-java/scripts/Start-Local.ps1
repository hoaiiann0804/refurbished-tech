param(
    [ValidateRange(1024, 65535)][int]$Port = 8080,
    [switch]$RecoverAdmin
)
$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path $PSScriptRoot -Parent
$jarPath = Join-Path $backendRoot 'target/refurbished-backend-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jarPath)) { throw 'Build first: .\mvnw.cmd clean verify' }
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
    throw "Port $Port is in use. Stop the previous backend or use -Port 8082."
}
& (Join-Path $PSScriptRoot 'Use-LocalEnvironment.ps1')

# Không để recovery/bootstrap còn sót trong terminal tự chạy lại ở lần khởi động sau.
$oneTimeNames = @('REFURBISHED_RECOVERY_ADMIN_EMAIL', 'REFURBISHED_RECOVERY_ADMIN_PASSWORD',
    'REFURBISHED_BOOTSTRAP_ADMIN_EMAIL', 'REFURBISHED_BOOTSTRAP_ADMIN_PASSWORD')
$previousValues = @{}
foreach ($name in $oneTimeNames) {
    $previousValues[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    [Environment]::SetEnvironmentVariable($name, $null, 'Process')
}
try {
    if ($RecoverAdmin) {
        $email = (Read-Host 'Existing enabled ADMIN email').Trim()
        if ([string]::IsNullOrWhiteSpace($email)) { throw 'ADMIN email is required.' }
        $newPassword = [System.Net.NetworkCredential]::new('', (Read-Host 'New password' -AsSecureString)).Password
        $confirmation = [System.Net.NetworkCredential]::new('', (Read-Host 'Confirm new password' -AsSecureString)).Password
        if ($newPassword -cne $confirmation) { throw 'Passwords do not match.' }
        if ([string]::IsNullOrWhiteSpace($newPassword) -or $newPassword.Length -lt 12 -or
            [System.Text.Encoding]::UTF8.GetByteCount($newPassword) -gt 72) {
            throw 'Password requires at least 12 characters and at most 72 UTF-8 bytes.'
        }
        $env:REFURBISHED_RECOVERY_ADMIN_EMAIL = $email
        $env:REFURBISHED_RECOVERY_ADMIN_PASSWORD = $newPassword
    }
    # Chỉ dùng profile dev: LocalDataSourceConfiguration vẫn bảo vệ database đích.
    & java -jar $jarPath '--spring.profiles.active=dev' "--server.port=$Port"
    if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE. See the preceding error." }
} finally {
    $newPassword = $null
    $confirmation = $null
    foreach ($name in $oneTimeNames) {
        [Environment]::SetEnvironmentVariable($name, $previousValues[$name], 'Process')
    }
}
