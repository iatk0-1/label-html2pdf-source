[CmdletBinding()]
param(
    [string]$JdkPath = "$env:USERPROFILE\.jdks\corretto-21.0.9",
    [string]$ReleaseId = (Get-Date -Format 'yyyyMMdd-HHmmss')
)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$appName = '现在买打印软件'
$jarName = 'label-html2pdf-1.0.0.jar'
if ($ReleaseId -notmatch '^[A-Za-z0-9-]+$') { throw '发布编号只能包含字母、数字和横线' }
if (-not (Test-Path -LiteralPath "$JdkPath\bin\jpackage.exe")) { throw "找不到打包 JDK：$JdkPath" }

Push-Location -LiteralPath $projectRoot
$originalJavaHome = $env:JAVA_HOME
$originalPath = $env:Path
try {
    $env:JAVA_HOME = $JdkPath
    $env:Path = "$JdkPath\bin;$originalPath"
    Write-Host '正在编译并执行测试……'
    & mvn package '-Dmaven.jar.forceCreation=true'
    if ($LASTEXITCODE -ne 0) { throw 'Maven 构建或测试失败' }

    $releaseRoot = Join-Path $projectRoot "release\$ReleaseId"
    if (Test-Path -LiteralPath $releaseRoot) { throw "发布目录已经存在，请使用新的发布编号：$releaseRoot" }
    $inputDir = Join-Path $projectRoot "target\release-input-$ReleaseId"
    $moduleDir = Join-Path $projectRoot "target\release-modules-$ReleaseId"
    New-Item -ItemType Directory -Path $inputDir,$moduleDir,$releaseRoot | Out-Null
    Copy-Item -LiteralPath "target\$jarName" -Destination $inputDir
    foreach ($module in @('base','controls','fxml','graphics')) {
        foreach ($classifier in @('', '-win')) {
            $dependency = Join-Path $env:USERPROFILE ".m2\repository\org\openjfx\javafx-$module\21.0.2\javafx-$module-21.0.2$classifier.jar"
            if (-not (Test-Path -LiteralPath $dependency)) { throw "缺少 JavaFX 模块：$dependency" }
            Copy-Item -LiteralPath $dependency -Destination $moduleDir
        }
    }

    Write-Host '正在生成带运行环境的 Windows 软件……'
    & "$JdkPath\bin\jpackage.exe" --type app-image --name $appName --app-version 1.0.0 `
        --vendor '现在买' --description '现在买快递面单打印软件' `
        --input $inputDir --dest $releaseRoot --main-jar $jarName --main-class com.label.MainApp `
        --icon (Join-Path $projectRoot 'packaging\app-icon.ico') --module-path $moduleDir `
        --add-modules 'javafx.controls,javafx.fxml,javafx.graphics,javafx.base,java.net.http,jdk.crypto.ec,java.scripting,java.logging' `
        --java-options '--add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED'
    if ($LASTEXITCODE -ne 0) { throw 'jpackage 打包失败' }

    $appDir = Join-Path $releaseRoot $appName
    $exePath = Join-Path $appDir "$appName.exe"
    $bytes = [IO.File]::ReadAllBytes($exePath)
    $peOffset = [BitConverter]::ToInt32($bytes, 0x3c)
    if ([BitConverter]::ToUInt16($bytes, $peOffset + 24 + 68) -ne 2) { throw 'EXE 不是 Windows GUI 程序' }
    if (-not (Test-Path -LiteralPath "$appDir\runtime\bin\server\jvm.dll")) { throw '打包结果缺少内置运行环境' }
    $cfg = Get-Content -LiteralPath (Join-Path $appDir "app\$appName.cfg") -Raw -Encoding UTF8
    if ($cfg -notmatch 'app.mainclass=com.label.MainApp') { throw '启动配置中的主类不正确' }

    $instructions = @"
现在买打印软件

解压整个文件夹后，双击“现在买打印软件.exe”启动，无需安装 Java。
请保留 exe 同级的 app 和 runtime 文件夹，复制软件时请复制整个文件夹。
已有 PDF 请在面单查询页重新点击“生成”，才能应用本次面单排版。
PDF 纸张大小为 76×130 毫米，打印机驱动请按实际标签纸设置纸张大小。
"@
    [IO.File]::WriteAllText((Join-Path $appDir '使用说明.txt'), $instructions, [Text.UTF8Encoding]::new($true))
    $zipPath = Join-Path $releaseRoot "$appName-Windows-x64.zip"
    Compress-Archive -LiteralPath $appDir -DestinationPath $zipPath -CompressionLevel Optimal
    $checksum = (Get-FileHash -LiteralPath $zipPath -Algorithm SHA256).Hash
    [IO.File]::WriteAllText((Join-Path $releaseRoot 'SHA256.txt'), "$checksum  $([IO.Path]::GetFileName($zipPath))`r`n")
    Write-Host "打包完成：$exePath"
    Write-Host "分发压缩包：$zipPath"
} finally {
    $env:JAVA_HOME = $originalJavaHome
    $env:Path = $originalPath
    Pop-Location
}
