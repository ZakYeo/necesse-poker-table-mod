$ErrorActionPreference = 'Stop'
$artifact = Join-Path $PSScriptRoot 'dist\PokerTable-1.3.3-0.1.0.jar'
if (-not (Test-Path -LiteralPath $artifact)) { throw 'Run build.ps1 first.' }
$uiFolder = Join-Path $PSScriptRoot 'ui'
if (-not (Test-Path -LiteralPath (Join-Path $uiFolder 'poker-ui.properties'))) {
    throw 'UI settings missing. Run build.ps1 before installing.'
}
$modsFolder = Join-Path $env:APPDATA 'Necesse\mods'
New-Item -ItemType Directory -Path $modsFolder -Force | Out-Null
$destination = Join-Path $modsFolder 'PokerTable-1.3.3-0.1.0.jar'
if (Test-Path -LiteralPath $destination) {
    $oldHash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash
    $newHash = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash
    if ($oldHash -ne $newHash) {
        $backupFolder = Join-Path $PSScriptRoot 'build\install-backups'
        New-Item -ItemType Directory -Path $backupFolder -Force | Out-Null
        Copy-Item -LiteralPath $destination -Destination (Join-Path $backupFolder ((Get-Date -Format 'yyyyMMdd-HHmmss') + '-PokerTable.jar'))
    }
}
Copy-Item -LiteralPath $artifact -Destination $destination -Force
$uiConfigFolder = Join-Path $env:APPDATA 'Necesse\cfg\poker-ui'
New-Item -ItemType Directory -Path $uiConfigFolder -Force | Out-Null
[System.IO.File]::WriteAllText((Join-Path $uiConfigFolder 'source.txt'), $uiFolder, [System.Text.UTF8Encoding]::new($false))
Write-Host "Installed: $destination"
Write-Host "Live UI settings: $uiFolder\poker-ui.properties"
Write-Host 'After this installation, restart once. Future UI settings/art edits load when you reopen the table.'
Write-Host 'Restart Necesse and enable Poker Table in Mods if needed.'
