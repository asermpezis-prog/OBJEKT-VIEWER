@echo off
REM Baut alles und startet das Beispiel "Vorgang suchen" (Anmeldung demo/demo, VorgangsNummer 100000001).
cd /d "%~dp0"
call build.bat > nul
if errorlevel 1 (call build.bat & exit /b 1)
start "" javaw -cp "dist\objektviewer.jar;out\beispiel" de.objektbaum.beispiel.BeispielVorgang %*
