$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$jdk = (Get-ChildItem -LiteralPath "$root\tools\jdk17" -Directory | Select-Object -First 1).FullName
$game = 'D:\SteamLibrary\steamapps\common\Necesse'
$output = "$root\build\smoke-classes"
New-Item -ItemType Directory -Path $output,"$root\build\smoke-data\mods" -Force | Out-Null
$testSources = @(Get-ChildItem -LiteralPath "$root\tests\integration" -Filter '*.java' | ForEach-Object FullName)
& "$jdk\bin\javac.exe" --release 17 -encoding UTF-8 -classpath "$root\build\classes;$game\Necesse.jar;$game\lib\*" -d $output $testSources
if ($LASTEXITCODE -ne 0) { throw 'Smoke test compilation failed.' }
@'
{
    id = zakye.pokersmoke,
    name = Poker Integration Test,
    version = 1.0,
    gameVersion = 1.3.3,
    author = Local,
    description = Temporary integration tests,
    clientside = false,
    dependencies = [zakye.poker]
}
'@ | Set-Content -LiteralPath "$output\mod.info" -Encoding utf8
& "$jdk\bin\jar.exe" --create --file "$root\build\smoke-data\mods\PokerSmoke.jar" -C $output .
if ($LASTEXITCODE -ne 0) { throw 'Smoke test packaging failed.' }
