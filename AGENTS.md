# Windows 打包规范

- 项目主类为 `com.label.MainApp`，Maven 编译目标 Java 17，JavaFX 21.0.2。
- 使用 `build-release.ps1` 和 Corretto JDK 21 构建，详情见 `JPACKAGE_README.md`。
- 软件名称及 EXE 名称统一为“现在买打印软件”。
- Windows 图标为 `packaging/app-icon.ico`，窗口图标为 `src/main/resources/com/label/images/app-icon.png`。
- 正式发布使用 `jpackage --type app-image`，禁止添加 `--win-console`。
- JavaFX 不打入 shaded JAR；模块路径必须包含 base、controls、fxml、graphics 的普通及 Windows 分类器 JAR。
- 运行时必须包含 `java.net.http`、`jdk.crypto.ec`、`java.scripting`，确保接口请求、TLS 及 FXML 可用。
- 必须运行测试，核对 EXE 的 PE Subsystem 为 2，启动配置主类正确，完整 app/runtime 目录存在。
- 每次发布使用新目录；ZIP 必须包含完整应用文件夹，不能单独分发 exe。
- 旧产物清理必须核对绝对路径，只清理明确授权的目录；保留当前主源码和 Git 仓库。
