param(
    [Parameter(Mandatory=$true)][string]$BackupPath,
    [string]$DockerHost = 'npipe:////./pipe/dockerDesktopLinuxEngine'
)
$ErrorActionPreference = 'Stop'
$dump = (Resolve-Path -LiteralPath $BackupPath).Path
$metadata = Get-Content -LiteralPath "$dump.json" -Raw | ConvertFrom-Json
if ($metadata.postgresMajor -ne 17 -or (Get-FileHash -LiteralPath $dump -Algorithm SHA256).Hash -ne $metadata.sha256) { throw 'Backup checksum/version does not match metadata.' }
$name = 'refurbished-restore-drill-' + [Guid]::NewGuid().ToString('N')
$created = $false
$started = [DateTime]::UtcNow
try {
    # No published ports/network/volume: a restore drill cannot overwrite dev or staging.
    & docker --host $DockerHost run -d --name $name --network none --tmpfs /var/lib/postgresql/data -e POSTGRES_HOST_AUTH_METHOD=trust postgres:17-alpine@sha256:b0f9560a2de083e2cc7382e75f808c7381a32852a7ec49117deedb300e552b24 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not start isolated restore container.' }
    $created = $true
    $ready = $false
    for ($attempt=0; $attempt -lt 30; $attempt++) {
        & docker --host $DockerHost exec $name pg_isready -h 127.0.0.1 -U postgres 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready=$true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'Restore database did not become ready.' }
    & docker --host $DockerHost exec $name createdb -U postgres restore_drill
    if ($LASTEXITCODE -ne 0) { throw 'Creating isolated target failed.' }
    & docker --host $DockerHost cp $dump "${name}:/tmp/backup.dump"
    if ($LASTEXITCODE -ne 0) { throw 'Copying backup failed.' }
    & docker --host $DockerHost exec $name pg_restore --exit-on-error --no-owner --no-privileges -U postgres -d restore_drill /tmp/backup.dump
    if ($LASTEXITCODE -ne 0) { throw 'Restore failed.' }
    $query = "SELECT json_build_object('migrations',(SELECT count(*) FROM flyway_schema_history WHERE success),'products',(SELECT count(*) FROM products),'devices',(SELECT count(*) FROM device_units),'orders',(SELECT count(*) FROM sales_orders),'audit',(SELECT count(*) FROM audit_events));"
    $counts = & docker --host $DockerHost exec $name psql -U postgres -d restore_drill -At -v ON_ERROR_STOP=1 -c $query
    if ($LASTEXITCODE -ne 0) { throw 'Restored schema verification failed.' }
    $parsed = $counts | ConvertFrom-Json
    if ($parsed.migrations -lt 8) { throw 'Expected at least V8 schema for this release.' }
    $result = @{ result='PASS'; durationSeconds=([DateTime]::UtcNow-$started).TotalSeconds; sourceHash=$metadata.sha256; restoredCounts=$parsed }
    [System.IO.File]::WriteAllText("$dump.restore.json",($result | ConvertTo-Json -Depth 5))
    Write-Output ($result | ConvertTo-Json -Depth 5)
} finally {
    # This exact container was created here; no user-supplied target accepts destructive restore.
    if ($created) { & docker --host $DockerHost rm -f $name | Out-Null }
}
