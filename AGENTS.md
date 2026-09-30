# Windows EXE 打包注意事项

## 适用项目

- 项目：`label-html2pdf`
- 主类：`com.label.MainApp`
- JavaFX：`21.0.2`
- Maven 编译目标：Java `17`
- 打包 JDK：`C:\Users\54417\.jdks\corretto-21.0.9`

## 推荐打包流程

1. 使用 Corretto JDK 21 执行 Maven 构建：

   ```powershell
   $jdk = 'C:\Users\54417\.jdks\corretto-21.0.9'
   $env:JAVA_HOME = $jdk
   $env:Path = "$jdk\bin;" + $env:Path
   mvn clean package -DskipTests
   ```

2. 使用 `target\label-html2pdf-1.0.0.jar` 作为 jpackage 的主 JAR。该 JAR 已由 Maven Shade 插件合并非 JavaFX 依赖，JavaFX 模块不能重复打进 shaded JAR。

3. 准备 JavaFX 模块路径，必须包含以下模块的普通 JAR 和 Windows 分类器 JAR：

   - `javafx-base`
   - `javafx-controls`
   - `javafx-fxml`
   - `javafx-graphics`

4. 推荐使用 `jpackage --type app-image`：

   - 会生成完整的应用目录，包含运行时和 JavaFX。
   - 目标电脑不需要预装 Java。
   - 不需要 WiX Toolset。
   - 分发时必须保留整个 `LabelPrinter` 目录，不能只拿单独的 `LabelPrinter.exe`。

5. 关键 jpackage 参数：

   ```text
   --type app-image
   --name LabelPrinter
   --app-version 1.0.0
   --main-jar label-html2pdf-1.0.0.jar
   --main-class com.label.MainApp
   --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base,java.net.http,jdk.crypto.ec
   --java-options "--add-opens=javafx.graphics/javafx.scene=ALL-UNNAMED"
   ```

## 无控制台窗口要求

- 正式发布时绝对不要加 `--win-console`，该参数会显示黑色控制台窗口。
- jpackage 生成的 exe 应为 Windows GUI 子系统。可用 PowerShell 检查 PE Subsystem：

  ```powershell
  $exe = 'dist-jpackage-win-YYYYMMDD-jdk21-release\LabelPrinter\LabelPrinter.exe'
  $bytes = [IO.File]::ReadAllBytes((Resolve-Path $exe))
  $pe = [BitConverter]::ToInt32($bytes, 0x3c)
  $subsystem = [BitConverter]::ToUInt16($bytes, $pe + 24 + 68)
  $subsystem
  ```

- 输出必须是 `2`，代表 `WINDOWS_GUI`；输出 `3` 代表控制台程序，不符合发布要求。

## 发布目录和分发

- 每次使用新的输入、输出目录，避免覆盖旧版本，例如：

  ```text
  jpackage-input-win-20260915-jdk21-release
  dist-jpackage-win-20260915-jdk21-release
  ```

- 产物结构应包含：

  ```text
  LabelPrinter\
  ├─ LabelPrinter.exe
  ├─ app\
  └─ runtime\
  ```

- 推荐把整个 `LabelPrinter` 目录压缩为 ZIP 分发。ZIP 解压后双击 `LabelPrinter.exe` 启动。
- `app\LabelPrinter.cfg` 中应确认主类为 `com.label.MainApp`，且不出现控制台相关参数。

## 常见坑

- 现有 `build-jpackage*.bat` 脚本中部分带有 `--win-console`，不能直接用于正式发布；使用前必须移除。
- `--type exe` 是 Windows 安装程序，通常需要 WiX Toolset；用户要求“解压后直接运行”时使用 `--type app-image`。
- PowerShell 调 Maven 属性时，包含 `=` 的参数建议整体加引号，例如 `'-Dmaven.compiler.debug=false'`，否则可能被错误解析成 Maven 生命周期阶段。
- Maven 的 `maven.compiler.debug=false` 不一定会移除所有 `LineNumberTable` 元数据；“不显示控制台窗口”应以 exe 的 PE Subsystem 为准，不要把这两个概念混为一谈。
- 打包完成后应核对：JDK 版本、exe 是否存在、完整目录是否存在、PE Subsystem 是否为 `2`、ZIP 是否包含 `app` 和 `runtime`。

