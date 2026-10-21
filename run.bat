@echo off
REM Baut alles und startet die Beispielanwendung im Browser-Modus (Parameter wie run.sh).
cd /d "%~dp0"
call build.bat > nul
if errorlevel 1 (call build.bat & exit /b 1)
java -cp "dist\objektviewer.jar;out\beispiel" de.objektbaum.beispiel.BeispielApp %*
