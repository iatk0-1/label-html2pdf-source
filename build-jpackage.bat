@echo off
chcp 65001 >nul
echo ========================================
echo 使用 jpackage 构建独立可执行程序
echo ========================================
echo.

REM 设置变量
set APP_NAME=LabelPrinter
set APP_VERSION=1.0.0
set MAIN_CLASS=com.label.MainApp
set MAIN_JAR=label-html2pdf-1.0.0.jar
set INPUT_DIR=jpackage-input
set OUTPUT_DIR=dist-jpackage
set VENDOR=XZM
set DESCRIPTION=快递面单 HTML 转 PDF 工具

REM 检查 Java 版本
echo [1/7] 检查 Java 环境...
java -version 2>&1 | findstr /C:"17" >nul
if errorlevel 1 (
    echo 错误：需要 Java 17 或更高版本！
    echo 当前 Java 版本：
    java -version
    pause
    exit /b 1
)

REM 清理旧的构建
echo.
echo [2/7] 清理旧的构建文件...
if exist %INPUT_DIR% rmdir /s /q %INPUT_DIR%
if exist %OUTPUT_DIR% rmdir /s /q %OUTPUT_DIR%

REM 使用 Maven 构建项目
echo.
echo [3/7] 使用 Maven 构建项目...
call mvn clean package -DskipTests
if errorlevel 1 (
    echo Maven 构建失败！
    pause
    exit /b 1
)

REM 检查 JAR 文件是否存在
if not exist "target\%MAIN_JAR%" (
    echo 错误：找不到 target\%MAIN_JAR%
    pause
    exit /b 1
)

REM 创建 jpackage 输入目录
echo.
echo [4/7] 准备 jpackage 输入文件...
mkdir %INPUT_DIR%

REM 复制主 JAR
copy target\%MAIN_JAR% %INPUT_DIR%\

REM 获取 JavaFX 模块路径
echo.
echo [5/7] 查找 JavaFX 模块...
set JAVAFX_MODS=
for /f "delims=" %%i in ('dir /b /s "%USERPROFILE%\.m2\repository\org\openjfx\javafx-controls\21.0.2\javafx-controls-21.0.2-win.jar" 2^>nul') do set JAVAFX_CONTROLS=%%i
for /f "delims=" %%i in ('dir /b /s "%USERPROFILE%\.m2\repository\org\openjfx\javafx-graphics\21.0.2\javafx-graphics-21.0.2-win.jar" 2^>nul') do set JAVAFX_GRAPHICS=%%i
for /f "delims=" %%i in ('dir /b /s "%USERPROFILE%\.m2\repository\org\openjfx\javafx-base\21.0.2\javafx-base-21.0.2-win.jar" 2^>nul') do set JAVAFX_BASE=%%i
for /f "delims=" %%i in ('dir /b /s "%USERPROFILE%\.m2\repository\org\openjfx\javafx-fxml\21.0.2\javafx-fxml-21.0.2-win.jar" 2^>nul') do set JAVAFX_FXML=%%i

if "%JAVAFX_CONTROLS%"=="" (
    echo 错误：找不到 JavaFX 模块！
    echo 请确保已通过 Maven 下载了 JavaFX 依赖。
    pause
    exit /b 1
)

REM 构建模块路径
set JAVAFX_MODS=%JAVAFX_CONTROLS%;%JAVAFX_GRAPHICS%;%JAVAFX_BASE%;%JAVAFX_FXML%

echo JavaFX 模块路径：
echo %JAVAFX_MODS%

REM 创建启动器配置文件
echo.
echo [6/7] 创建启动器配置...
mkdir launcher 2>nul
(
echo module-path=%JAVAFX_MODS%
echo add-modules=javafx.controls,javafx.fxml,javafx.graphics,javafx.base
) > launcher\launcher.properties

REM 使用 jpackage 打包
echo.
echo [7/7] 使用 jpackage 打包应用程序...
echo 这可能需要几分钟时间，请耐心等待...
echo.

jpackage ^
    --type exe ^
    --name "%APP_NAME%" ^
    --app-version %APP_VERSION% ^
    --vendor "%VENDOR%" ^
    --description "%DESCRIPTION%" ^
    --input %INPUT_DIR% ^
    --dest %OUTPUT_DIR% ^
    --main-jar %MAIN_JAR% ^
    --main-class %MAIN_CLASS% ^
    --module-path "%JAVAFX_MODS%" ^
    --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base ^
    --win-console ^
    --win-dir-chooser ^
    --win-menu ^
    --win-shortcut

if errorlevel 1 (
    echo.
    echo jpackage 打包失败！
    echo.
    echo 可能的原因：
    echo 1. 未安装 WiX Toolset（用于创建 Windows 安装程序）
    echo    下载地址：https://wixtoolset.org/
    echo 2. Java 版本不支持 jpackage（需要 Java 14+）
    echo 3. JavaFX 模块路径不正确
    echo.
    pause
    exit /b 1
)

echo.
echo ========================================
echo 构建完成！
echo ========================================
echo.
echo 输出目录: %OUTPUT_DIR%
echo 安装程序: %OUTPUT_DIR%\%APP_NAME%-%APP_VERSION%.exe
echo.
echo 你可以运行安装程序将应用安装到任何 Windows 电脑上
echo 无需安装 Java 环境！
echo.

REM 显示文件大小
if exist "%OUTPUT_DIR%\%APP_NAME%-%APP_VERSION%.exe" (
    echo 安装程序大小：
    powershell -command "'{0:N2} MB' -f ((Get-Item '%OUTPUT_DIR%\%APP_NAME%-%APP_VERSION%.exe').Length / 1MB)"
    echo.
)

REM 打开输出目录
explorer %OUTPUT_DIR%

pause
