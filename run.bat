@echo off
REM Kompiliert und startet den Objekt-Viewer (Java 17 oder neuer, keine Abhaengigkeiten)
cd /d "%~dp0"
if exist out rmdir /s /q out
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -d out @sources.txt
if errorlevel 1 exit /b 1
del sources.txt
java -cp out de.objektbaum.Main %*
