param([Parameter(Mandatory=$true)][string]$Root)
$ErrorActionPreference = 'Stop'
$repos = @($Root)
$subs = git -C $Root submodule status --recursive 2>$null
foreach ($line in $subs) {
    $p = $line.TrimStart('+','-','U',' ')
    $parts = $p.Split(' ')
    if ($parts.Length -ge 2) { $repos += (Join-Path $Root $parts[1]) }
}
$fixed = 0
$failed = 0
foreach ($repo in ($repos | Where-Object { $_ -and (Test-Path $_) })) {
    $links = git -C $repo ls-files -s 2>$null | Where-Object { $_ -match '^120000' }
    foreach ($l in $links) {
        $rel = ($l -split "`t")[-1]
        $linkPath = Join-Path $repo $rel
        if (-not (Test-Path $linkPath)) { Write-Host "MISSING: $linkPath"; $failed++; continue }
        $targetRel = (Get-Content -LiteralPath $linkPath -Raw).Trim()
        $targetPath = Join-Path (Split-Path $linkPath -Parent) $targetRel
        if (-not (Test-Path $targetPath)) { Write-Host "BROKEN: $linkPath -> $targetRel"; $failed++; continue }
        Copy-Item -LiteralPath $targetPath -Destination $linkPath -Force
        $fixed++
    }
}
Write-Host "fixed=$fixed failed=$failed"