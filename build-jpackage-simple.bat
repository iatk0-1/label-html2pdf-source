@echo off
chcp 65001 >nul
echo ========================================
echo 使用 jpackage 构建独立应用程序（app-image）
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
echo [1/6] 检查 Java 环境...
java -version 2>&1 | findstr /C:"17" >nul
if errorlevel 1 (
    java -version 2>&1 | findstr /C:"21" >nul
    if errorlevel 1 (
        echo 警告：推荐使用 Java 17 或更高版本
        echo 当前 Java 版本：
        java -version
        echo.
        echo 继续构建...
        echo.
    )
)

REM 清理旧的构建
echo.
echo [2/6] 清理旧的构建文件...
if exist %INPUT_DIR% rmdir /s /q %INPUT_DIR%
if exist %OUTPUT_DIR% rmdir /s /q %OUTPUT_DIR%

REM 使用 Maven 构建项目
echo.
echo [3/6] 使用 Maven 构建项目...
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
echo [4/6] 准备 jpackage 输入文件...
mkdir %INPUT_DIR%

REM 复制主 JAR
copy target\%MAIN_JAR% %INPUT_DIR%\

REM 获取 JavaFX 模块路径
echo.
echo [5/6] 查找 JavaFX 模块...
set JAVAFX_PATH=%USERPROFILE%\.m2\repository\org\openjfx

REM 查找 JavaFX JAR 文件
set JAVAFX_CONTROLS=
set JAVAFX_GRAPHICS=
set JAVAFX_BASE=
set JAVAFX_FXML=

for /f "delims=" %%i in ('dir /b /s "%JAVAFX_PATH%\javafx-controls\21.0.2\*-win.jar" 2^>nul') do set JAVAFX_CONTROLS=%%i
for /f "delims=" %%i in ('dir /b /s "%JAVAFX_PATH%\javafx-graphics\21.0.2\*-win.jar" 2^>nul') do set JAVAFX_GRAPHICS=%%i
for /f "delims=" %%i in ('dir /b /s "%JAVAFX_PATH%\javafx-base\21.0.2\*-win.jar" 2^>nul') do set JAVAFX_BASE=%%i
for /f "delims=" %%i in ('dir /b /s "%JAVAFX_PATH%\javafx-fxml\21.0.2\*-win.jar" 2^>nul') do set JAVAFX_FXML=%%i

if "%JAVAFX_CONTROLS%"=="" (
    echo 错误：找不到 JavaFX 模块！
    echo 请确保已通过 Maven 下载了 JavaFX 依赖。
    echo.
    echo 尝试运行：mvn dependency:resolve
    pause
    exit /b 1
)

REM 构建模块路径
set MODULE_PATH=%JAVAFX_CONTROLS%;%JAVAFX_GRAPHICS%;%JAVAFX_BASE%;%JAVAFX_FXML%

echo 找到 JavaFX 模块：
echo   - javafx-controls
echo   - javafx-graphics
echo   - javafx-base
echo   - javafx-fxml
echo.

REM 使用 jpackage 打包（app-image 类型，不需要 WiX）
echo.
echo [6/6] 使用 jpackage 打包应用程序...
echo 这可能需要几分钟时间，请耐心等待...
echo.

jpackage ^
    --type app-image ^
    --name "%APP_NAME%" ^
    --app-version %APP_VERSION% ^
    --vendor "%VENDOR%" ^
    --description "%DESCRIPTION%" ^
    --input %INPUT_DIR% ^
    --dest %OUTPUT_DIR% ^
    --main-jar %MAIN_JAR% ^
    --main-class %MAIN_CLASS% ^
    --module-path "%MODULE_PATH%" ^
    --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base ^
    --java-options "--add-opens javafx.graphics/javafx.scene=ALL-UNNAMED" ^
    --win-console

if errorlevel 1 (
    echo.
    echo jpackage 打包失败！
    echo.
    echo 可能的原因：
    echo 1. Java 版本不支持 jpackage（需要 Java 14+）
    echo 2. JavaFX 模块路径不正确
    echo 3. 缺少必要的系统工具
    echo.
    pause
    exit /b 1
)

echo.
echo ========================================
echo 构建完成！
echo ========================================
echo.
echo 输出目录: %OUTPUT_DIR%\%APP_NAME%
echo 启动文件: %OUTPUT_DIR%\%APP_NAME%\%APP_NAME%.exe
echo.
echo 你可以将整个 %APP_NAME% 文件夹复制到任何 Windows 电脑上运行
echo 无需安装 Java 环境！
echo.

REM 显示目录大小
if exist "%OUTPUT_DIR%\%APP_NAME%" (
    echo 正在计算打包大小...
    powershell -command "'{0:N2} MB' -f ((Get-ChildItem -Path '%OUTPUT_DIR%\%APP_NAME%' -Recurse | Measure-Object -Property Length -Sum).Sum / 1MB)"
    echo.
)

REM 创建压缩包
echo 是否创建 ZIP 压缩包？(Y/N)
set /p CREATE_ZIP=
if /i "%CREATE_ZIP%"=="Y" (
    echo.
    echo 正在创建压缩包...
    powershell -command "Compress-Archive -Path '%OUTPUT_DIR%\%APP_NAME%' -DestinationPath '%OUTPUT_DIR%\%APP_NAME%-%APP_VERSION%-windows.zip' -Force"
    if exist "%OUTPUT_DIR%\%APP_NAME%-%APP_VERSION%-windows.zip" (
        echo 压缩包已创建：%OUTPUT_DIR%\%APP_NAME%-%APP_VERSION%-windows.zip
        powershell -command "'{0:N2} MB' -f ((Get-Item '%OUTPUT_DIR%\%APP_NAME%-%APP_VERSION%-windows.zip').Length / 1MB)"
    )
)

echo.
REM 打开输出目录
explorer %OUTPUT_DIR%

pause
