@echo off
setlocal
set "SCRIPT=%~dp0align_elf_load_16k.py"
set "LIB=%~dp0..\app\src\main\jniLibs\arm64-v8a\libmbCan.so"

py -3 "%SCRIPT%" "%LIB%" -o "%LIB%" 2>nul && exit /b 0
python "%SCRIPT%" "%LIB%" -o "%LIB%" 2>nul && exit /b 0
python3 "%SCRIPT%" "%LIB%" -o "%LIB%" 2>nul && exit /b 0

echo error: need Python on PATH ^(py -3, python, or python3^) to align libmbCan.so >&2
exit /b 1
