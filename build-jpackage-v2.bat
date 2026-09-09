@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo  jpackage 构建独立可执行程序 (无需Java环境)
echo ========================================
echo.

REM ============================================
REM 配置变量
REM ============================================
set APP_NAME=LabelPrinter
set APP_VERSION=1.0.0
set MAIN_CLASS=com.label.MainApp
set MAIN_JAR=label-html2pdf-1.0.0.jar
set INPUT_DIR=jpackage-input
set OUTPUT_DIR=dist-jpackage
set VENDOR=XZM
set DESCRIPTION=快递面单 HTML 转 PDF 工具
set JAVAFX_VERSION=21.0.2

REM ============================================
REM [1/6] 检查 Java 环境
REM ============================================
echo [1/6] 检查 Java 环境...
for /f "tokens=1-3 delims=." %%a in ('java -version 2^>^&1 ^| findstr /R "[0-9]\+\.[0-9]\+\.[0-9]\+"') do (
    set JAVA_MAJOR=%%a
)
if !JAVA_MAJOR! LSS 17 (
    echo 错误：需要 Java 17 或更高版本！当前主版本: !JAVA_MAJOR!
    java -version
    pause
    exit /b 1
)
echo   Java 版本符合要求 (主版本: !JAVA_MAJOR!)

REM ============================================
REM [2/6] 获取 Maven 本地仓库路径
REM ============================================
echo.
echo [2/6] 检测 Maven 本地仓库...
for /f "delims=" %%i in ('mvn help:evaluate -Dexpression=settings.localRepository -q -DforceStdout 2^>nul') do set MVN_REPO=%%i
if "!MVN_REPO!"=="" (
    echo 错误：无法获取 Maven 本地仓库路径！
    pause
    exit /b 1
)
echo   Maven 仓库: !MVN_REPO!

REM ============================================
REM [3/6] Maven 构建项目
REM ============================================
echo.
echo [3/6] Maven 构建项目...
call mvn clean package -DskipTests
if errorlevel 1 (
    echo Maven 构建失败！
    pause
    exit /b 1
)
echo   Maven 构建完成

REM ============================================
REM [4/6] 准备 jpackage 输入文件
REM ============================================
echo.
echo [4/6] 准备 jpackage 输入文件...

REM 清理旧文件
if exist !INPUT_DIR! rmdir /s /q !INPUT_DIR!
if exist !OUTPUT_DIR! rmdir /s /q !OUTPUT_DIR!
mkdir !INPUT_DIR!

REM 复制主 JAR
if not exist "target\!MAIN_JAR!" (
    echo 错误：找不到 target\!MAIN_JAR!
    pause
    exit /b 1
)
copy "target\!MAIN_JAR!" "!INPUT_DIR!\" >nul
echo   已复制主 JAR: !MAIN_JAR!

REM ============================================
REM [5/6] 定位 JavaFX 模块 JAR
REM ============================================
echo.
echo [5/6] 定位 JavaFX 模块...

set JAVAFX_BASE=!MVN_REPO!\org\openjfx\javafx-base\!JAVAFX_VERSION!\javafx-base-!JAVAFX_VERSION!.jar
set JAVAFX_BASE_WIN=!MVN_REPO!\org\openjfx\javafx-base\!JAVAFX_VERSION!\javafx-base-!JAVAFX_VERSION!-win.jar
set JAVAFX_CONTROLS=!MVN_REPO!\org\openjfx\javafx-controls\!JAVAFX_VERSION!\javafx-controls-!JAVAFX_VERSION!.jar
set JAVAFX_CONTROLS_WIN=!MVN_REPO!\org\openjfx\javafx-controls\!JAVAFX_VERSION!\javafx-controls-!JAVAFX_VERSION!-win.jar
set JAVAFX_FXML=!MVN_REPO!\org\openjfx\javafx-fxml\!JAVAFX_VERSION!\javafx-fxml-!JAVAFX_VERSION!.jar
set JAVAFX_FXML_WIN=!MVN_REPO!\org\openjfx\javafx-fxml\!JAVAFX_VERSION!\javafx-fxml-!JAVAFX_VERSION!-win.jar
set JAVAFX_GRAPHICS=!MVN_REPO!\org\openjfx\javafx-graphics\!JAVAFX_VERSION!\javafx-graphics-!JAVAFX_VERSION!.jar
set JAVAFX_GRAPHICS_WIN=!MVN_REPO!\org\openjfx\javafx-graphics\!JAVAFX_VERSION!\javafx-graphics-!JAVAFX_VERSION!-win.jar

REM 验证所有 JavaFX JAR 都存在
for %%f in ("!JAVAFX_BASE!" "!JAVAFX_BASE_WIN!" "!JAVAFX_CONTROLS!" "!JAVAFX_CONTROLS_WIN!" "!JAVAFX_FXML!" "!JAVAFX_FXML_WIN!" "!JAVAFX_GRAPHICS!" "!JAVAFX_GRAPHICS_WIN!") do (
    if not exist %%f (
        echo 错误：找不到 JavaFX 模块: %%f
        echo 请运行 mvn dependency:resolve 下载依赖
        pause
        exit /b 1
    )
)

REM 构建模块路径（Windows 用分号分隔）
set MODULE_PATH=!JAVAFX_BASE!;!JAVAFX_CONTROLS!;!JAVAFX_FXML!;!JAVAFX_GRAPHICS!;!JAVAFX_BASE_WIN!;!JAVAFX_CONTROLS_WIN!;!JAVAFX_FXML_WIN!;!JAVAFX_GRAPHICS_WIN!

echo   已找到所有 JavaFX !JAVAFX_VERSION! 模块

REM ============================================
REM [6/6] jpackage 打包
REM ============================================
echo.
echo [6/6] jpackage 打包应用程序（含运行时 + JavaFX）...
echo   这可能需要 2-5 分钟，请耐心等待...
echo.

jpackage ^
    --type app-image ^
    --name "!APP_NAME!" ^
    --app-version !APP_VERSION! ^
    --vendor "!VENDOR!" ^
    --description "!DESCRIPTION!" ^
    --input "!INPUT_DIR!" ^
    --dest "!OUTPUT_DIR!" ^
    --main-jar !MAIN_JAR! ^
    --main-class !MAIN_CLASS! ^
    --module-path "!MODULE_PATH!" ^
    --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base,java.net.http,jdk.crypto.ec ^
    --java-options "--add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED" ^
    --win-console

if errorlevel 1 (
    echo.
    echo jpackage 打包失败！
    echo.
    echo 可能的原因：
    echo 1. JavaFX 模块路径不正确
    echo 2. 磁盘空间不足
    echo 3. jpackage 版本不支持当前配置
    echo.
    pause
    exit /b 1
)

REM ============================================
REM 完成
REM ============================================
echo.
echo ========================================
echo  构建完成！
echo ========================================
echo.
echo 输出目录: !OUTPUT_DIR!\!APP_NAME!
echo 启动程序: !OUTPUT_DIR!\!APP_NAME!\!APP_NAME!.exe
echo.
echo 将整个 !APP_NAME! 文件夹复制到任何 Windows 电脑即可运行
echo 无需安装 Java 环境，无需安装 JavaFX！
echo.

REM 显示打包大小
if exist "!OUTPUT_DIR!\!APP_NAME!" (
    echo 正在计算打包大小...
    powershell -command "$size = (Get-ChildItem -Path '!OUTPUT_DIR!\!APP_NAME!' -Recurse | Measure-Object -Property Length -Sum).Sum; Write-Host ('总大小: {0:N2} MB' -f ($size / 1MB))"
    echo.
)

REM 创建 ZIP 压缩包便于分发
echo 正在创建 ZIP 压缩包...
powershell -command "Compress-Archive -Path '!OUTPUT_DIR!\!APP_NAME!' -DestinationPath '!OUTPUT_DIR!\!APP_NAME!-!APP_VERSION!-windows.zip' -Force"
if exist "!OUTPUT_DIR!\!APP_NAME!-!APP_VERSION!-windows.zip" (
    powershell -command "$size = (Get-Item '!OUTPUT_DIR!\!APP_NAME!-!APP_VERSION!-windows.zip').Length; Write-Host ('ZIP 大小: {0:N2} MB' -f ($size / 1MB))"
)
echo.

REM 打开输出目录
explorer !OUTPUT_DIR!

echo 按任意键退出...
pause >nul
