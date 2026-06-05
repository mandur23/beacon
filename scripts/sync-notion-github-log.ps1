# Notion page body sync (requires Integration token + page ID)
# Usage:
#   $env:NOTION_TOKEN = "secret_..."
#   $env:NOTION_PAGE_ID = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
#   .\scripts\sync-notion-github-log.ps1

param(
    [string]$Token = $env:NOTION_TOKEN,
    [string]$PageId = $env:NOTION_PAGE_ID,
    [string]$MarkdownPath = "$PSScriptRoot\..\docs\notion-github-work-log.md"
)

if (-not $Token -or -not $PageId) {
    Write-Error "Set NOTION_TOKEN and NOTION_PAGE_ID (Integration must have access to the page)."
    exit 1
}

if (-not (Test-Path $MarkdownPath)) {
    Write-Error "Missing file: $MarkdownPath"
    exit 1
}

$md = Get-Content $MarkdownPath -Raw -Encoding UTF8
# Notion API: append as paragraph blocks (simple split; for rich blocks use a dedicated converter)
$lines = $md -split "`n" | Where-Object { $_.Trim() -ne "" }

$children = foreach ($line in $lines) {
    if ($line -match '^# (.+)$') {
        @{ object = "block"; type = "heading_1"; heading_1 = @{ rich_text = @(@{ type = "text"; text = @{ content = $Matches[1].Substring(0, [Math]::Min(2000, $Matches[1].Length)) } }) } }
    } elseif ($line -match '^## (.+)$') {
        @{ object = "block"; type = "heading_2"; heading_2 = @{ rich_text = @(@{ type = "text"; text = @{ content = $Matches[1].Substring(0, [Math]::Min(2000, $Matches[1].Length)) } }) } }
    } elseif ($line -match '^### (.+)$') {
        @{ object = "block"; type = "heading_3"; heading_3 = @{ rich_text = @(@{ type = "text"; text = @{ content = $Matches[1].Substring(0, [Math]::Min(2000, $Matches[1].Length)) } }) } }
    } else {
        $text = $line.Substring(0, [Math]::Min(2000, $line.Length))
        @{ object = "block"; type = "paragraph"; paragraph = @{ rich_text = @(@{ type = "text"; text = @{ content = $text } }) } }
    }
}

$body = @{
    children = @($children | Select-Object -First 100)
} | ConvertTo-Json -Depth 20

$headers = @{
    Authorization  = "Bearer $Token"
    "Notion-Version" = "2022-06-28"
    "Content-Type" = "application/json"
}

# Clear existing blocks (optional: list and delete - skipped for safety)
$uri = "https://api.notion.com/v1/blocks/$PageId/children"
try {
    Invoke-RestMethod -Uri $uri -Method Patch -Headers $headers -Body $body
    Write-Host "Appended blocks to Notion page $PageId (max 100 blocks per run)."
} catch {
    Write-Error $_.Exception.Message
    if ($_.ErrorDetails.Message) { Write-Error $_.ErrorDetails.Message }
    exit 1
}
