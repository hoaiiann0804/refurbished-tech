# Kiểm tra workflow script trong thư mục cô lập; không khởi động Java hoặc sửa ADMIN thật.
$ErrorActionPreference = 'Stop'
$sandboxRoot = Join-Path (Split-Path $PSScriptRoot -Parent) ('.run/script-tests-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path (Join-Path $sandboxRoot 'scripts'), (Join-Path $sandboxRoot 'target') -Force | Out-Null
$runner = Join-Path $sandboxRoot 'scripts/Start-Local.ps1'
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Start-Local.ps1') -Destination $runner
$fakeJar = Join-Path $sandboxRoot 'target/refurbished-backend-0.0.1-SNAPSHOT.jar'
$envLoader = Join-Path $sandboxRoot 'scripts/Use-LocalEnvironment.ps1'
$global:startLocalTestPortBusy = $false
$global:startLocalTestJavaExit = 0
$global:startLocalTestJavaCalled = $false
$global:startLocalTestAnswers = [System.Collections.Generic.Queue[string]]::new()
$global:startLocalTestObservedEmail = $null

function Get-NetTCPConnection { param($LocalPort, $State, $ErrorAction) if ($global:startLocalTestPortBusy) { return 'listener' } }
function Read-Host {
    param($Prompt, [switch]$AsSecureString)
    $answer = $global:startLocalTestAnswers.Dequeue()
    if ($AsSecureString) { return ConvertTo-SecureString $answer -AsPlainText -Force }
    return $answer
}
function java {
    $global:startLocalTestJavaCalled = $true
    $global:startLocalTestObservedEmail = $env:REFURBISHED_RECOVERY_ADMIN_EMAIL
    $global:LASTEXITCODE = $global:startLocalTestJavaExit
}
function Expect-Failure([scriptblock]$Action, [string]$Message) {
    $caught = $false
    try { & $Action } catch {
        $caught = $true
        if ($_.Exception.Message -notlike "*$Message*") { throw }
    }
    if (-not $caught) { throw "Expected failure: $Message" }
}
function Set-Answers([string[]]$Values) {
    $global:startLocalTestAnswers.Clear()
    foreach ($value in $Values) { $global:startLocalTestAnswers.Enqueue($value) }
}

Expect-Failure { & $runner } 'Build first'
Set-Content -LiteralPath $fakeJar -Value 'fixture'
Set-Content -LiteralPath $envLoader -Value "throw 'Run scripts/Initialize-Local.ps1 first.'"
Expect-Failure { & $runner } 'Initialize-Local'
Set-Content -LiteralPath $envLoader -Value "Write-Output 'Fixture environment loaded.'"
$global:startLocalTestPortBusy = $true
Expect-Failure { & $runner } 'Port 8080 is in use'
$global:startLocalTestPortBusy = $false

$originalEmail = $env:REFURBISHED_RECOVERY_ADMIN_EMAIL
$originalPassword = $env:REFURBISHED_RECOVERY_ADMIN_PASSWORD
try {
    $env:REFURBISHED_RECOVERY_ADMIN_EMAIL = 'caller@test.local'
    $env:REFURBISHED_RECOVERY_ADMIN_PASSWORD = 'CallerFixturePassword123!'
    & $runner
    if (-not $global:startLocalTestJavaCalled -or $global:startLocalTestObservedEmail) { throw 'Normal start inherited recovery values.' }
    Set-Answers @('admin@test.local', 'FixturePassword123!', 'DifferentPassword123!')
    Expect-Failure { & $runner -RecoverAdmin } 'Passwords do not match'
    Set-Answers @('admin@test.local', 'short', 'short')
    Expect-Failure { & $runner -RecoverAdmin } 'at least 12'
    Set-Answers @('admin@test.local', 'FixturePassword123!', 'FixturePassword123!')
    & $runner -RecoverAdmin -Port 8082
    if ($global:startLocalTestObservedEmail -ne 'admin@test.local') { throw 'Recovery email was not passed to Java.' }
    if ($env:REFURBISHED_RECOVERY_ADMIN_EMAIL -ne 'caller@test.local' -or
        $env:REFURBISHED_RECOVERY_ADMIN_PASSWORD -ne 'CallerFixturePassword123!') {
        throw 'Caller environment was not restored.'
    }
    $global:startLocalTestJavaExit = 1
    Expect-Failure { & $runner } 'Backend exited with code 1'
    Write-Output 'PASS: missing JAR/env, busy port, normal start, password validation, recovery, environment restore, Java failure.'
} finally {
    $env:REFURBISHED_RECOVERY_ADMIN_EMAIL = $originalEmail
    $env:REFURBISHED_RECOVERY_ADMIN_PASSWORD = $originalPassword
}
