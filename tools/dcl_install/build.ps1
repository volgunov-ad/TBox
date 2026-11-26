# Builds DclInstall.apk without Gradle: aapt + javac + d8 + apksigner, signed with the repo keystore.
# Bundles a freshly built :rootsvc release APK as assets/rootsvc.apk.
param(
    [string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "$env:LOCALAPPDATA\Android\Sdk" }),
    [string]$BuildTools = "",
    [string]$Jdk = $(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\Android\Android Studio\jbr" })
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
New-Item -ItemType Directory -Force "$b\wallpaper-classes", "$b\unlock-classes", "$b\app-classes", "$b\assets", "$b\dex", "$b\plan-test" | Out-Null

function Fail-Native([string]$Step) {
    if ($LASTEXITCODE -ne 0) { throw "$Step failed with exit $LASTEXITCODE" }
}

& javac -encoding UTF-8 -Xlint:-options -source 8 -target 8 -d "$b\plan-test" `
    "$here\src\vad\dashing\dclinstall\InstallPlan.java" `
    "$here\src\vad\dashing\dclinstall\MbDaemon.java" `
    "$here\src\vad\dashing\dclinstall\RootRun.java" `
    "$here\test\InstallPlanTest.java"
Fail-Native "InstallPlanTest javac"
& java -cp "$b\plan-test" vad.dashing.dclinstall.InstallPlanTest
Fail-Native "InstallPlanTest"

Write-Host "Building :rootsvc release APK for assets…"
Push-Location $repo
& .\gradlew.bat :rootsvc:assembleRelease --quiet
Fail-Native "rootsvc assembleRelease"
Pop-Location
$rootsvcApk = Get-ChildItem "$repo\rootsvc\build\outputs\apk\release\rootsvc-*-release.apk" | Select-Object -First 1
if (-not $rootsvcApk) { throw "rootsvc release APK missing after assembleRelease" }
Copy-Item $rootsvcApk.FullName "$b\assets\rootsvc.apk"
Copy-Item "$here\assets\default_wallpaper.jpg" "$b\assets\default_wallpaper.jpg"

& javac -Xlint:-options -source 8 -target 8 -bootclasspath $androidJar -d "$b\wallpaper-classes" "$here\src\SetWallpaper.java"
Fail-Native "SetWallpaper javac"
& "$BuildTools\d8.bat" --min-api 22 --lib $androidJar --output "$b\assets" "$b\wallpaper-classes\SetWallpaper.class"
Fail-Native "SetWallpaper d8"
Move-Item "$b\assets\classes.dex" "$b\assets\setwallpaper.dex"

& javac -Xlint:-options -source 8 -target 8 -bootclasspath $androidJar -d "$b\unlock-classes" "$here\src\UnlockUsb.java"
Fail-Native "UnlockUsb javac"
& "$BuildTools\d8.bat" --min-api 22 --lib $androidJar --output "$b\assets" "$b\unlock-classes\UnlockUsb.class"
Fail-Native "UnlockUsb d8"
Move-Item "$b\assets\classes.dex" "$b\assets\unlockusb.dex"

$appSources = @(Get-ChildItem -Recurse "$here\src\vad" -Filter *.java).FullName +
    @(Get-ChildItem -Recurse "$repo\shared\rootsvc\java" -Filter *.java).FullName
& javac -Xlint:-options -source 8 -target 8 -bootclasspath $androidJar -d "$b\app-classes" $appSources
Fail-Native "app javac"
& "$BuildTools\d8.bat" --min-api 22 --lib $androidJar --output "$b\dex" (Get-ChildItem -Recurse "$b\app-classes" -Filter *.class).FullName
Fail-Native "app d8"

& "$BuildTools\aapt.exe" package -f -M "$here\AndroidManifest.xml" -S "$here\res" -A "$b\assets" -I $androidJar -F "$b\unsigned.apk"
Fail-Native "aapt package"
Push-Location "$b\dex"
& "$BuildTools\aapt.exe" add "$b\unsigned.apk" classes.dex
Fail-Native "aapt add"
Pop-Location
& "$BuildTools\zipalign.exe" -f 4 "$b\unsigned.apk" "$b\aligned.apk"
Fail-Native "zipalign"
if (Test-Path "$here\DclInstall.apk") { Remove-Item "$here\DclInstall.apk" -Force }
& "$BuildTools\apksigner.bat" sign --v4-signing-enabled false --ks $ks --ks-pass pass:android --key-pass pass:android --out "$here\DclInstall.apk" "$b\aligned.apk"
Fail-Native "apksigner"
& "$BuildTools\apksigner.bat" verify "$here\DclInstall.apk"
Fail-Native "apksigner verify"
& "$BuildTools\aapt.exe" dump badging "$here\DclInstall.apk" | Select-String "package:"
Write-Host "OK $here\DclInstall.apk"
