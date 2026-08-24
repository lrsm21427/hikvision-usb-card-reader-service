$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$toolsDir = Join-Path $projectRoot 'tools'
$archivePath = Join-Path $toolsDir 'wix314-binaries.zip'
$wixDir = Join-Path $toolsDir 'wix314'
$candlePath = Join-Path $wixDir 'candle.exe'
$downloadUrl = 'https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip'
$expectedSha256 = '6ac824e1642d6f7277d0ed7ea09411a508f6116ba6fae0aa5f2c7daa2ff43d31'

if (Test-Path -LiteralPath $candlePath) {
    Write-Host '[OK] Portable WiX is ready.'
    exit 0
}

New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
if (-not (Test-Path -LiteralPath $archivePath)) {
    Write-Host 'Downloading portable WiX 3.14.1...'
    Invoke-WebRequest -Uri $downloadUrl -OutFile $archivePath
}

$actualSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $archivePath).Hash.ToLowerInvariant()
if ($actualSha256 -ne $expectedSha256) {
    throw "WiX archive SHA-256 mismatch: $actualSha256"
}

Expand-Archive -LiteralPath $archivePath -DestinationPath $wixDir -Force
if (-not (Test-Path -LiteralPath $candlePath)) {
    throw 'Portable WiX extraction failed.'
}

Write-Host '[OK] Portable WiX is ready.'
