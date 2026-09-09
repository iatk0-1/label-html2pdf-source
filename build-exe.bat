@echo off
chcp 65001 >nul
echo ========================================
echo 开始构建独立可执行程序
echo ========================================
echo.

REM 设置变量
set APP_NAME=LabelPrinter
set APP_VERSION=1.0.0
set MAIN_CLASS=com.label.MainApp
set MAIN_JAR=label-html2pdf-1.0.0.jar
set OUTPUT_DIR=dist
set RUNTIME_DIR=runtime

REM 清理旧的构建
echo [1/6] 清理旧的构建文件...
if exist %OUTPUT_DIR% rmdir /s /q %OUTPUT_DIR%
if exist %RUNTIME_DIR% rmdir /s /q %RUNTIME_DIR%
if exist target rmdir /s /q target

REM 使用 Maven 构建项目
echo.
echo [2/6] 使用 Maven 构建项目...
call mvn clean package
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

REM 创建输出目录
mkdir %OUTPUT_DIR%
mkdir %OUTPUT_DIR%\lib

REM 复制主 JAR
echo.
echo [3/6] 复制应用程序文件...
copy target\%MAIN_JAR% %OUTPUT_DIR%\lib\

REM 复制 JavaFX 依赖
echo 复制 JavaFX 库...
for %%f in (%USERPROFILE%\.m2\repository\org\openjfx\javafx-*\21.0.2\*.jar) do (
    copy "%%f" %OUTPUT_DIR%\lib\
)

REM 复制其他依赖（非 JavaFX）
echo 复制其他依赖库...
for %%f in (target\lib\*.jar) do (
    copy "%%f" %OUTPUT_DIR%\lib\
)

REM 创建自定义 JRE
echo.
echo [4/6] 创建精简的 JRE 运行时...
jlink --add-modules java.base,java.desktop,java.logging,java.xml,java.naming,java.sql,jdk.unsupported,jdk.crypto.ec ^
      --strip-debug ^
      --no-man-pages ^
      --no-header-files ^
      --compress=2 ^
      --output %RUNTIME_DIR%

if errorlevel 1 (
    echo jlink 创建运行时失败！
    pause
    exit /b 1
)

REM 复制运行时到输出目录
echo 复制运行时到输出目录...
xcopy /E /I /Y %RUNTIME_DIR% %OUTPUT_DIR%\jre

REM 创建启动脚本
echo.
echo [5/6] 创建启动脚本...
(
echo @echo off
echo set JAVA_HOME=%%~dp0jre
echo set PATH=%%JAVA_HOME%%\bin;%%PATH%%
echo.
echo REM 获取所有 lib 目录下的 jar 文件
echo setlocal enabledelayedexpansion
echo set CLASSPATH=
echo for %%%%j in ^(%%~dp0lib\*.jar^) do ^(
echo     if "!CLASSPATH!"=="" ^(
echo         set CLASSPATH=%%%%j
echo     ^) else ^(
echo         set CLASSPATH=!CLASSPATH!;%%%%j
echo     ^)
echo ^)
echo.
echo REM 设置 JavaFX 模块路径
echo set MODULE_PATH=%%~dp0lib\javafx-base-21.0.2.jar;%%~dp0lib\javafx-controls-21.0.2.jar;%%~dp0lib\javafx-fxml-21.0.2.jar;%%~dp0lib\javafx-graphics-21.0.2.jar
echo.
echo REM 启动应用
echo "%%JAVA_HOME%%\bin\java.exe" --module-path "%%MODULE_PATH%%" --add-modules javafx.controls,javafx.fxml -cp "%%CLASSPATH%%" %MAIN_CLASS%
) > %OUTPUT_DIR%\%APP_NAME%.bat

REM 创建 VBS 启动器（隐藏命令行窗口）
echo.
echo [6/6] 创建无窗口启动器...
(
echo Set WshShell = CreateObject^("WScript.Shell"^)
echo WshShell.Run chr^(34^) ^& WScript.ScriptFullName ^& "\..\%APP_NAME%.bat" ^& Chr^(34^), 0
echo Set WshShell = Nothing
) > %OUTPUT_DIR%\%APP_NAME%.vbs

echo.
echo ========================================
echo 构建完成！
echo ========================================
echo.
echo 输出目录: %OUTPUT_DIR%
echo 启动文件: %APP_NAME%.vbs ^(无窗口^) 或 %APP_NAME%.bat ^(有窗口^)
echo.
echo 你可以将整个 %OUTPUT_DIR% 文件夹复制到任何 Windows 电脑上运行
echo 无需安装 Java 环境！
echo.

REM 显示目录大小
echo 正在计算打包大小...
powershell -command "'{0:N2} MB' -f ((Get-ChildItem -Path %OUTPUT_DIR% -Recurse | Measure-Object -Property Length -Sum).Sum / 1MB)"
echo.

pause
