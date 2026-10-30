@echo off
setlocal
cd /d "%~dp0"
if not exist "lib\sqlite-jdbc-3.50.3.0.jar" (
  echo ERROR: SQLite JDBC driver is missing from the lib folder.
  pause
  exit /b 1
)
if not exist "out" mkdir out

echo Compiling Java source...
javac -cp ".;lib\sqlite-jdbc-3.50.3.0.jar" -d out src\Main.java
if errorlevel 1 (
  echo.
  echo Compilation failed. Check the error above.
  pause
  exit /b 1
)
echo.
echo Starting LifeLink Organ Donation Website...
echo Open http://localhost:8080 in your browser.
echo Keep this window open while using the website.
echo Press Ctrl+C to stop the server.
java -cp ".;out;lib\sqlite-jdbc-3.50.3.0.jar" Main
pause
