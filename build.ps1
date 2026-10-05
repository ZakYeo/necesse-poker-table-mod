param([switch]$Test)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$gameDir = 'D:\SteamLibrary\steamapps\common\Necesse'
$jdkDir = (Get-ChildItem -LiteralPath (Join-Path $projectRoot 'tools\jdk17') -Directory | Select-Object -First 1).FullName
if (-not $jdkDir) { throw 'Java 17 JDK missing from tools\jdk17.' }
$classes = Join-Path $projectRoot 'build\classes'
$dist = Join-Path $projectRoot 'dist'
New-Item -ItemType Directory -Path $classes,$dist -Force | Out-Null
$classpath = "$gameDir\Necesse.jar;$gameDir\lib\*"
$sources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src') -Recurse -Filter '*.java' | ForEach-Object FullName)
& "$jdkDir\bin\javac.exe" --release 17 -encoding UTF-8 -classpath $classpath -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }
$uiTools = Join-Path $projectRoot 'build\ui-tools'
New-Item -ItemType Directory -Path $uiTools -Force | Out-Null
& "$jdkDir\bin\javac.exe" --release 17 -encoding UTF-8 -classpath $classes -d $uiTools (Join-Path $projectRoot 'tools\ExportPokerUi.java')
if ($LASTEXITCODE -ne 0) { throw 'UI settings exporter compilation failed.' }
& "$jdkDir\bin\java.exe" -classpath "$classes;$uiTools" poker.ExportPokerUi (Join-Path $projectRoot 'ui')
if ($LASTEXITCODE -ne 0) { throw 'Could not create default UI settings.' }
& "$jdkDir\bin\java.exe" -classpath $classpath CreateModInfoFile -file "$classes\mod.info" -id 'zakye.poker' -name 'Poker Table' -version '0.1.0' -gameVersion '1.3.3' -description 'Texas Holdem poker with settlers and multiplayer. Coins, side pots, and unlimited NPC rebuys.' -author 'Zakye' -clientside 'false'
if ($LASTEXITCODE -ne 0) { throw 'Could not create mod.info.' }
& "$jdkDir\bin\jar.exe" --create --file "$dist\PokerTable-1.3.3-0.1.0.jar" -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Packaging failed.' }
if ($Test) {
    $testSources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'tests') -Filter '*.java' | ForEach-Object FullName)
    New-Item -ItemType Directory -Path "$projectRoot\build\tests" -Force | Out-Null
    & "$jdkDir\bin\javac.exe" --release 17 -encoding UTF-8 -classpath "$classes;$classpath" -d "$projectRoot\build\tests" $testSources
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
    & "$jdkDir\bin\java.exe" -ea -classpath "$projectRoot\build\tests;$classes;$classpath" poker.PokerGameTest
    if ($LASTEXITCODE -ne 0) { throw 'Poker rule tests failed.' }
    & "$jdkDir\bin\java.exe" '-Djava.awt.headless=true' -ea -classpath "$projectRoot\build\tests;$classes;$classpath" poker.PokerUiSettingsTest
    if ($LASTEXITCODE -ne 0) { throw 'Poker UI settings tests failed.' }
}
Write-Host "Built $dist\PokerTable-1.3.3-0.1.0.jar"
