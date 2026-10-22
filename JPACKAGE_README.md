# 现在买打印软件打包说明

## 开发环境

- Maven 编译目标：Java 17。
- JavaFX：21.0.2。
- Windows 发布使用 Corretto JDK 21，默认位置为用户目录下 `.jdks/corretto-21.0.9`。

## 构建

在项目目录执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-release.ps1
```

可以通过 `-JdkPath` 指定 JDK，通过 `-ReleaseId` 指定唯一发布编号。脚本会运行 Maven 构建和测试，生成带 Java / JavaFX 运行环境的 Windows x64 应用目录及 ZIP。

产物位于 `release/<发布编号>/`：

```text
现在买打印软件/
  现在买打印软件.exe
  app/
  runtime/
  使用说明.txt
现在买打印软件-Windows-x64.zip
SHA256.txt
```

解压后双击 exe 即可使用，无需安装 Java。分发时必须保留整个应用目录。EXE 使用 Windows GUI 子系统，不显示控制台。

## 名称与图标

- EXE 名称：现在买打印软件。
- Windows 图标：`packaging/app-icon.ico`，包含多种分辨率。
- JavaFX 窗口图标：`src/main/resources/com/label/images/app-icon.png`。
- 两种图标来自同一原始图片。

## 面单排版

PDF 尺寸为 76×130 毫米，外框贴页面边缘。收件地址自动换行并避开竖条码，寄件详细地址脱敏，商品备注从验视章所在行开始紧凑排版并避开验视章和右下角二维码。韵达和中通分拣码按原始 HTML 对应区域读取并打印，不限制字符、长度或段数；多个可见文本块按位置汇总，隐藏内容不参与打印。

已有 PDF 会被打印流程复用，需要先重新点击“生成”，才能使用更新后的排版。

## 列表字段复制

面单查询列表的序号、订单编号、运单号、收件人、收件地址、备注、创建时间、生成时间和状态均支持鼠标拖选文字后按 `Ctrl+C` 复制。点击字段后直接按 `Ctrl+C` 可复制完整字段；右键菜单提供“复制选中文字”“复制完整字段”和“全选”。打印列表的文件名同样支持复制。字段为只读显示，复制时保留完整内容和换行。

发布包、预览、临时目录和构建输出不提交到 Git。清理历史产物时必须核对绝对路径，保留当前主源码和 `.git` 仓库。
