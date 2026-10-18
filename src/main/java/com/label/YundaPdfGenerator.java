package com.label;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * 韵达快递面单 PDF 生成器
 *
 * 针对韵达面单的布局和样式进行优化
 */
public class YundaPdfGenerator {

    private static final double ORIG_WIDTH = YundaHtmlParser.YUNDA_ORIG_WIDTH;
    private static final double ORIG_HEIGHT = YundaHtmlParser.YUNDA_ORIG_HEIGHT;
    private final float pageW = LabelPdfLayout.PAGE_WIDTH;
    private final float pageH = LabelPdfLayout.PAGE_HEIGHT;
    private final float margin = LabelPdfLayout.MARGIN;
    private LabelPdfLayout layout = new LabelPdfLayout(null, ORIG_WIDTH, ORIG_HEIGHT);

    private float tx(double x) { return layout.x(x); }
    private float ty(double y) { return layout.y(y); }
    private float sw(double width) { return layout.width(width); }

    public void generate(WaybillData data, File outputFile) throws IOException, DocumentException {
        layout = new LabelPdfLayout(data, ORIG_WIDTH, ORIG_HEIGHT);
        Rectangle pageSize = new Rectangle(pageW, pageH);
        Document document = new Document(pageSize, 0, 0, 0, 0);
        ByteArrayOutputStream pdfBuffer = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, pdfBuffer);
        document.open();

        PdfContentByte cb = writer.getDirectContent();

        // 绘制横线
        cb.setLineWidth(0.5f);
        double headerBottom = data.hlines.stream()
                .filter(line -> line.width > (layout.right - layout.left) * 0.8
                        && line.top > layout.top + 20)
                .mapToDouble(line -> line.top).min().orElse(103);
        for (WaybillData.LineInfo l : data.hlines) {
            if (l.width > 0 && !isHeaderDecoration(l, headerBottom)) {
                cb.moveTo(tx(l.left), ty(l.top));
                cb.lineTo(tx(l.left + l.width), ty(l.top));
                cb.stroke();
            }
        }

        // 绘制竖线
        for (WaybillData.LineInfo l : data.vlines) {
            if (l.height > 0 && !isHeaderDecoration(l, headerBottom)) {
                cb.moveTo(tx(l.left), ty(l.top));
                cb.lineTo(tx(l.left), ty(l.top + l.height));
                cb.stroke();
            }
        }

        // 绘制图片（条形码、二维码等）
        for (WaybillData.ImageInfo img : data.images) {
            try {
                com.itextpdf.text.Image pdfImg = com.itextpdf.text.Image.getInstance(toBlackAndWhiteImage(img.data));
                LabelPdfLayout.ImagePlacement image = layout.imagePlacement(img);
                pdfImg.scaleAbsolute(image.width(), image.height());
                pdfImg.setAbsolutePosition(image.x(), image.y());
                cb.addImage(pdfImg);
            } catch (Exception e) {
                System.err.println("Failed to embed image: " + e.getMessage());
            }
        }

        // 加载中文字体
        BaseFont bf = loadChineseFont();

        // 韵达面单字段布局（基于真实数据：宽=607.65px, 高=960.00px）

        // 日期和时间（top=70.7）
        if (data.date != null) {
            addText(cb, data.date, bf, 4.18, 78, 350, 26.18, 18,
                    Element.ALIGN_LEFT);
        }

        // 分拣码（如：602C N216-00 39）
        // 加粗、居中显示，字体大小 57（接近原始 56.57），宽度足够避免换行
        if (data.sortingCode != null) {
            addText(cb, data.sortingCode, bf, 0, 110, 607.65, 60, 57,
                    Element.ALIGN_CENTER, true);
        }

        // 运单号（top=290.9, 居中显示）
        if (data.trackingNumber != null) {
            layout.text(cb, bf, data.trackingNumber, 0, 293, 607.65, 32,
                    12f, 10f, Element.ALIGN_CENTER, false);
        }

        // 中转站/分拣信息（top=333.4）
        // 加粗显示，字体加大
        if (data.transferStation != null) {
            addText(cb, data.transferStation, bf, 19.71, 355, 580, 40, 26,
                    Element.ALIGN_LEFT, true);
        }

        // 收件人信息（top=398.0）
        if (data.recipientInfo != null) {
            StringBuilder rText = new StringBuilder(data.recipientInfo);
            if (data.recipientAddr != null && !data.recipientAddr.isEmpty()) {
                rText.append('\n').append(data.recipientAddr);
            }
            layout.text(cb, bf, rText.toString(), 102.55, 410,
                    layout.textRight(102.55, 410, 535) - 102.55, 125,
                    (float) (18 * Math.min(layout.scaleX, layout.scaleY)), 5.5f,
                    Element.ALIGN_LEFT, false);
        }

        // 寄件人信息（top=541.5）
        if (data.senderInfo != null) {
            StringBuilder sText = new StringBuilder(data.senderInfo);
            if (data.senderAddr != null && !data.senderAddr.isEmpty()) {
                sText.append('\n').append(PrivacyMasker.maskSenderAddress(data.senderAddr));
            }
            layout.text(cb, bf, sText.toString(), 102.55, 550,
                    layout.textRight(102.55, 550, 665) - 102.55, 115,
                    7.5f, 6.5f, Element.ALIGN_LEFT, false);
        }

        // 验视章（top=666.7）
        if (data.verifiedStamp != null) {
            addText(cb, data.verifiedStamp, bf, 502.57, 675, 100, 37.08, 24,
                    Element.ALIGN_LEFT);
        }

        // 货到付款标签（top=4.0, 右上角，仅在非隐藏时显示）
        if (data.codLabel != null) {
            addText(cb, data.codLabel, bf, 387.38, 3.99, 100, 50, 16,
                    Element.ALIGN_CENTER);
        }

        // 商品信息（显示在面单最下方）
        if (data.productInfo != null && !data.productInfo.isEmpty()) {
            layout.product(cb, bf, data.productInfo, 675, 502.57, 37.08);
        }

        document.close();
        Files.write(outputFile.toPath(), pdfBuffer.toByteArray());
    }

    /** 忽略旧模板右上角内部横竖线，保留外框和整行分隔线。 */
    private boolean isHeaderDecoration(WaybillData.LineInfo line, double headerBottom) {
        double frameWidth = layout.right - layout.left;
        return line.left > layout.left + frameWidth * 0.4
                && line.left < layout.right - 10
                && line.top < headerBottom - 3
                && line.top + line.height <= headerBottom + 8;
    }

    private static BaseFont loadChineseFont() throws DocumentException, IOException {
        // 加载内置的黑体字体
        java.io.InputStream fontStream = YundaPdfGenerator.class.getClassLoader()
                .getResourceAsStream("font/simhei.ttf");
        if (fontStream != null) {
            try {
                byte[] fontBytes = readAllBytes(fontStream);
                java.nio.file.Path tmpFile = java.nio.file.Files.createTempFile("simhei", ".ttf");
                try {
                    java.nio.file.Files.write(tmpFile, fontBytes);
                    return BaseFont.createFont(tmpFile.toAbsolutePath().toString(),
                            BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                } finally {
                    try { java.nio.file.Files.deleteIfExists(tmpFile); } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                System.err.println("Failed to load bundled font: " + e.getMessage());
            }
        }
        // 回退到系统字体
        String[] fontPaths = {
            "/System/Library/Fonts/STHeiti Light.ttc,0",
            "/System/Library/Fonts/Supplemental/Songti.ttc,0",
            "C:/Windows/Fonts/simhei.ttf",
            "C:/Windows/Fonts/simsun.ttc,0",
            "C:/Windows/Fonts/msyh.ttc,0",
            "C:/Windows/Fonts/STSONG.TTF",
        };
        for (String path : fontPaths) {
            try {
                return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception ignored) {}
        }
        return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
    }

    private static byte[] toBlackAndWhiteImage(byte[] imageData) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(imageData));
        if (source == null) {
            return imageData;
        }

        BufferedImage binary = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xff;
                int red = (argb >>> 16) & 0xff;
                int green = (argb >>> 8) & 0xff;
                int blue = argb & 0xff;

                red = blendWithWhite(red, alpha);
                green = blendWithWhite(green, alpha);
                blue = blendWithWhite(blue, alpha);

                int luminance = (red * 299 + green * 587 + blue * 114) / 1000;
                binary.setRGB(x, y, luminance < 200 ? 0xff000000 : 0xffffffff);
            }
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(binary, "png", output);
        return output.toByteArray();
    }

    private static int blendWithWhite(int color, int alpha) {
        return (color * alpha + 255 * (255 - alpha)) / 255;
    }

    private static byte[] readAllBytes(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int n;
        while ((n = in.read(tmp)) != -1) {
            buf.write(tmp, 0, n);
        }
        return buf.toByteArray();
    }

    private void addText(PdfContentByte cb, String text, BaseFont bf,
                         double origX, double origY, double origW, double origH,
                         double origFontSize, int alignment) throws DocumentException {
        addText(cb, text, bf, origX, origY, origW, origH, origFontSize, alignment, false);
    }

    private void addText(PdfContentByte cb, String text, BaseFont bf,
                         double origX, double origY, double origW, double origH,
                         double origFontSize, int alignment, boolean bold) throws DocumentException {
        float fontSize = (float) (origFontSize * Math.min(layout.scaleX, layout.scaleY));
        double width = origW > 0 ? Math.min(origW, layout.right - origX - 4) : layout.right - origX - 4;
        double height = origH > 0 ? origH : origFontSize * 1.5;
        layout.text(cb, bf, text, origX, origY, width, height, fontSize,
                Math.min(fontSize, 5f), alignment, bold);
    }
}
