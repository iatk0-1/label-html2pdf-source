@echo off
chcp 65001 >nul
echo ========================================
echo 环境检查工具
echo ========================================
echo.

set ERROR_COUNT=0

REM 检查 Java
echo [1/4] 检查 Java 环境...
java -version >nul 2>&1
if errorlevel 1 (
    echo ❌ 未找到 Java 命令
    echo    请安装 JDK 17 或更高版本
    set /a ERROR_COUNT+=1
) else (
    echo ✅ Java 已安装
    java -version 2>&1 | findstr /C:"version"
)
echo.

REM 检查 JAVA_HOME
echo [2/4] 检查 JAVA_HOME 环境变量...
if "%JAVA_HOME%"=="" (
    echo ❌ JAVA_HOME 未设置
    echo    请设置 JAVA_HOME 环境变量
    set /a ERROR_COUNT+=1
) else (
    echo ✅ JAVA_HOME = %JAVA_HOME%
)
echo.

REM 检查 Maven
echo [3/4] 检查 Maven 环境...
mvn -version >nul 2>&1
if errorlevel 1 (
    echo ❌ 未找到 Maven 命令
    echo    请安装 Apache Maven
    set /a ERROR_COUNT+=1
) else (
    echo ✅ Maven 已安装
    mvn -version 2>&1 | findstr /C:"Apache Maven"
)
echo.

REM 检查 jlink
echo [4/4] 检查 jlink 工具...
jlink --version >nul 2>&1
if errorlevel 1 (
    echo ❌ 未找到 jlink 命令
    echo    请确保使用 JDK（而非 JRE）
    set /a ERROR_COUNT+=1
) else (
    echo ✅ jlink 已安装
    jlink --version
)
echo.

REM 总结
echo ========================================
if %ERROR_COUNT%==0 (
    echo ✅ 环境检查通过！
    echo.
    echo 你可以运行以下脚本进行打包：
    echo   - build-exe.bat ^(推荐^)
    echo   - build-exe-advanced.bat ^(需要 JDK 14+^)
) else (
    echo ❌ 发现 %ERROR_COUNT% 个问题
    echo.
    echo 请解决上述问题后再进行打包
)
echo ========================================
echo.

pause
