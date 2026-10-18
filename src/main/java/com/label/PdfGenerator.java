package com.label;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.html.simpleparser.HTMLWorker;
import com.itextpdf.text.html.simpleparser.StyleSheet;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class PdfGenerator {

    private static final double ORIG_WIDTH = 606.04;
    private static final double ORIG_HEIGHT = 1042.04;
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

        // Draw all horizontal lines
        cb.setLineWidth(0.5f);
        for (WaybillData.LineInfo l : data.hlines) {
            if (l.width > 0) {
                cb.moveTo(tx(l.left), ty(l.top));
                cb.lineTo(tx(l.left + l.width), ty(l.top));
                cb.stroke();
            }
        }

        // Draw all vertical lines
        for (WaybillData.LineInfo l : data.vlines) {
            if (l.height > 0) {
                cb.moveTo(tx(l.left), ty(l.top));
                cb.lineTo(tx(l.left), ty(l.top + l.height));
                cb.stroke();
            }
        }

        // Draw all images
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

        // Draw text fields — use Chinese-capable font
        BaseFont bf = loadChineseFont();

        // Date — top area
        if (data.date != null) {
            addText(cb, data.date, bf, 10.90, 80, 322.66, 26.18, 21.80,
                    Element.ALIGN_LEFT);
        }

        // Tracking number — barcode area
        if (data.trackingNumber != null) {
            layout.text(cb, bf, data.trackingNumber, 0, 232, 584.26, 30,
                    12f, 10f, Element.ALIGN_CENTER, false);
        }

        // Sorting code
        if (data.sortingCode != null) {
            addText(cb, data.sortingCode, bf, 10.90, 278, 588.62, 62, 56.68,
                    Element.ALIGN_CENTER);
        }

        // Transfer station
        if (data.transferStation != null) {
            addText(cb, data.transferStation, bf, 97.00, 365, 501.42, 44, 39.24,
                    Element.ALIGN_LEFT);
        }

        // Recipient
        if (data.recipientInfo != null) {
            StringBuilder rText = new StringBuilder(data.recipientInfo);
            if (data.recipientAddr != null && !data.recipientAddr.isEmpty()) {
                rText.append('\n').append(data.recipientAddr);
            }
            layout.text(cb, bf, rText.toString(), 85.02, 430,
                    layout.textRight(85.02, 430, 540) - 85.02, 110,
                    (float) (21 * Math.min(layout.scaleX, layout.scaleY)), 5.5f,
                    Element.ALIGN_LEFT, false);
        }

        // Sender
        if (data.senderInfo != null) {
            StringBuilder sText = new StringBuilder(data.senderInfo);
            if (data.senderAddr != null && !data.senderAddr.isEmpty()) {
                sText.append('\n').append(PrivacyMasker.maskSenderAddress(data.senderAddr));
            }
            layout.text(cb, bf, sText.toString(), 82.5, 555,
                    layout.textRight(82.5, 555, 665) - 82.5, 110,
                    7.5f, 6.5f, Element.ALIGN_LEFT, false);
        }

        // Verified stamp
        if (data.verifiedStamp != null) {
            addText(cb, data.verifiedStamp, bf, 495.92, 782, 100.30, 37.08, 30.52,
                    Element.ALIGN_LEFT);
        }

        // COD label (fit 3 CJK chars in narrow box: use smaller font)
        if (data.codLabel != null) {
            addText(cb, data.codLabel, bf, 514.31, 15.17, 74.14, 78.50, 16,
                    Element.ALIGN_CENTER);
        }

        // Bottom tracking number repeat
        if (data.trackingNumber != null) {
            layout.text(cb, bf, data.trackingNumber, 180.94, 731, 425.10, 30,
                    10.5f, 10f, Element.ALIGN_CENTER, false);
        }

        // Product info at bottom (商品信息显示在面单最下方)
        if (data.productInfo != null && !data.productInfo.isEmpty()) {
            layout.product(cb, bf, data.productInfo, 782, 495.92, 37.08);
        }

        document.close();
        Files.write(outputFile.toPath(), pdfBuffer.toByteArray());
    }

    /**
     * 直接用 HTML 渲染为 PDF（适用于微信 print_html 等无法被 HtmlParser 解析的 HTML）
     */
    public void generateFromHtml(File htmlFile, File outputFile, String productInfo) throws IOException, DocumentException {
        generateFromHtml(htmlFile, outputFile, productInfo, java.util.List.of());
    }

    public void generateFromHtml(File htmlFile, File outputFile, String productInfo,
                                 java.util.List<WaybillOrderItem> orderItems) throws IOException, DocumentException {
        WaybillData productData = new WaybillData();
        productData.productInfo = productInfo;
        productData.orderItems = orderItems;
        layout = new LabelPdfLayout(productData, ORIG_WIDTH, ORIG_HEIGHT);
        String html = new String(Files.readAllBytes(htmlFile.toPath()), StandardCharsets.UTF_8);

        Rectangle pageSize = new Rectangle(pageW, pageH);
        Document document = new Document(pageSize, margin, margin, margin, margin);
        ByteArrayOutputStream pdfBuffer = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, pdfBuffer);
        document.open();

        // 注册中文字体
        BaseFont bf = loadChineseFont();
        StyleSheet styles = new StyleSheet();
        styles.loadTagStyle("body", "face", bf.getPostscriptFontName());
        styles.loadTagStyle("body", "encoding", BaseFont.IDENTITY_H);
        styles.loadTagStyle("body", "size", "10pt");

        // 用 HTMLWorker 解析 HTML 并渲染到 PDF
        HTMLWorker htmlWorker = new HTMLWorker(document);
        htmlWorker.setStyleSheet(styles);
        htmlWorker.parse(new StringReader(html));

        // 在面单最下方空白处渲染商品信息
        if (productInfo != null && !productInfo.isEmpty()) {
            PdfContentByte cb = writer.getDirectContent();
            layout.product(cb, bf, productInfo, 820);
        }

        document.close();
        Files.write(outputFile.toPath(), pdfBuffer.toByteArray());
    }

    private static BaseFont loadChineseFont() throws DocumentException, IOException {
        // Load bundled SimHei font — copy from classpath to temp file for iText
        java.io.InputStream fontStream = PdfGenerator.class.getClassLoader()
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
        // Fallback: try system Chinese fonts (macOS and Windows)
        String[] fontPaths = {
            // macOS
            "/System/Library/Fonts/STHeiti Light.ttc,0",
            "/System/Library/Fonts/Supplemental/Songti.ttc,0",
            // Windows
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
