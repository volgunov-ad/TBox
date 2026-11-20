$ErrorActionPreference = "Stop"

$root = "C:\Users\volgu\AppData\Local\Temp\rufp"
$gcc  = Join-Path $root "toolchain\mingw64\bin\gcc.exe"
$sdl  = Join-Path $root "sdl2\SDL2-2.32.10\x86_64-w64-mingw32"
$proj = "C:\Users\volgu\AndroidStudioProjects\1\firmware\esp32-climate-display"
$lvgl = Join-Path $proj "managed_components\lvgl__lvgl"
$sim  = Join-Path $proj "sim"
$out  = Join-Path $sim "build"

New-Item -ItemType Directory -Force -Path $out | Out-Null

$sources = @(Get-ChildItem (Join-Path $lvgl "src") -Recurse -Filter *.c | ForEach-Object { $_.FullName -replace "\\", "/" })
$sources += ($sim + "/main.c") -replace "\\", "/"
$sources += ($proj + "/main/climate_model.c") -replace "\\", "/"
$sources += ($proj + "/main/climate_view.c") -replace "\\", "/"

$incLvgl = "-I" + ($lvgl -replace "\\", "/")
$incSim  = "-I" + ($sim -replace "\\", "/")
$incMain = "-I" + ($proj -replace "\\", "/") + "/main"
$incStub = $incSim + "/stubs"
$incSdl  = "-I" + ($sdl -replace "\\", "/") + "/include"
$libSdl  = "-L" + ($sdl -replace "\\", "/") + "/lib"

$flags = @(
    "-O2",
    "-std=gnu11",
    "-DLV_CONF_INCLUDE_SIMPLE",
    $incLvgl,
    $incSim,
    $incMain,
    $incStub,
    $incSdl,
    $libSdl
)
$libs = @("-lmingw32", "-lSDL2main", "-lSDL2")

$resp = Join-Path $out "objs.rsp"
$lines = @($flags) + $sources + $libs
Set-Content -Path $resp -Value $lines -Encoding ASCII

$exe = Join-Path $out "sim.exe"
$ErrorActionPreference = "Continue"
$output = & $gcc "@$resp" -o $exe 2>&1
$exit = $LASTEXITCODE
$ErrorActionPreference = "Stop"
$output | Out-File (Join-Path $out "build.log") -Encoding UTF8
if ($exit -ne 0) {
    Write-Output "BUILD FAILED exit=$exit (see build.log)"
    Get-Content (Join-Path $out "build.log") -Tail 40
    exit 1
}

Copy-Item (Join-Path $sdl "bin\SDL2.dll") $out -Force
Write-Output "BUILD OK -> $exe"
