@echo off
chcp 65001 >nul 2>&1
echo ============================================
echo   AKShare Bridge Server - 启动脚本
echo ============================================
echo.

cd /d "%~dp0"

REM 检查 Python 是否可用
set "PYTHON_CMD="
for %%c in (python python3 py) do (
    where %%c >nul 2>&1 && (
        set "PYTHON_CMD=%%c"
        goto found_python
    )
)

echo [错误] 未找到 Python，请先安装 Python 3.8+
echo        下载地址: https://www.python.org/downloads/
pause
exit /b 1

:found_python
echo [信息] 使用 Python: %PYTHON_CMD%

REM 检查依赖是否已安装
%PYTHON_CMD% -c "import flask; import akshare; from packaging.version import Version; import sys; sys.exit(0 if Version(akshare.__version__) >= Version('1.18.94') else 1)" >nul 2>&1
if errorlevel 1 (
    echo [信息] 正在安装/升级依赖包...
    %PYTHON_CMD% -m pip install -r requirements.txt -U -q
    if errorlevel 1 (
        echo [错误] 依赖安装失败，请手动执行:
        echo        %PYTHON_CMD% -m pip install flask akshare pandas
        pause
        exit /b 1
    )
    echo [信息] 依赖安装完成
)

echo [信息] 启动 AKShare Bridge Server (端口 5555)...
echo [信息] 服务地址: http://127.0.0.1:5555
echo [信息] 健康检查: http://127.0.0.1:5555/health
echo.
%PYTHON_CMD% akshare_server.py
pause
