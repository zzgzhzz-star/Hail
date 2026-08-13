$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$lock = Get-Content (Join-Path $repoRoot 'upstream-lock.json') -Raw | ConvertFrom-Json
$hasUpdate = $false

foreach ($name in @('hail', 'gkd')) {
    $item = $lock.$name
    $remoteLine = git ls-remote $item.repository "refs/heads/$($item.branch)"
    if ($LASTEXITCODE -ne 0 -or -not $remoteLine) {
        throw "Unable to read $name upstream."
    }
    $remoteCommit = ($remoteLine -split '\s+')[0]
    $state = if ($remoteCommit -eq $item.commit) { 'up to date' } else { 'update available' }
    Write-Host "$name`: $state"
    Write-Host "  merged: $($item.commit)"
    Write-Host "  remote: $remoteCommit"
    if ($remoteCommit -ne $item.commit) {
        $hasUpdate = $true
    }
}

if ($hasUpdate) {
    Write-Error 'One or more upstream projects have new commits. Follow UPSTREAM.md to merge and validate them.'
}
