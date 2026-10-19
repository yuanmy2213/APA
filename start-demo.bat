@echo off
cd /d "%~dp0"
set "REVIEW_JAR=finance-access-review-1.1.0.jar"
if not exist "%REVIEW_JAR%" set "REVIEW_JAR=target\finance-access-review-1.1.0.jar"
if not exist "%REVIEW_JAR%" (
  echo Please run mvn package or use the release package containing the JAR.
  pause
  exit /b 1
)
echo Open http://127.0.0.1:8080 after startup.
java -Xms128m -Xmx1g -jar "%REVIEW_JAR%" --spring.profiles.active=demo %*
pause
