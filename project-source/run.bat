@echo off
setlocal
echo ========================================================
echo   Starting Maito Modular Monolith (Spring Boot 3.3.4)
echo ========================================================
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"

for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080" ^| findstr "LISTENING"') do (
    echo [INFO] Freeing port 8080 occupied by PID %%a...
    taskkill /F /PID %%a >nul 2>&1
)

echo [INFO] Running: mvnw.cmd spring-boot:run
call mvnw.cmd spring-boot:run
endlocal
