@echo off
chcp 65001 >nul 2>&1
title Keyword Stock Stats - 一键启动
echo ============================================
echo   Keyword Stock Stats - 一键启动
echo   基于 AKShare 实盘数据
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

REM ---- 确定 Maven 命令 (优先使用项目自带 mvnw.cmd) ----
set "MVN_CMD="
if exist "%~dp0mvnw.cmd" (
    set "MVN_CMD=call mvnw.cmd"
    echo [信息] 使用项目自带 Maven Wrapper (mvnw.cmd)
) else (
    where mvn >nul 2>&1 && (
        set "MVN_CMD=mvn"
        echo [信息] 使用系统全局 Maven
    )
)
if not defined MVN_CMD (
    if exist "F:\apache-maven-3.9.13\bin\mvn.cmd" (
        set "MAVEN_HOME=F:\apache-maven-3.9.13"
        set "MVN_CMD=F:\apache-maven-3.9.13\bin\mvn"
        set "PATH=%MAVEN_HOME%\bin;%PATH%"
        echo [信息] 使用本地 Maven: F:\apache-maven-3.9.13
    )
)

REM ---- 确定启动命令 ----
set "RUN_CMD="
if defined MVN_CMD (
    set "RUN_CMD=%MVN_CMD% spring-boot:run"
)
if not defined RUN_CMD (
    if exist "target\keyword-stock-stats-0.1.0.jar" (
        set "RUN_CMD=%JAVA_CMD% -jar target\keyword-stock-stats-0.1.0.jar"
        echo [信息] Maven 不可用，使用已构建的 JAR 启动
    )
)
if not defined RUN_CMD (
    echo [错误] 未找到 Maven 且未构建 jar
    echo [提示] 请先运行 mvnw.cmd clean package -DskipTests 构建 JAR
    pause
    exit /b 1
)

REM ---- 启动 Python AKShare 桥接服务 ----
echo [步骤 1/2] 启动 Python AKShare 桥接服务...
start "AKShare Bridge" cmd /c "cd /d "%~dp0python-bridge" && start_server.bat"
echo [信息] 等待 Python 桥接服务初始化...
timeout /t 5 /nobreak >nul

REM ---- 启动 Java Spring Boot ----
echo [步骤 2/2] 启动 Java Spring Boot 应用...
echo.
echo ============================================
echo   服务地址: http://localhost:8080
echo   数据状态: http://localhost:8080/api/status
echo   概念热度: http://localhost:8080/api/concept/top
echo   关键词统计: http://localhost:8080/api/keyword/stats?keywords=新能源,华为
echo   历史趋势: http://localhost:8080/api/trend?keyword=新能源
echo ============================================
echo.

%RUN_CMD%
pause
