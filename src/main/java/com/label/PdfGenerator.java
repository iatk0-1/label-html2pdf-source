package com.label;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.html.simpleparser.HTMLWorker;
import com.itextpdf.text.html.simpleparser.StyleSheet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class PdfGenerator {

    // Paper: 76mm x 130mm (in points: 1mm = 2.83464567pt)
    private static final float PAGE_WIDTH_MM = 76f;
    private static final float PAGE_HEIGHT_MM = 130f;
    private static final float PT_PER_MM = 2.83464567f;

    // Margins: 3mm each side
    private static final float MARGIN_MM = 3f;

    // Original HTML content area
    private static final double ORIG_WIDTH = 606.04;   // 608.22 - 2.18
    private static final double ORIG_HEIGHT = 1042.04;  // 1044.22 - 2.18

    private float pageW;
    private float pageH;
    private float margin;
    private float contentW;
    private float contentH;
    private double scale;
    private float offsetX;
    private float offsetY;

    public PdfGenerator() {
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
        // PDF Y axis is bottom-up; original HTML Y is top-down
        // contentH is the available content area height
        // We position from the top of the content area
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
                com.itextpdf.text.Image pdfImg = com.itextpdf.text.Image.getInstance(img.data);
                float x = tx(img.left);
                float y = ty(img.top + img.height); // bottom of image
                float w = sw(img.width);
                float h = sw(img.height);
                pdfImg.scaleAbsolute(w, h);
                pdfImg.setAbsolutePosition(x, y);
                cb.addImage(pdfImg);
            } catch (Exception e) {
                System.err.println("Failed to embed image: " + e.getMessage());
            }
        }

        // Draw text fields — use Chinese-capable font
        BaseFont bf = loadChineseFont();

        // Date — top area
        if (data.date != null) {
            addText(cb, data.date, bf, 10.90, 90.30, 322.66, 26.18, 21.80,
                    Element.ALIGN_LEFT);
        }

        // Tracking number — barcode area
        if (data.trackingNumber != null) {
            addText(cb, data.trackingNumber, bf, 0, 245.14, 584.26, 37.08, 26.16,
                    Element.ALIGN_CENTER);
        }

        // Sorting code
        if (data.sortingCode != null) {
            addText(cb, data.sortingCode, bf, 10.90, 310.96, 588.62, 0, 56.68,
                    Element.ALIGN_CENTER);
        }

        // Transfer station
        if (data.transferStation != null) {
            addText(cb, data.transferStation, bf, 97.00, 385, 501.42, 80.68, 39.24,
                    Element.ALIGN_LEFT);
        }

        // Recipient
        if (data.recipientInfo != null) {
            StringBuilder rText = new StringBuilder(data.recipientInfo);
            if (data.recipientAddr != null && !data.recipientAddr.isEmpty()) {
                rText.append('\n').append(data.recipientAddr);
            }
            addText(cb, rText.toString(), bf, 85.02, 440.56, 370.62, 150, 21,
                    Element.ALIGN_LEFT);
        }

        // Sender
        if (data.senderInfo != null) {
            StringBuilder sText = new StringBuilder(data.senderInfo);
            if (data.senderAddr != null && !data.senderAddr.isEmpty()) {
                sText.append('\n').append(data.senderAddr);
            }
            addText(cb, sText.toString(), bf, 82.5, 570.08, 371.82, 100, 21,
                    Element.ALIGN_LEFT);
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
            addText(cb, data.trackingNumber, bf, 180.94, 750.30, 425.10, 0, 26.16,
                    Element.ALIGN_CENTER);
        }

        document.close();
    }

    /**
     * 直接用 HTML 渲染为 PDF（适用于微信 print_html 等无法被 HtmlParser 解析的 HTML）
     */
    public void generateFromHtml(File htmlFile, File outputFile) throws IOException, DocumentException {
        String html = new String(Files.readAllBytes(htmlFile.toPath()), StandardCharsets.UTF_8);

        Rectangle pageSize = new Rectangle(pageW, pageH);
        Document document = new Document(pageSize, margin, margin, margin, margin);
        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(outputFile));
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

        document.close();
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

        String[] inputLines = text.split("\\n");
        float fontSize = (float) (origFontSize * scale);
        float lineHeight = fontSize * 3f;
        float maxW = origW > 0 ? sw(origW) : Float.MAX_VALUE;

        // Flatten: wrap each input line, collect all output lines
        java.util.List<String> outLines = new java.util.ArrayList<>();
        for (String inputLine : inputLines) {
            String trimmed = inputLine.trim();
            if (trimmed.isEmpty()) continue;
            if (bf.getWidthPoint(trimmed, fontSize) <= maxW) {
                outLines.add(trimmed);
            } else {
                // Character-level wrap for CJK / long strings
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

            float x = tx(origX);
            // In HTML coords, later lines are further down → larger Y → lower on PDF page
            float y = ty(origY + fontSize * 0.85f + i * lineHeight);

            if (alignment == Element.ALIGN_CENTER && origW > 0) {
                float textWidth = bf.getWidthPoint(line, fontSize);
                x = tx(origX) + (maxW - textWidth) / 2;
            }

            cb.setTextMatrix(x, y);
            cb.showText(line);
            cb.endText();
        }
    }

}
