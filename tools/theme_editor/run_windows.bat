@echo off
setlocal
cd /d "%~dp0"

where python >nul 2>nul
if errorlevel 1 (
  where py >nul 2>nul
  if errorlevel 1 (
    echo Python не найден. Установите Python 3.10+ с python.org
    echo и отметьте "tcl/tk" / "tcl/tk and IDLE" при установке.
    pause
    exit /b 1
  )
  set PY=py -3
) else (
  set PY=python
)

%PY% -c "import tkinter" 2>nul
if errorlevel 1 (
  echo Модуль tkinter недоступен.
  echo Переустановите Python и включите компонент tcl/tk.
  pause
  exit /b 1
)

%PY% -c "import PIL" 2>nul
if errorlevel 1 (
  echo Опционально: pip install Pillow  ^(превью обоев^)
)

%PY% "%~dp0__main__.py" %*
set EXITCODE=%ERRORLEVEL%
if %EXITCODE% neq 0 pause
exit /b %EXITCODE%
