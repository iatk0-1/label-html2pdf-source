package com.label;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;

public final class PdfPreviewer {
    private PdfPreviewer() {
    }

    public static void open(File pdfFile) throws IOException {
        if (pdfFile == null || !pdfFile.isFile()) {
            throw new IOException("PDF 文件不存在");
        }
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            throw new IOException("当前系统不支持打开 PDF 文件");
        }
        Desktop.getDesktop().open(pdfFile);
    }
}
