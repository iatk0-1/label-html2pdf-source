# jpackage 打包说明

## 概述

提供了两种 jpackage 打包方式：

1. **build-jpackage.bat** - 生成 Windows 安装程序（.exe）
2. **build-jpackage-simple.bat** - 生成独立应用文件夹（推荐）

## 推荐方式：build-jpackage-simple.bat

### 优点
- ✅ 无需安装 WiX Toolset
- ✅ 打包速度快
- ✅ 生成的应用可直接运行
- ✅ 包含完整的 JRE 和 JavaFX
- ✅ 可以压缩成 ZIP 分发

### 使用步骤

1. **确保环境**
   - Java 17 或更高版本
   - Maven 已配置

2. **运行打包脚本**
   ```cmd
   build-jpackage-simple.bat
   ```

3. **等待完成**
   - 脚本会自动执行以下步骤：
     - 清理旧构建
     - Maven 编译打包
     - 查找 JavaFX 模块
     - 使用 jpackage 打包
     - 创建 ZIP 压缩包（可选）

4. **输出结果**
   - 位置：`dist-jpackage\LabelPrinter\`
   - 启动文件：`LabelPrinter.exe`
   - 大小：约 150-200 MB

### 分发方式

**方式一：文件夹分发**
- 将整个 `LabelPrinter` 文件夹复制到目标电脑
- 双击 `LabelPrinter.exe` 运行

**方式二：ZIP 压缩包分发**
- 使用脚本生成的 ZIP 文件
- 解压后运行 `LabelPrinter.exe`

## 高级方式：build-jpackage.bat

### 前置要求
- 安装 WiX Toolset 3.11+
  - 下载地址：https://wixtoolset.org/
  - 安装后需要重启命令行

### 优点
- ✅ 生成标准的 Windows 安装程序
- ✅ 支持开始菜单快捷方式
- ✅ 支持自定义安装目录
- ✅ 支持卸载程序

### 使用步骤

1. **安装 WiX Toolset**
   ```
   下载并安装：https://github.com/wixtoolset/wix3/releases
   ```

2. **运行打包脚本**
   ```cmd
   build-jpackage.bat
   ```

3. **输出结果**
   - 位置：`dist-jpackage\LabelPrinter-1.0.0.exe`
   - 类型：Windows 安装程序
   - 大小：约 150-200 MB

### 分发方式
- 将 `.exe` 安装程序发送给用户
- 用户双击安装，选择安装目录
- 安装完成后可在开始菜单找到

## 对比

| 特性 | build-jpackage-simple.bat | build-jpackage.bat |
|------|---------------------------|-------------------|
| 需要 WiX | ❌ 不需要 | ✅ 需要 |
| 打包速度 | 快（2-3分钟） | 慢（5-10分钟） |
| 输出类型 | 应用文件夹 | 安装程序 |
| 分发方式 | 复制文件夹或ZIP | 运行安装程序 |
| 开始菜单 | ❌ 无 | ✅ 有 |
| 卸载程序 | ❌ 无 | ✅ 有 |
| 推荐场景 | 内部使用、快速分发 | 正式发布、外部用户 |

## 常见问题

### 1. jpackage 命令找不到
**原因**：Java 版本低于 14

**解决**：
```cmd
java -version
```
确保显示 Java 17 或更高版本

### 2. 找不到 JavaFX 模块
**原因**：Maven 未下载 JavaFX 依赖

**解决**：
```cmd
mvn dependency:resolve
```

### 3. WiX Toolset 未找到（仅 build-jpackage.bat）
**原因**：未安装 WiX 或未重启命令行

**解决**：
1. 下载安装 WiX Toolset
2. 重启命令行窗口
3. 或使用 build-jpackage-simple.bat

### 4. 打包后程序无法启动
**原因**：JavaFX 模块未正确打包

**解决**：
- 检查 Maven 依赖是否完整
- 确保 JavaFX 版本为 21.0.2
- 查看错误日志

## 技术细节

### jpackage 参数说明

**--type app-image**
- 生成应用文件夹，包含 JRE 和应用
- 无需安装，直接运行

**--type exe**
- 生成 Windows 安装程序
- 需要 WiX Toolset

**--module-path**
- 指定 JavaFX 模块路径
- 包含 javafx-controls、javafx-graphics、javafx-base、javafx-fxml

**--add-modules**
- 添加 JavaFX 模块到运行时
- 必须包含应用使用的所有 JavaFX 模块

**--win-console**
- 显示控制台窗口（用于调试）
- 正式发布时可以移除

### 目录结构

```
dist-jpackage/
└── LabelPrinter/
    ├── LabelPrinter.exe          # 启动程序
    ├── app/
    │   └── label-html2pdf-1.0.0.jar  # 应用 JAR
    ├── runtime/
    │   └── ...                   # JRE 运行时
    └── lib/
        └── ...                   # JavaFX 和其他依赖
```

## 最佳实践

1. **开发阶段**
   - 使用 `build-jpackage-simple.bat`
   - 快速打包测试

2. **内部分发**
   - 使用 `build-jpackage-simple.bat`
   - 创建 ZIP 压缩包
   - 通过网盘或内部系统分发

3. **正式发布**
   - 使用 `build-jpackage.bat`
   - 生成安装程序
   - 提供专业的安装体验

4. **版本管理**
   - 修改 `APP_VERSION` 变量
   - 每次发布更新版本号
   - 保留历史版本的安装包

## 更新日志

### v1.0.0 (2026-05-30)
- ✅ 初始版本
- ✅ 支持韵达和中通面单
- ✅ 修复所有已知问题
- ✅ 添加 jpackage 打包支持
