param(
    [switch]$Fetch
)

$ErrorActionPreference = 'Stop'

$upstreams = [ordered]@{
    'hail-upstream' = 'https://github.com/aistra0528/Hail.git'
    'gkd-upstream' = 'https://github.com/gkd-kit/gkd.git'
}

foreach ($entry in $upstreams.GetEnumerator()) {
    $existing = git remote get-url $entry.Key 2>$null
    if ($LASTEXITCODE -eq 0) {
        git remote set-url $entry.Key $entry.Value
    } else {
        git remote add $entry.Key $entry.Value
    }
    Write-Host "$($entry.Key) -> $($entry.Value)"
}

if ($Fetch) {
    git fetch --prune hail-upstream master
    git fetch --prune gkd-upstream main
}
