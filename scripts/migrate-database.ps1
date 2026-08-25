param(
  [string]$JarPath,
  [string]$JdbcUrl,
  [string]$DatabaseUser,
  [string]$DatabasePassword
)

$projectRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($JarPath)) {
  $apiTarget = Join-Path $projectRoot 'dream_service\api\target'
  $jar = Get-ChildItem $apiTarget -Filter 'dream_service_api-*.jar' -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike '*.original' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
  if ($null -eq $jar) {
    throw "API JAR not found under $apiTarget. Build the API package first."
  }
  $JarPath = $jar.FullName
}

$resolvedJar = (Resolve-Path $JarPath -ErrorAction Stop).Path

$javaOptions = @()
if (-not [string]::IsNullOrWhiteSpace($JdbcUrl)) {
  $javaOptions += "-Ddatabase.jdbc.url=$JdbcUrl"
}
if (-not [string]::IsNullOrWhiteSpace($DatabaseUser)) {
  $javaOptions += "-Ddatabase.user=$DatabaseUser"
}
if ($null -ne $DatabasePassword -and $DatabasePassword.Length -gt 0) {
  $javaOptions += "-Ddatabase.password=$DatabasePassword"
}

& java @javaOptions -jar $resolvedJar --migrate-database

if ($LASTEXITCODE -ne 0) {
  exit $LASTEXITCODE
}
