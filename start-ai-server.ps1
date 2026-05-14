Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

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

$envPath = Join-Path $repoRoot ".env"
$envExamplePath = Join-Path $repoRoot ".env.example"
$ollamaModelsDir = Join-Path $repoRoot ".runtime\ollama"

if (-not (Test-Path $envPath)) {
    if (-not (Test-Path $envExamplePath)) {
        throw ".env.example 파일이 없어 .env를 만들 수 없습니다."
    }
    Copy-Item -Path $envExamplePath -Destination $envPath
    Write-Host "Created .env from .env.example"
}

if (-not (Test-Path $ollamaModelsDir)) {
    New-Item -Path $ollamaModelsDir -ItemType Directory | Out-Null
}

$normalizedModelsDir = $ollamaModelsDir -replace "\\", "/"

# Docker 미사용 + 서버 내 로컬 Ollama 자동 설치/실행 모드
Set-EnvValue -FilePath $envPath -Key "OLLAMA_DOCKER_ENABLED" -Value "false"
Set-EnvValue -FilePath $envPath -Key "OLLAMA_DOCKER_AUTO_START" -Value "false"
Set-EnvValue -FilePath $envPath -Key "OLLAMA_AUTO_INSTALL" -Value "true"
Set-EnvValue -FilePath $envPath -Key "OLLAMA_ALLOW_AUTO_INSTALL" -Value "true"

# 현재 실행 세션에서 Ollama 모델 저장 위치를 프로젝트 내부로 고정
$env:OLLAMA_MODELS = $ollamaModelsDir

Write-Host "Ollama models directory: $normalizedModelsDir"
Write-Host "Starting Beacon server (local Ollama mode, no Docker)..."
& ".\gradlew.bat" bootRun
