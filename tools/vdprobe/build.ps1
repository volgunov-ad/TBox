# Builds VdProbe.apk without Gradle. A host window shows another activity on a virtual display.
param(
    [string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "$env:LOCALAPPDATA\Android\Sdk" }),
    [string]$BuildTools = "",
    [string]$Jdk = $(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot" })
)
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Resolve-Path (Join-Path $here "..\..")
if (-not $BuildTools) {
    $BuildTools = (Get-ChildItem "$Sdk\build-tools" -Directory | Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } | Sort-Object { [version]$_.Name } | Select-Object -Last 1).FullName
}
$env:JAVA_HOME = $Jdk
$env:PATH = "$Jdk\bin;$env:PATH"
$androidJar = "$Sdk\platforms\android-28\android.jar"
$ks = "$repo\keystore\debug.keystore"
$b = "$here\build"
Remove-Item -Recurse -Force $b -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$b\classes", "$b\dex" | Out-Null

& "$BuildTools\aapt2.exe" link -o "$b\unsigned.apk" -I $androidJar --manifest "$here\AndroidManifest.xml"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& javac -encoding UTF-8 -Xlint:-options -source 8 -target 8 -bootclasspath $androidJar -classpath $androidJar -d "$b\classes" (Get-ChildItem -Recurse "$here\src" -Filter *.java).FullName
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& "$BuildTools\d8.bat" --min-api 26 --lib $androidJar --output "$b\dex" (Get-ChildItem -Recurse "$b\classes" -Filter *.class).FullName
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Push-Location "$b\dex"
& jar uf "$b\unsigned.apk" classes.dex
Pop-Location
& "$BuildTools\zipalign.exe" -f 4 "$b\unsigned.apk" "$b\aligned.apk"
& "$BuildTools\apksigner.bat" sign --v4-signing-enabled false --ks $ks --ks-pass pass:android --key-pass pass:android --out "$here\VdProbe.apk" "$b\aligned.apk"
& "$BuildTools\apksigner.bat" verify "$here\VdProbe.apk"
Write-Host "OK $here\VdProbe.apk"
