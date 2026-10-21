@echo off
REM Baut alles und startet die Beispielanwendung als Swing-Fenster (kein Webserver).
cd /d "%~dp0"
call build.bat > nul
if errorlevel 1 (call build.bat & exit /b 1)
start "" javaw -cp "dist\objektviewer.jar;out\beispiel" de.objektbaum.beispiel.BeispielSwing %*
