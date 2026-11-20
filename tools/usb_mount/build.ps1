# Builds UsbMount.apk without Gradle: aapt + javac + d8 + apksigner, signed with the repo keystore.
# See docs/SYSTEMUI_A9_DISABLE_RU.md.
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
New-Item -ItemType Directory -Force "$b\unlock-classes", "$b\app-classes", "$b\assets", "$b\dex" | Out-Null

& javac -Xlint:-options -source 8 -target 8 -bootclasspath $androidJar -d "$b\unlock-classes" "$here\src\UnlockUsb.java"
& "$BuildTools\d8.bat" --min-api 22 --lib $androidJar --output "$b\assets" "$b\unlock-classes\UnlockUsb.class"
Move-Item "$b\assets\classes.dex" "$b\assets\unlockusb.dex"

& javac -Xlint:-options -source 8 -target 8 -bootclasspath $androidJar -d "$b\app-classes" (Get-ChildItem -Recurse "$here\src\vad" -Filter *.java).FullName
& "$BuildTools\d8.bat" --min-api 22 --lib $androidJar --output "$b\dex" (Get-ChildItem -Recurse "$b\app-classes" -Filter *.class).FullName

& "$BuildTools\aapt.exe" package -f -M "$here\AndroidManifest.xml" -A "$b\assets" -I $androidJar -F "$b\unsigned.apk"
Push-Location "$b\dex"
& "$BuildTools\aapt.exe" add "$b\unsigned.apk" classes.dex
Pop-Location
& "$BuildTools\zipalign.exe" -f 4 "$b\unsigned.apk" "$b\aligned.apk"
& "$BuildTools\apksigner.bat" sign --v4-signing-enabled false --ks $ks --ks-pass pass:android --key-pass pass:android --out "$here\UsbMount.apk" "$b\aligned.apk"
& "$BuildTools\apksigner.bat" verify "$here\UsbMount.apk"
Write-Host "OK $here\UsbMount.apk"
