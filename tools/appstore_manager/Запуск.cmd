@echo off
setlocal
cd /d "%~dp0"

where py >nul 2>&1
if %ERRORLEVEL%==0 (
  py -3 appstore_manager.py
  goto :eof
)

where python >nul 2>&1
if %ERRORLEVEL%==0 (
  python appstore_manager.py
  goto :eof
)

echo Python не найден. Установите Python 3 с python.org (с галочкой tcl/tk)
echo и снова запустите Запуск.cmd
pause
endlocal
