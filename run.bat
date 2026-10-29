@echo off
cd /d "%~dp0"
if not exist out mkdir out
echo Compiling QuizApp...
javac -d out src\*.java
if errorlevel 1 (
  echo.
  echo Compilation failed. Check that JDK 17 or newer is installed and javac works.
  pause
  exit /b 1
)
echo.
echo Starting QuizApp web UI...
echo Open http://localhost:8081 in your browser.
echo Keep this window open while using QuizApp.
java -cp out ApiServer
pause
