@echo off
REM Kompiliert und startet die Swing-Version (eigenes Fenster, kein Webserver). Java 17 oder neuer.
cd /d "%~dp0"
if exist out rmdir /s /q out
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -d out @sources.txt
if errorlevel 1 exit /b 1
del sources.txt
start "" javaw -cp out de.objektbaum.MainSwing %*
