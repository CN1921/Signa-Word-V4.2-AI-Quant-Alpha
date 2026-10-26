@echo off
setlocal
chcp 65001 >nul 2>&1
title Keyword Stock Stats - Launcher

cd /d "%~dp0"

echo ============================================
echo   Keyword Stock Stats - Launcher
echo ============================================
echo.

REM Check Java
where java >nul 2>&1
if errorlevel 1 (
    if exist "C:\Program Files\Java\jdk1.8.0_291\bin\java.exe" (
        set "JAVA_HOME=C:\Program Files\Java\jdk1.8.0_291"
        set "PATH=C:\Program Files\Java\jdk1.8.0_291\bin;%PATH%"
    ) else (
        echo [ERROR] Java was not found. Install JDK 8 or newer and configure PATH.
        pause
        exit /b 1
    )
)

REM Select Maven command: project wrapper, global Maven, or known local install
set "MVN_MODE="
if exist "%~dp0mvnw.cmd" (
    set "MVN_MODE=wrapper"
) else (
    where mvn >nul 2>&1
    if not errorlevel 1 set "MVN_MODE=global"
)
if not defined MVN_MODE if exist "F:\apache-maven-3.9.13\bin\mvn.cmd" set "MVN_MODE=local"

REM Start Python AKShare bridge when available
if exist "%~dp0python-bridge\start_server.bat" (
    echo [STEP 1/2] Starting Python AKShare bridge...
    start "AKShare Bridge" cmd /k "cd /d ""%~dp0python-bridge"" && call start_server.bat"
    echo Waiting for the bridge to initialize...
    timeout /t 5 /nobreak >nul
) else (
    echo [WARNING] python-bridge\start_server.bat was not found. Skipping bridge.
)

echo [STEP 2/2] Starting Spring Boot...
echo.
echo Service:       http://localhost:8080
echo Status:        http://localhost:8080/api/status
echo Concept heat:  http://localhost:8080/api/concept/top
echo.

if "%MVN_MODE%"=="wrapper" (
    call "%~dp0mvnw.cmd" spring-boot:run
) else if "%MVN_MODE%"=="global" (
    call mvn spring-boot:run
) else if "%MVN_MODE%"=="local" (
    call "F:\apache-maven-3.9.13\bin\mvn.cmd" spring-boot:run
) else if exist "%~dp0target\keyword-stock-stats-0.1.0.jar" (
    java -jar "%~dp0target\keyword-stock-stats-0.1.0.jar"
) else (
    echo [ERROR] Maven was not found and no prebuilt JAR was found.
    echo Install Maven, restore mvnw.cmd, or build the project first.
    pause
    exit /b 1
)

pause
endlocal
