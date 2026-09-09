package com.label;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 韵达快递面单 PDF 生成器
 *
 * 针对韵达面单的布局和样式进行优化
 */
public class YundaPdfGenerator {

    // 纸张尺寸：76mm x 130mm
    private static final float PAGE_WIDTH_MM = 76f;
    private static final float PAGE_HEIGHT_MM = 130f;
    private static final float PT_PER_MM = 2.83464567f;

    // 边距：3mm
    private static final float MARGIN_MM = 3f;

    // 韵达面单原始尺寸（与 YundaHtmlParser 保持一致）
    private static final double ORIG_WIDTH = YundaHtmlParser.YUNDA_ORIG_WIDTH;
    private static final double ORIG_HEIGHT = YundaHtmlParser.YUNDA_ORIG_HEIGHT;

    private float pageW;
    private float pageH;
    private float margin;
    private float contentW;
    private float contentH;
    private double scale;
    private float offsetX;
    private float offsetY;

    public YundaPdfGenerator() {
        this.pageW = PAGE_WIDTH_MM * PT_PER_MM;
        this.pageH = PAGE_HEIGHT_MM * PT_PER_MM;
        this.margin = MARGIN_MM * PT_PER_MM;
        this.contentW = pageW - 2 * margin;
        this.contentH = pageH - 2 * margin;
        this.scale = contentW / ORIG_WIDTH;
        double scaledH = ORIG_HEIGHT * scale;
        this.offsetX = margin;
        this.offsetY = (float) ((contentH - scaledH) / 2) + margin;
    }

    private float tx(double x) {
        return (float) (x * scale) + offsetX;
    }

    private float ty(double y) {
        return (float) (pageH - offsetY - y * scale);
    }

    private float sw(double w) {
        return (float) (w * scale);
    }

    public void generate(WaybillData data, File outputFile) throws IOException, DocumentException {
        Rectangle pageSize = new Rectangle(pageW, pageH);
        Document document = new Document(pageSize, 0, 0, 0, 0);
        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(outputFile));
        document.open();

        PdfContentByte cb = writer.getDirectContent();

        // 绘制横线
        cb.setLineWidth(0.5f);
        for (WaybillData.LineInfo l : data.hlines) {
            if (l.width > 0) {
                cb.moveTo(tx(l.left), ty(l.top));
                cb.lineTo(tx(l.left + l.width), ty(l.top));
                cb.stroke();
            }
        }

        // 绘制竖线
        for (WaybillData.LineInfo l : data.vlines) {
            if (l.height > 0) {
                cb.moveTo(tx(l.left), ty(l.top));
                cb.lineTo(tx(l.left), ty(l.top + l.height));
                cb.stroke();
            }
        }

        // 绘制图片（条形码、二维码等）
        for (WaybillData.ImageInfo img : data.images) {
            try {
                com.itextpdf.text.Image pdfImg = com.itextpdf.text.Image.getInstance(toBlackAndWhiteImage(img.data));
                float x = tx(img.left);
                float y = ty(img.top + img.height);
                float w = sw(img.width);
                float h = sw(img.height);
                pdfImg.scaleAbsolute(w, h);
                pdfImg.setAbsolutePosition(x, y);
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
            addText(cb, data.date, bf, 4.18, 90, 200, 26.18, 18,
                    Element.ALIGN_LEFT);
        }

        // 分拣码（如：602C N216-00 39）
        // 加粗、居中显示，字体大小 57（接近原始 56.57），宽度足够避免换行
        if (data.sortingCode != null) {
            addText(cb, data.sortingCode, bf, 0, 145, 607.65, 60, 57,
                    Element.ALIGN_CENTER, true);
        }

        // 运单号（top=290.9, 居中显示）
        if (data.trackingNumber != null) {
            addText(cb, data.trackingNumber, bf, 0, 310, 607.65, 37.08, 24,
                    Element.ALIGN_CENTER);
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
            addText(cb, rText.toString(), bf, 102.55, 420, 480, 120, 18,
                    Element.ALIGN_LEFT);
        }

        // 寄件人信息（top=541.5）
        if (data.senderInfo != null) {
            StringBuilder sText = new StringBuilder(data.senderInfo);
            if (data.senderAddr != null && !data.senderAddr.isEmpty()) {
                sText.append('\n').append(data.senderAddr);
            }
            addText(cb, sText.toString(), bf, 102.55, 555, 480, 100, 18,
                    Element.ALIGN_LEFT);
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
            addText(cb, data.productInfo, bf, 10.90, 720, 580, 0, 16,
                    Element.ALIGN_LEFT);
        }

        document.close();
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
                         double origFontSize, int alignment) {
        addText(cb, text, bf, origX, origY, origW, origH, origFontSize, alignment, false);
    }

    private void addText(PdfContentByte cb, String text, BaseFont bf,
                         double origX, double origY, double origW, double origH,
                         double origFontSize, int alignment, boolean bold) {

        String[] inputLines = text.split("\\n");
        float fontSize = (float) (origFontSize * scale);
        float lineHeight = fontSize * 3f;
        float maxW = origW > 0 ? sw(origW) : Float.MAX_VALUE;

        // 文本换行处理
        java.util.List<String> outLines = new java.util.ArrayList<>();
        for (String inputLine : inputLines) {
            String trimmed = inputLine.trim();
            if (trimmed.isEmpty()) continue;
            if (bf.getWidthPoint(trimmed, fontSize) <= maxW) {
                outLines.add(trimmed);
            } else {
                // 字符级换行
                StringBuilder buf = new StringBuilder();
                for (int ci = 0; ci < trimmed.length(); ci++) {
                    String ch = trimmed.substring(ci, ci + 1);
                    if (bf.getWidthPoint(buf.toString() + ch, fontSize) > maxW && buf.length() > 0) {
                        outLines.add(buf.toString());
                        buf.setLength(0);
                    }
                    buf.append(ch);
                }
                if (buf.length() > 0) outLines.add(buf.toString());
            }
        }

        for (int i = 0; i < outLines.size(); i++) {
            String line = outLines.get(i);

            cb.beginText();
            cb.setFontAndSize(bf, fontSize);

            // 加粗效果：设置文本渲染模式为填充+描边
            if (bold) {
                cb.setTextRenderingMode(PdfContentByte.TEXT_RENDER_MODE_FILL_STROKE);
                cb.setLineWidth(0.5f); // 描边宽度
            }

            float x = tx(origX);
            float y = ty(origY + fontSize * 0.85f + i * lineHeight);

            if (alignment == Element.ALIGN_CENTER && origW > 0) {
                float textWidth = bf.getWidthPoint(line, fontSize);
                x = tx(origX) + (maxW - textWidth) / 2;
            }

            cb.setTextMatrix(x, y);
            cb.showText(line);
            cb.endText();

            // 重置渲染模式
            if (bold) {
                cb.setTextRenderingMode(PdfContentByte.TEXT_RENDER_MODE_FILL);
            }
        }
    }
}
