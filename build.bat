@echo off
REM Baut das Framework als dist\objektviewer.jar und kompiliert die Beispielanwendung. Java 17 oder neuer.
cd /d "%~dp0"
if exist out rmdir /s /q out
if exist dist rmdir /s /q dist
mkdir out\framework out\beispiel dist

dir /s /b framework\src\*.java > out\framework-sources.txt
javac -encoding UTF-8 --release 17 -d out\framework @out\framework-sources.txt
if errorlevel 1 exit /b 1
xcopy /e /i /q /y framework\resources out\framework > nul
jar --create --file dist\objektviewer.jar --main-class de.objektbaum.Init -C out\framework .
if errorlevel 1 exit /b 1

dir /s /b beispiel\src\*.java > out\beispiel-sources.txt
javac -encoding UTF-8 --release 17 -cp dist\objektviewer.jar -d out\beispiel @out\beispiel-sources.txt
if errorlevel 1 exit /b 1
echo Gebaut: dist\objektviewer.jar und Beispielanwendung (out\beispiel)
