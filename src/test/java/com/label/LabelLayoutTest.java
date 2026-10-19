package com.label;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSNumber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LabelLayoutTest {
    @TempDir Path directory;

    @Test void multiQuantityProductAndWrappedRemarksAreBoldInBothTemplates() throws Exception {
        for (int mode = 0; mode < 3; mode++) {
            boolean yunda = mode == 1;
            WaybillData data = new WaybillData();
            data.orderItems = List.of(
                    new WaybillOrderItem(1L, 1L, "A001", "多件商品 加厚保暖外套 / 黑色 / XL * 2", 2,
                            "请检查拉链和包装，多件商品的这段长备注换行后也要加粗", "管理员要求检查配件并分开包装"),
                    new WaybillOrderItem(2L, 2L, "A002", "单件商品 纯棉长袖 * 1", 1, "普通字重", "单件商家备注"),
                    new WaybillOrderItem(3L, 3L, "A003", "缺少件数的商品 * 9", null, "不根据商品名称猜测数量"));
            data.productInfo = data.getProductInfoForPdf();
            Path output = directory.resolve(mode == 2 ? "bold-fallback.pdf" : yunda ? "bold-yunda.pdf" : "bold-zto.pdf");
            if (mode == 2) {
                Path html = directory.resolve("fallback.html");
                Files.writeString(html, "<html><body></body></html>");
                new PdfGenerator().generateFromHtml(html.toFile(), output.toFile(), data.productInfo, data.orderItems);
            } else if (yunda) new YundaPdfGenerator().generate(data, output.toFile());
            else new PdfGenerator().generate(data, output.toFile());
            try (PDDocument pdf = PDDocument.load(output.toFile())) {
                List<Boolean> strokes = new ArrayList<>();
                List<Float> baseline = new ArrayList<>();
                PDFTextStripper stripper = new PDFTextStripper() {
                    @Override protected void processTextPosition(TextPosition text) {
                        for (char c : text.getUnicode().toCharArray()) {
                            if (!Character.isWhitespace(c)) {
                                strokes.add(getGraphicsState().getTextState().getRenderingMode().isStroke());
                                baseline.add(text.getYDirAdj());
                            }
                        }
                        super.processTextPosition(text);
                    }
                };
                String actual = stripper.getText(pdf).replaceAll("\\s+", "");
                assertEquals(data.productInfo.replaceAll("\\s+", ""), actual);
                int firstNoteIndex = data.orderItems.get(0).toPdfLine().split("\n", 2)[0]
                        .replaceAll("\\s+", "").length();
                assertTrue(baseline.get(firstNoteIndex) > baseline.get(firstNoteIndex - 1) + 1,
                        "备注必须从商品信息下一行开始，不能接在商品后面");
                int boldLength = data.orderItems.get(0).toPdfLine().replaceAll("\\s+", "").length();
                assertTrue(strokes.subList(0, boldLength).stream().allMatch(Boolean::booleanValue),
                        "多件商品及其换行备注均应加粗");
                assertTrue(strokes.subList(boldLength, strokes.size()).stream().noneMatch(Boolean::booleanValue),
                        "单件和件数为空的商品不能加粗");
                assertTrue(baseline.subList(0, boldLength).stream().distinct().count() > 1,
                        "必须覆盖多件商品实际换行的情况");
            }
            String previewDir = System.getProperty("label.previewDir");
            if (previewDir != null) {
                Path preview = Path.of(previewDir);
                Files.createDirectories(preview);
                Files.copy(output, preview.resolve(output.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        WaybillData legacy = new WaybillData();
        legacy.productInfo = "旧数据商品 * 2";
        assertFalse(legacy.getProductLinesForPdf().get(0).bold(), "无件数字段时保持原字重");
    }

    @Test void yundaHeaderRemovesInteriorMarksAndKeepsOuterFrame() throws Exception {
        WaybillData data = fixture(607.65, 960, true);
        addLine(data, 2, 103, 607.65, 0);
        addLine(data, 359, 42, 151, 0);
        addLine(data, 356, 6, 0, 99);
        Path output = directory.resolve("header.pdf");
        new YundaPdfGenerator().generate(data, output.toFile());
        LabelPdfLayout layout = new LabelPdfLayout(data, 607.65, 960);
        try (PDDocument document = PDDocument.load(output.toFile())) {
            PDFStreamParser parser = new PDFStreamParser(document.getPage(0));
            parser.parse();
            List<Object> operands = new ArrayList<>();
            List<float[]> lines = new ArrayList<>();
            float startX = 0, startY = 0;
            for (Object token : parser.getTokens()) {
                if (token instanceof Operator op) {
                    if ((op.getName().equals("m") || op.getName().equals("l")) && operands.size() >= 2) {
                        float x = ((COSNumber) operands.get(operands.size() - 2)).floatValue();
                        float y = ((COSNumber) operands.get(operands.size() - 1)).floatValue();
                        if (op.getName().equals("l")) lines.add(new float[]{startX, startY, x, y});
                        startX = x; startY = y;
                    }
                    operands.clear();
                } else operands.add(token);
            }
            assertFalse(lines.stream().anyMatch(l -> Math.abs(l[0] - layout.x(359)) < 0.1
                    && Math.abs(l[1] - layout.y(42)) < 0.1), "右上角内部横线应去除");
            assertFalse(lines.stream().anyMatch(l -> Math.abs(l[0] - layout.x(356)) < 0.1
                    && Math.abs(l[1] - layout.y(6)) < 0.1), "右上角内部竖线应去除");
            assertTrue(lines.stream().anyMatch(l -> Math.abs(l[1] - layout.y(103)) < 0.1
                    && Math.abs(l[3] - layout.y(103)) < 0.1), "日期下方分隔线必须保留");
            assertTrue(lines.stream().anyMatch(l -> Math.abs(l[0]) < 0.1 && Math.abs(l[2]) < 0.1),
                    "左侧外框应贴近页面边缘");
        }
    }

    @Test void excessiveContentFailsWithoutOverwritingAnExistingPdf() throws Exception {
        for (boolean yunda : new boolean[]{false, true}) {
            WaybillData data = fixture(yunda ? 607.65 : 606.04, yunda ? 960 : 1042.04, yunda);
            data.productInfo = "超长备注内容".repeat(500);
            data.orderItems = List.of();
            Path output = directory.resolve(yunda ? "existing-yunda.pdf" : "existing-zto.pdf");
            Files.writeString(output, "保留原文件");
            assertThrows(com.itextpdf.text.DocumentException.class, () -> {
                if (yunda) new YundaPdfGenerator().generate(data, output.toFile());
                else new PdfGenerator().generate(data, output.toFile());
            });
            assertEquals("保留原文件", Files.readString(output));
        }
    }

    @Test void senderAddressKeepsOnlyAdministrativeAreas() {
        assertEquals("浙江省杭州市钱塘区******", PrivacyMasker.maskSenderAddress("浙江省 杭州市 钱塘区\n义蓬街道测试小区8幢301室"));
        assertEquals("杭州市钱塘区******", PrivacyMasker.maskSenderAddress("杭州市 钱塘区 义蓬街道测试小区"));
        assertEquals("上海市浦东新区******", PrivacyMasker.maskSenderAddress("上海市浦东新区世纪大道100号"));
        assertEquals("上海市浦东新区******", PrivacyMasker.maskSenderAddress("上海市浦东新区世纪大道超市旁"));
        assertEquals("广西壮族自治区南宁市青秀区******", PrivacyMasker.maskSenderAddress("广西壮族自治区南宁市青秀区民族大道1号"));
        assertEquals("江苏省苏州市昆山市******", PrivacyMasker.maskSenderAddress("江苏省苏州市昆山市玉山镇测试路1号"));
        assertEquals("浙江省杭州市钱塘区******", PrivacyMasker.maskSenderAddress("130****5216\n浙江省 杭州市 钱塘区 义蓬街道8号"));
        assertEquals("杭州市******", PrivacyMasker.maskSenderAddress("杭州市文一路幸福小区1号"));
        assertEquals("******", PrivacyMasker.maskSenderAddress("测试小区8幢301室"));
        assertNull(PrivacyMasker.maskSenderAddress(null));
    }

    @Test void bothTemplatesFillPaperAndKeepTextAwayFromBarcode() throws Exception {
        for (boolean yunda : new boolean[]{false, true}) {
            double width = yunda ? 607.65 : 606.04;
            double height = yunda ? 960 : 1042.04;
            WaybillData data = fixture(width, height, yunda);
            LabelPdfLayout layout = new LabelPdfLayout(data, width, height);
            assertEquals(LabelPdfLayout.MARGIN, layout.x(layout.left), 0.001);
            assertEquals(LabelPdfLayout.PAGE_WIDTH - LabelPdfLayout.MARGIN, layout.x(layout.right), 0.001);
            assertEquals(LabelPdfLayout.MARGIN, layout.y(layout.bottom), 0.001);
            assertEquals(LabelPdfLayout.PAGE_HEIGHT - LabelPdfLayout.MARGIN, layout.y(layout.top), 0.001);
            Path output = directory.resolve(yunda ? "yunda.pdf" : "zto.pdf");
            if (yunda) new YundaPdfGenerator().generate(data, output.toFile());
            else new PdfGenerator().generate(data, output.toFile());

            try (PDDocument pdf = PDDocument.load(output.toFile())) {
                assertEquals(1, pdf.getNumberOfPages());
                assertEquals(LabelPdfLayout.PAGE_WIDTH, pdf.getPage(0).getMediaBox().getWidth(), 0.01);
                assertEquals(LabelPdfLayout.PAGE_HEIGHT, pdf.getPage(0).getMediaBox().getHeight(), 0.01);
                List<TextPosition> positions = new ArrayList<>();
                PDFTextStripper stripper = new PDFTextStripper() {
                    @Override protected void processTextPosition(TextPosition text) {
                        positions.add(text);
                        super.processTextPosition(text);
                    }
                };
                String text = stripper.getText(pdf).replaceAll("\\s+", "");
                assertTrue(text.contains(data.recipientAddr.replaceAll("\\s+", "")), "长收件地址必须完整保留");
                assertTrue(text.contains(data.productInfo.replaceAll("\\s+", "")), "多行商品备注必须完整保留");
                assertTrue(text.contains("浙江省杭州市钱塘区******"));
                assertFalse(text.contains("义蓬街道"));
                assertFalse(text.contains("测试小区"));
                assertTrue(data.senderAddr.contains("义蓬街道"), "脱敏不能修改原始数据");
                double minY = LabelPdfLayout.PAGE_HEIGHT - layout.y(yunda ? 410 : 430);
                double maxY = LabelPdfLayout.PAGE_HEIGHT - layout.y(yunda ? 535 : 540);
                List<TextPosition> recipient = positions.stream()
                        .filter(p -> p.getYDirAdj() >= minY && p.getYDirAdj() <= maxY).toList();
                assertFalse(recipient.isEmpty());
                for (TextPosition p : recipient) {
                    assertTrue(p.getXDirAdj() + p.getWidthDirAdj() <= layout.x(455) + 0.1,
                            "收件信息不得侵入条码分栏");
                    assertTrue(p.getFontSizeInPt() >= 5.5f);
                    assertTrue(p.getFontSizeInPt() <= (yunda ? 18 : 21)
                            * Math.min(layout.scaleX, layout.scaleY) + 0.01,
                            "收件信息应恢复原模板字号");
                }
                // 商品信息占用二维码上方及左侧空间，任何文字都不能进入二维码。
                LabelPdfLayout.ImagePlacement qr = layout.imagePlacement(data.images.get(0));
                double qrLeft = qr.x(), qrRight = qr.x() + qr.width();
                double qrTop = LabelPdfLayout.PAGE_HEIGHT - qr.y() - qr.height();
                double qrBottom = LabelPdfLayout.PAGE_HEIGHT - qr.y();
                assertEquals(1.5 * LabelPdfLayout.PT_PER_MM, qr.y(), 0.01);
                assertEquals(1.5 * LabelPdfLayout.PT_PER_MM, LabelPdfLayout.PAGE_WIDTH - qrRight, 0.01);
                for (TextPosition p : positions) {
                    assertFalse(p.getXDirAdj() < qrRight && p.getXDirAdj() + p.getWidthDirAdj() > qrLeft
                            && p.getYDirAdj() > qrTop && p.getYDirAdj() - p.getHeightDir() < qrBottom,
                            "商品备注不得遮住二维码");
                }
                double productTop = LabelPdfLayout.PAGE_HEIGHT - layout.y(yunda ? 675 : 782);
                List<TextPosition> product = positions.stream().filter(p -> p.getXDirAdj() < layout.x(100)
                        && p.getYDirAdj() >= productTop).toList();
                assertFalse(product.isEmpty());
                assertTrue(product.stream().mapToDouble(TextPosition::getYDirAdj).min().orElseThrow()
                        < productTop + 12, "商品信息必须从验视章所在行开始");
                double stampLeft = layout.x(yunda ? 502.57 : 495.92);
                double stampBottom = LabelPdfLayout.PAGE_HEIGHT - layout.y((yunda ? 675 : 782) + 37.08);
                for (TextPosition p : positions) {
                    if (p.getYDirAdj() > productTop && p.getYDirAdj() < stampBottom && p.getFontSizeInPt() == 9.5f) {
                        assertTrue(p.getXDirAdj() + p.getWidthDirAdj() < stampLeft,
                                "同一行的商品备注不得覆盖验视章");
                    }
                }
            }
        }
    }

    private static WaybillData fixture(double width, double height, boolean yunda) throws Exception {
        WaybillData data = new WaybillData();
        addLine(data, 2, 2, width, 0);
        addLine(data, 2, height + 2, width, 0);
        addLine(data, 2, 2, 0, height);
        addLine(data, width + 2, 2, 0, height);
        addLine(data, 465, yunda ? 350 : 346, 0, 325);
        data.recipientInfo = "张* 138****5678";
        data.date = "2026/10/18";
        data.trackingNumber = "12345678901234";
        data.sortingCode = yunda ? "602C N216-00 39" : "320- Q3 46 [HW]";
        data.transferStation = yunda ? "集包地：杭州-长线" : "杭州";
        data.verifiedStamp = "已验视";
        data.recipientAddr = "浙江省杭州市西湖区三墩镇紫荆花北路新时代城市广场西区写字楼三号楼十八层一八零一室收发处";
        data.senderInfo = "李* 130****5216";
        data.senderAddr = "浙江省杭州市钱塘区义蓬街道测试小区8幢301室";
        for (int index = 0; index < 3; index++) {
            data.orderItems.add(new WaybillOrderItem(1L, 1L, "A012", "加厚保暖外套 / 黑色 / XL * 2", 2,
                    "请检查拉链，分开包装"));
            data.orderItems.add(new WaybillOrderItem(2L, 2L, "A013", "纯棉长袖 / 白色 / M * 1", 1,
                    "请不要折叠领口"));
        }
        data.productInfo = data.getProductInfoForPdf();
        BufferedImage qr = new BufferedImage(100, 100, BufferedImage.TYPE_BYTE_BINARY);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(qr, "png", bytes);
        WaybillData.ImageInfo image = new WaybillData.ImageInfo();
        image.data = bytes.toByteArray();
        image.left = 455; image.top = 820; image.width = 130; image.height = 130;
        data.images.add(image);
        return data;
    }

    private static void addLine(WaybillData data, double x, double y, double width, double height) {
        WaybillData.LineInfo line = new WaybillData.LineInfo();
        line.left = x; line.top = y; line.width = width; line.height = height;
        if (height == 0) data.hlines.add(line); else data.vlines.add(line);
    }
}
