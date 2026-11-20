# Builds tools/dclwm/dclwm.dex (app_process helper) and runs the desktop session checks.
param(
    [string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "$env:LOCALAPPDATA\Android\Sdk" }),
    [string]$BuildTools = "",
    [string]$Jdk = $(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot" })
)
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $BuildTools) {
    $BuildTools = (Get-ChildItem "$Sdk\build-tools" -Directory | Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } | Sort-Object { [version]$_.Name } | Select-Object -Last 1).FullName
}
$env:JAVA_HOME = $Jdk
$env:PATH = "$Jdk\bin;$env:PATH"
$androidJar = "$Sdk\platforms\android-28\android.jar"
$b = "$here\build"
Remove-Item -Recurse -Force $b -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$b\check", "$b\classes" | Out-Null

$pure = @(
    "$here\src\vad\dashing\dclwm\Protocol.java",
    "$here\src\vad\dashing\dclwm\WindowTask.java",
    "$here\src\vad\dashing\dclwm\StackListParser.java",
    "$here\src\vad\dashing\dclwm\LauncherUid.java",
    "$here\src\vad\dashing\dclwm\AmFailure.java",
    "$here\src\vad\dashing\dclwm\Am.java",
    "$here\src\vad\dashing\dclwm\Session.java",
    "$here\src\vad\dashing\dclwm\ProcessAm.java",
    "$here\src\vad\dashing\dclwm\ArmScript.java",
    "$here\test\SessionTest.java"
)
& javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -d "$b\check" $pure
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -cp "$b\check" vad.dashing.dclwm.SessionTest
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$src = (Get-ChildItem -Recurse "$here\src" -Filter *.java).FullName
& javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -bootclasspath $androidJar -classpath $androidJar -d "$b\classes" $src
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& "$BuildTools\d8.bat" --min-api 28 --lib $androidJar --output "$b" (Get-ChildItem -Recurse "$b\classes" -Filter *.class).FullName
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Copy-Item "$b\classes.dex" "$here\dclwm.dex" -Force
Write-Host "OK $here\dclwm.dex"
