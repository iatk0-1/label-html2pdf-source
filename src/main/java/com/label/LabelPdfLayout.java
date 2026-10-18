package com.label;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Font;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.DefaultSplitCharacter;
import com.itextpdf.text.pdf.PdfContentByte;

import java.util.ArrayList;
import java.util.List;

/** 面单坐标映射及有边界的文字排版。所有区域坐标均沿用原 HTML 的像素坐标。 */
final class LabelPdfLayout {
    static final float PT_PER_MM = 72f / 25.4f;
    static final float PAGE_WIDTH = 76f * PT_PER_MM;
    static final float PAGE_HEIGHT = 130f * PT_PER_MM;
    static final float MARGIN = 0;
    private static final float QR_EDGE_GAP = 1.5f * PT_PER_MM;

    final double left;
    final double top;
    final double right;
    final double bottom;
    final double scaleX;
    final double scaleY;
    private final WaybillData data;

    LabelPdfLayout(WaybillData data, double originalWidth, double originalHeight) {
        this.data = data;
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        if (data != null) {
            for (WaybillData.LineInfo line : data.hlines) {
                if (line.width <= 0) continue;
                minX = Math.min(minX, line.left);
                minY = Math.min(minY, line.top);
                maxX = Math.max(maxX, line.left + line.width);
                maxY = Math.max(maxY, line.top);
            }
            for (WaybillData.LineInfo line : data.vlines) {
                if (line.height <= 0) continue;
                minX = Math.min(minX, line.left);
                minY = Math.min(minY, line.top);
                maxX = Math.max(maxX, line.left);
                maxY = Math.max(maxY, line.top + line.height);
            }
        }
        // 只有边框接近完整模板时才据其缩放，避免少量内部线条被误当成整张面单。
        boolean hasFrame = maxX - minX > originalWidth * 0.8
                && maxY - minY > originalHeight * 0.8;
        left = hasFrame ? minX : 0;
        top = hasFrame ? minY : 0;
        right = hasFrame ? maxX : originalWidth;
        bottom = hasFrame ? maxY : originalHeight;
        scaleX = (PAGE_WIDTH - 2 * MARGIN) / (right - left);
        scaleY = (PAGE_HEIGHT - 2 * MARGIN) / (bottom - top);
    }

    float x(double value) { return MARGIN + (float) ((value - left) * scaleX); }
    float y(double value) { return PAGE_HEIGHT - MARGIN - (float) ((value - top) * scaleY); }
    float width(double value) { return (float) (value * scaleX); }
    float height(double value) { return (float) (value * scaleY); }

    /** 二维码绘制与文字避让共用同一位置，避免挪图后仍按旧位置排版。 */
    ImagePlacement imagePlacement(WaybillData.ImageInfo image) {
        float x = x(image.left);
        float y = y(image.top + image.height);
        float w = width(image.width);
        float h = height(image.height);
        if ((image.height <= image.width * 2 && image.width <= image.height * 2)
                || image.top < 100) {
            float factor = (float) Math.min(scaleX, scaleY);
            float imageW = (float) image.width * factor;
            float imageH = (float) image.height * factor;
            x += (w - imageW) / 2;
            y += (h - imageH) / 2;
            w = imageW;
            h = imageH;
        }
        // 横向条码略收紧高度，给放大后的运单号留出清晰间隔。
        if (image.top > 100 && image.width > image.height * 3) {
            float reduction = h * 0.08f;
            y += reduction;
            h -= reduction;
        }
        if (isBottomQr(image)) {
            x = PAGE_WIDTH - QR_EDGE_GAP - w;
            y = QR_EDGE_GAP;
        }
        return new ImagePlacement(x, y, w, h);
    }

    private boolean isBottomQr(WaybillData.ImageInfo image) {
        double ratio = image.width / image.height;
        return ratio > 0.8 && ratio < 1.2 && image.left > (left + right) / 2
                && image.top > top + (bottom - top) * 0.65;
    }

    record ImagePlacement(float x, float y, float width, float height) {}

    /** 收寄件文字不能越过分栏竖线或右侧竖条码，并在其左侧留出间距。 */
    double textRight(double textLeft, double textTop, double textBottom) {
        double edge = right;
        if (data != null) {
            for (WaybillData.LineInfo line : data.vlines) {
                if (line.left > textLeft + 100 && line.top < textBottom
                        && line.top + line.height > textTop) {
                    edge = Math.min(edge, line.left);
                }
            }
            for (WaybillData.ImageInfo image : data.images) {
                if (image.height > image.width * 2 && image.left > textLeft + 100
                        && image.top < textBottom && image.top + image.height > textTop) {
                    edge = Math.min(edge, image.left);
                }
            }
        }
        return edge - 10;
    }

    void text(PdfContentByte cb, BaseFont font, String value, double x, double y,
              double width, double height, float fontSize, float minimumFontSize,
              int alignment, boolean bold) throws DocumentException {
        if (value == null || value.isBlank()) return;
        flow(cb, font, value, List.of(new Box(x, y, width, height)), fontSize,
                minimumFontSize, alignment, bold);
    }

    /** 商品备注从验视章同一行开始，绕开验视章和右下角二维码。 */
    void product(PdfContentByte cb, BaseFont font, String value, double startY)
            throws DocumentException {
        product(cb, font, value, startY, Double.NaN, 0);
    }

    void product(PdfContentByte cb, BaseFont font, String value, double startY,
                 double stampLeft, double stampHeight) throws DocumentException {
        if (value == null || value.isBlank()) return;
        double x = left + 10;
        double endY = bottom - 12;
        List<Box> exclusions = new ArrayList<>();
        if (Double.isFinite(stampLeft) && data != null && data.verifiedStamp != null
                && !data.verifiedStamp.isBlank()) {
            exclusions.add(new Box(stampLeft - 10, startY, right - stampLeft + 10, stampHeight + 10));
        }
        if (data != null) {
            for (WaybillData.ImageInfo image : data.images) {
                if (isBottomQr(image)) {
                    ImagePlacement qr = imagePlacement(image);
                    double qrLeft = left + (qr.x - MARGIN) / scaleX;
                    double qrBottom = top + (PAGE_HEIGHT - MARGIN - qr.y) / scaleY;
                    double qrTop = qrBottom - qr.height / scaleY;
                    exclusions.add(new Box(qrLeft - 12, qrTop - 12,
                            qr.width / scaleX + 24, qr.height / scaleY + 24));
                }
            }
        }
        flow(cb, font, value, size -> productRows(x, startY, endY, exclusions, size),
                9.5f, 6.5f, com.itextpdf.text.Element.ALIGN_LEFT, false,
                data == null ? null : data.getProductLinesForPdf());
    }

    private List<Box> productRows(double x, double startY, double endY, List<Box> exclusions, double size) {
        // 每一行都按同一行高定位，跨过验视章或二维码时不再额外留空。
        double rowHeight = size * 1.05 / scaleY;
        List<Box> boxes = new ArrayList<>();
        for (double bandTop = startY; bandTop + rowHeight <= endY + 0.001; bandTop += rowHeight) {
            double bandBottom = bandTop + rowHeight;
            double edge = right - 10;
            for (Box exclusion : exclusions) {
                if (exclusion.y < bandBottom && exclusion.y + exclusion.height > bandTop) {
                    edge = Math.min(edge, exclusion.x);
                }
            }
            boxes.add(new Box(x, bandTop, edge - x, bandBottom - bandTop));
        }
        return boxes;
    }

    private void flow(PdfContentByte cb, BaseFont baseFont, String value, List<Box> boxes,
                      float preferredSize, float minimumSize, int alignment, boolean bold)
            throws DocumentException {
        flow(cb, baseFont, value, size -> boxes, preferredSize, minimumSize, alignment, bold);
    }

    private void flow(PdfContentByte cb, BaseFont baseFont, String value,
                      java.util.function.DoubleFunction<List<Box>> boxesForSize,
                      float preferredSize, float minimumSize, int alignment, boolean bold)
            throws DocumentException {
        flow(cb, baseFont, value, boxesForSize, preferredSize, minimumSize, alignment, bold, null);
    }

    private void flow(PdfContentByte cb, BaseFont baseFont, String value,
                      java.util.function.DoubleFunction<List<Box>> boxesForSize,
                      float preferredSize, float minimumSize, int alignment, boolean bold,
                      List<WaybillData.PdfProductLine> productLines) throws DocumentException {
        // 先模拟排版，长地址和多条备注仅在确有必要时缩小，不截断或覆盖其他区域。
        float size = preferredSize;
        while (!fits(cb, baseFont, value, boxesForSize.apply(size), size, alignment, bold, productLines)) {
            if (size <= minimumSize) {
                throw new DocumentException("面单文字过长，当前区域无法完整容纳，请减少备注内容或检查地址格式");
            }
            size = Math.max(minimumSize, size - 0.25f);
        }
        ColumnText column = column(cb, baseFont, value, size, bold, productLines);
        for (Box box : boxesForSize.apply(size)) {
            if (box.width <= 0 || box.height <= 0) continue;
            position(column, box, size, alignment);
            cb.saveState();
            try {
                cb.rectangle(x(box.x), y(box.y + box.height), width(box.width), height(box.height));
                cb.clip();
                cb.newPath();
                if (!ColumnText.hasMoreText(column.go())) break;
            } finally {
                cb.restoreState();
            }
        }
    }

    private boolean fits(PdfContentByte cb, BaseFont baseFont, String value, List<Box> boxes,
                         float size, int alignment, boolean bold,
                         List<WaybillData.PdfProductLine> productLines) throws DocumentException {
        ColumnText column = column(cb, baseFont, value, size, bold, productLines);
        for (Box box : boxes) {
            if (box.width <= 0 || box.height <= 0) continue;
            position(column, box, size, alignment);
            if (!ColumnText.hasMoreText(column.go(true))) return true;
        }
        return false;
    }

    private static ColumnText column(PdfContentByte cb, BaseFont baseFont, String text,
                                     float size, boolean bold, List<WaybillData.PdfProductLine> productLines) {
        ColumnText result = new ColumnText(cb);
        Phrase phrase = new Phrase();
        if (productLines == null || productLines.isEmpty()) {
            phrase.add(chunk(baseFont, text.trim(), size, bold));
        } else {
            for (int index = 0; index < productLines.size(); index++) {
                WaybillData.PdfProductLine line = productLines.get(index);
                phrase.add(chunk(baseFont, (index == 0 ? "" : "\n") + line.text(), size, line.bold()));
            }
        }
        result.setText(phrase);
        result.setUseAscender(true);
        return result;
    }

    private static Chunk chunk(BaseFont baseFont, String text, float size, boolean bold) {
        Chunk chunk = new Chunk(text, new Font(baseFont, size, bold ? Font.BOLD : Font.NORMAL));
        chunk.setSplitCharacter((start, current, end, characters, chunks) -> {
            if (current + 1 < end && "：:，,。；;！？!?）)】]》%％".indexOf(characters[current + 1]) >= 0) return false;
            if (current + 2 < end && characters[current] == '备' && characters[current + 1] == '注'
                    && "：:".indexOf(characters[current + 2]) >= 0) return false;
            return DefaultSplitCharacter.DEFAULT.isSplitCharacter(start, current, end, characters, chunks);
        });
        return chunk;
    }

    private void position(ColumnText column, Box box, float size, int alignment) {
        column.setSimpleColumn(x(box.x), y(box.y + box.height), x(box.x + box.width), y(box.y));
        column.setLeading(size * 1.12f);
        column.setAlignment(alignment);
    }

    private record Box(double x, double y, double width, double height) {}
}
