Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-MkcertPath {
    $command = Get-Command mkcert -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    $wingetMkcert = Join-Path $env:LOCALAPPDATA "Microsoft\WinGet\Packages\FiloSottile.mkcert_Microsoft.Winget.Source_8wekyb3d8bbwe\mkcert.exe"
    if (Test-Path $wingetMkcert) {
        return $wingetMkcert
    }

    throw "mkcert를 찾을 수 없습니다. 'winget install FiloSottile.mkcert' 후 다시 실행하세요."
}

function Set-EnvValue {
    param (
        [string]$FilePath,
        [string]$Key,
        [string]$Value
    )

    $escapedKey = [Regex]::Escape($Key)
    $line = "$Key=$Value"

    if (-not (Test-Path $FilePath)) {
        Set-Content -Path $FilePath -Value $line -Encoding UTF8
        return
    }

    $content = Get-Content -Path $FilePath -Raw
    if ($content -match "(?m)^$escapedKey=") {
        $content = [Regex]::Replace($content, "(?m)^$escapedKey=.*$", $line)
    } else {
        if (-not [string]::IsNullOrEmpty($content) -and -not $content.EndsWith([Environment]::NewLine)) {
            $content += [Environment]::NewLine
        }
        $content += $line + [Environment]::NewLine
    }
    Set-Content -Path $FilePath -Value $content -Encoding UTF8
}

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $repoRoot

$mkcert = Resolve-MkcertPath
$keystorePath = Join-Path $repoRoot "src\main\resources\keystore.p12"
$envPath = Join-Path $repoRoot ".env"
$envExamplePath = Join-Path $repoRoot ".env.example"

if (-not (Test-Path $envPath)) {
    if (-not (Test-Path $envExamplePath)) {
        throw ".env.example 파일이 없어 .env를 만들 수 없습니다."
    }
    Copy-Item -Path $envExamplePath -Destination $envPath
    Write-Host "Created .env from .env.example"
}

Write-Host "Installing local CA (mkcert -install)..."
& $mkcert -install

Write-Host "Generating PKCS12 keystore..."
& $mkcert -pkcs12 -p12-file $keystorePath localhost 127.0.0.1 ::1

# mkcert -pkcs12 creates a PKCS12 keystore with default password "changeit" and alias "1".
Set-EnvValue -FilePath $envPath -Key "SERVER_SSL_ENABLED" -Value "true"
Set-EnvValue -FilePath $envPath -Key "SERVER_SSL_KEYSTORE_PASSWORD" -Value "changeit"
Set-EnvValue -FilePath $envPath -Key "SERVER_SSL_KEY_ALIAS" -Value "1"

Write-Host "Starting Beacon with HTTPS..."
& ".\gradlew.bat" bootRun
