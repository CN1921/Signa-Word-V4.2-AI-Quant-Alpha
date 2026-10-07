@echo off
chcp 65001 >nul 2>&1
title Keyword Stock Stats - 构建打包
echo ============================================
echo   Keyword Stock Stats - 构建打包
echo ============================================
echo.

cd /d "%~dp0"

REM ---- 检测 Java ----
set "JAVA_CMD=java"
where java >nul 2>&1 || (
    if exist "C:\Program Files\Java\jdk1.8.0_291\bin\java.exe" (
        set "JAVA_HOME=C:\Program Files\Java\jdk1.8.0_291"
        set "JAVA_CMD=C:\Program Files\Java\jdk1.8.0_291\bin\java"
        set "PATH=%JAVA_HOME%\bin;%PATH%"
    ) else (
        echo [错误] 未找到 Java，请安装 JDK 8+
        pause
        exit /b 1
    )
)

REM ---- 确定 Maven 命令 (优先使用 mvnw.cmd) ----
set "MVN_CMD="
if exist "%~dp0mvnw.cmd" (
    set "MVN_CMD=call mvnw.cmd"
    echo [信息] 使用 Maven Wrapper (mvnw.cmd)
) else (
    where mvn >nul 2>&1 && set "MVN_CMD=mvn"
)
if not defined MVN_CMD (
    if exist "F:\apache-maven-3.9.13\bin\mvn.cmd" (
        set "MAVEN_HOME=F:\apache-maven-3.9.13"
        set "MVN_CMD=F:\apache-maven-3.9.13\bin\mvn"
        set "PATH=%MAVEN_HOME%\bin;%PATH%"
    )
)
if not defined MVN_CMD (
    echo [错误] 未找到 Maven 或 mvnw.cmd
    pause
    exit /b 1
)

echo [步骤] 执行 mvn clean package -DskipTests
echo.

%MVN_CMD% clean package -DskipTests

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ============================================
    echo   构建成功!
    echo   JAR: target\keyword-stock-stats-0.1.0.jar
    echo   运行: start.bat
    echo ============================================
) else (
    echo.
    echo [错误] 构建失败，请检查错误信息
)

pause
