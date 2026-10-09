@echo off
setlocal
echo ========================================================
echo   Building Maito Modular Monolith (Adoptium JDK 21)
echo ========================================================
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo [INFO] Running: mvnw.cmd clean compile
call mvnw.cmd clean compile
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================================
    echo   BUILD SUCCESSFUL!
    echo ========================================================
) else (
    echo.
    echo [ERROR] Build failed with exit code %ERRORLEVEL%
)
endlocal
