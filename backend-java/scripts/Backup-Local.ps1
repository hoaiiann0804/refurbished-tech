param(
    [ValidateSet('dev','test')][string]$Source = 'dev',
    [string]$DockerHost = 'npipe:////./pipe/dockerDesktopLinuxEngine'
)
$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path $PSScriptRoot -Parent
& (Join-Path $PSScriptRoot 'Use-LocalEnvironment.ps1')
$container = "refurbished-java-local-db-$Source-1"
$database = "refurbished_$Source"
$role = if ($Source -eq 'dev') { 'refurbished_app' } else { 'refurbished_test' }
$backupId = [Guid]::NewGuid().ToString('N')
$directory = Join-Path $backendRoot '.run/backups'
[void][System.IO.Directory]::CreateDirectory($directory)
$destination = Join-Path $directory "$database-$backupId.dump"
$remote = "/tmp/refurbished-$backupId.dump"
$oldPgPassword = $env:PGPASSWORD
try {
    $env:PGPASSWORD = if ($Source -eq 'dev') { $env:REFURBISHED_DEV_PASSWORD } else { $env:REFURBISHED_TEST_PASSWORD }
    # Password is inherited through Docker's -e NAME, never placed in the argument string.
    & docker --host $DockerHost exec -e PGPASSWORD $container pg_dump -h 127.0.0.1 -U $role -d $database -Fc -f $remote
    if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed; no valid backup was produced.' }
    & docker --host $DockerHost cp "${container}:$remote" $destination
    if ($LASTEXITCODE -ne 0) { throw 'Copying the dump failed.' }
    $metadata = @{ database=$database; createdAt=[DateTime]::UtcNow.ToString('o'); sha256=(Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash; format='PostgreSQL custom'; postgresMajor=17 }
    [System.IO.File]::WriteAllText("$destination.json", ($metadata | ConvertTo-Json))
    Write-Output "Backup: $destination"
    Write-Output 'Contains database data. Keep access restricted and copy to encrypted off-host storage.'
} finally {
    $env:PGPASSWORD = $oldPgPassword
    # Only the unique temporary file created by this invocation is removed.
    & docker --host $DockerHost exec $container rm -f -- $remote
}
