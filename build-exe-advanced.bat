@echo off
chcp 65001 >nul
echo ========================================
echo 高级打包方案 - 使用 jpackage
echo ========================================
echo.

REM 设置变量
set APP_NAME=LabelPrinter
set APP_VERSION=1.0.0
set MAIN_CLASS=com.label.MainApp
set MAIN_JAR=label-html2pdf-1.0.0.jar
set VENDOR=LabelPrinter
set COPYRIGHT=Copyright 2024

REM 清理旧的构建
echo [1/5] 清理旧的构建文件...
if exist dist rmdir /s /q dist
if exist target\jpackage rmdir /s /q target\jpackage

REM 使用 Maven 构建项目
echo.
echo [2/5] 使用 Maven 构建项目...
call mvn clean package
if errorlevel 1 (
    echo Maven 构建失败！
    pause
    exit /b 1
)

REM 创建临时目录结构
echo.
echo [3/5] 准备打包文件...
mkdir target\jpackage\lib

REM 复制主 JAR
copy target\%MAIN_JAR% target\jpackage\lib\

REM 复制所有依赖到 lib 目录
echo 复制依赖库...
xcopy /Y target\lib\*.jar target\jpackage\lib\ 2>nul

REM 从 Maven 仓库复制 JavaFX 依赖
for %%f in (%USERPROFILE%\.m2\repository\org\openjfx\javafx-base\21.0.2\*.jar) do copy "%%f" target\jpackage\lib\
for %%f in (%USERPROFILE%\.m2\repository\org\openjfx\javafx-controls\21.0.2\*.jar) do copy "%%f" target\jpackage\lib\
for %%f in (%USERPROFILE%\.m2\repository\org\openjfx\javafx-fxml\21.0.2\*.jar) do copy "%%f" target\jpackage\lib\
for %%f in (%USERPROFILE%\.m2\repository\org\openjfx\javafx-graphics\21.0.2\*.jar) do copy "%%f" target\jpackage\lib\

REM 使用 jpackage 创建应用程序包
echo.
echo [4/5] 使用 jpackage 创建独立应用...
echo 这可能需要几分钟时间...

jpackage ^
  --type app-image ^
  --name "%APP_NAME%" ^
  --app-version %APP_VERSION% ^
  --vendor "%VENDOR%" ^
  --copyright "%COPYRIGHT%" ^
  --input target\jpackage\lib ^
  --main-jar %MAIN_JAR% ^
  --main-class %MAIN_CLASS% ^
  --java-options "--add-modules javafx.controls,javafx.fxml" ^
  --dest dist

if errorlevel 1 (
    echo jpackage 打包失败！
    echo.
    echo 可能的原因：
    echo 1. 你的 JDK 版本不支持 jpackage（需要 JDK 14+）
    echo 2. 缺少 WiX Toolset（如果要创建 .exe 安装程序）
    echo.
    echo 请使用 build-exe.bat 脚本作为替代方案
    pause
    exit /b 1
)

echo.
echo [5/5] 打包完成！
echo.
echo ========================================
echo 构建成功！
echo ========================================
echo.
echo 输出目录: dist\%APP_NAME%
echo 启动文件: dist\%APP_NAME%\%APP_NAME%.exe
echo.
echo 你可以将整个 dist\%APP_NAME% 文件夹复制到任何 Windows 电脑上运行
echo 无需安装 Java 环境！
echo.

REM 显示目录大小
echo 正在计算打包大小...
powershell -command "'{0:N2} MB' -f ((Get-ChildItem -Path dist\%APP_NAME% -Recurse | Measure-Object -Property Length -Sum).Sum / 1MB)"
echo.

pause
