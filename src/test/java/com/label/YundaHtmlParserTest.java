package com.label;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class YundaHtmlParserTest {
    @TempDir Path directory;

    @Test void missingRequiredFieldsMustStopGeneration() {
        WaybillData data = new WaybillData();
        assertThrows(IllegalArgumentException.class, data::validateYundaRequiredFields);
        data.sortingCode = "732A R182-47 S04";
        assertThrows(IllegalArgumentException.class, data::validateYundaRequiredFields);
        data.recipientAddr = "测试地址";
        assertDoesNotThrow(data::validateYundaRequiredFields);
    }

    @Test void productionWaybillCodeAllowsLetterPrefixAndKeepsRawAddressLines() throws Exception {
        WaybillData data = parse("""
                <div class='item text' style='left:8.6px;top:99px;'>732A
                    R182-47 S04</div>
                <div class='item text' style='left:102.55px;top:398.02px;'>测试 13700000000
                浙江省杭州市西湖区
                示例路1号101室</div>
                """);
        assertEquals("732A R182-47 S04", data.sortingCode);
        assertEquals("浙江省杭州市西湖区\n示例路1号101室", data.recipientAddr);
        Path pdf = directory.resolve("production-structure.pdf");
        new YundaPdfGenerator().generate(data, pdf.toFile());
        try (PDDocument document = PDDocument.load(pdf.toFile())) {
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", "");
            assertTrue(text.contains("732AR182-47S04"));
            assertTrue(text.contains("浙江省杭州市西湖区示例路1号101室"));
        }
    }

    @Test void sortingCodeAllowsLetterSuffixFromRealYundaTemplate() throws Exception {
        WaybillData data = parse("""
                <div class='item text' style='left:8.6px;top:99px;'>332C
                    E171-00 45B</div>
                """);
        assertEquals("332C E171-00 45B", data.sortingCode);
        Path pdf = directory.resolve("letter-suffix.pdf");
        new YundaPdfGenerator().generate(data, pdf.toFile());
        try (PDDocument document = PDDocument.load(pdf.toFile())) {
            assertTrue(new PDFTextStripper().getText(document).contains("332C E171-00 45B"));
        }
    }

    @Test void rawLineBreaksInsideRecipientBlockMustBePreserved() throws Exception {
        WaybillData data = parse("""
                <div class='item text' style='left:100px;top:398px;'>测试 13800000000
                浙江省杭州市西湖区
                测试路1号101室</div>
                """);
        assertEquals("浙江省杭州市西湖区\n测试路1号101室", data.recipientAddr);
    }

    @Test void recipientAndSenderSplitAcrossBlocksMustNotOverwriteAddresses() throws Exception {
        WaybillData data = parse("""
                <div class='item text' style='left:100px;top:398px;'>测试 13800000000</div>
                <div class='item text' style='left:100px;top:430px;'>浙江省杭州市西湖区</div>
                <div class='item text' style='left:100px;top:460px;'>测试路1号101室</div>
                <div class='item text' style='left:100px;top:541px;'>商家 13000000000</div>
                <div class='item text' style='left:100px;top:580px;'>浙江省杭州市钱塘区</div>
                <div class='item text' style='left:100px;top:610px;'>测试街道2号</div>
                """);
        assertTrue(data.recipientInfo.contains("138"));
        assertEquals("浙江省杭州市西湖区\n测试路1号101室", data.recipientAddr);
        assertEquals("浙江省杭州市钱塘区\n测试街道2号", data.senderAddr);
    }

    @Test void missingHtmlAddressUsesApiAddressButKeepsAnExistingHtmlAddress() {
        WaybillData parsed = new WaybillData();
        WaybillData api = new WaybillData();
        api.recipientAddr = "浙江省杭州市西湖区测试路1号";
        api.senderAddr = "浙江省杭州市钱塘区测试街道2号";
        parsed.recipientAddr = "  ";
        parsed.fillMissingAddressesFrom(api);
        assertEquals(api.recipientAddr, parsed.recipientAddr);
        assertEquals(api.senderAddr, parsed.senderAddr);
        parsed.recipientAddr = "微信原始面单地址";
        parsed.fillMissingAddressesFrom(api);
        assertEquals("微信原始面单地址", parsed.recipientAddr);
    }

    @Test void sortingCodeIsReadBelowDateAreaAndRenderedInPdf() throws Exception {
        for (int top : new int[]{70, 100, 114, 145, 180}) {
            WaybillData data = parse("""
                    <div class="item text" style="left:4.18px;top:70.7px;">2026/10/18</div>
                    <div class="item text" style="left:150px;top:70.7px;">22:13:10</div>
                    <div class="item text" style="left:20px;top:%dpx;">386A A010-00 10</div>
                    <div class="item text" style="left:20px;top:290.9px;">313109923667092</div>
                    <div class="item text" style="left:19.71px;top:333.4px;">集包地：杭州-华东-省内件</div>
                    """.formatted(top));
            assertEquals("386A A010-00 10", data.sortingCode);
            assertEquals("2026/10/18 22:13:10", data.date);
            assertEquals("313109923667092", data.trackingNumber);
            assertEquals("集包地：杭州-华东-省内件", data.transferStation);
            Path pdf = directory.resolve("sorting-" + top + ".pdf");
            new YundaPdfGenerator().generate(data, pdf.toFile());
            try (PDDocument document = PDDocument.load(pdf.toFile())) {
                assertEquals(1, document.getNumberOfPages());
                assertTrue(new PDFTextStripper().getText(document).contains("386A A010-00 10"),
                        "从 HTML 识别的分拣码必须完整出现在 PDF 中");
            }
        }
    }

    @Test void sortingCodeAcceptsNestedMarkupWhitespaceAndReorderedAttributes() throws Exception {
        WaybillData data = parse("""
                <div style='left:20px;top:114px;' data-id='route' class='text item extra'>
                  <span>386A</span>&nbsp;<strong>A010-00</strong><br/>10
                </div>
                """);
        assertEquals("386A A010-00 10", data.sortingCode);
    }

    @Test void hiddenCodesAndCodesOutsideHeaderCannotOverwriteVisibleSortingCode() throws Exception {
        WaybillData data = parse("""
                <div class="item text" style="left:20px;top:114px;">386A A010-00 10</div>
                <div class="item text" style="left:20px;top:145px;display: none;">111A A001-00 01</div>
                <div style="display: NONE;">
                  <div class="item text" style="left:20px;top:160px;">222A A002-00 02</div>
                </div>
                <div class="item text" style="left:20px;top:300px;">333A A003-00 03</div>
                """);
        assertEquals("386A A010-00 10", data.sortingCode);
        assertNull(parse("<div class='item text' style='top:114px;'>2026/10/18</div>").sortingCode);
    }

    private WaybillData parse(String html) throws Exception {
        Path source = directory.resolve("yunda.html");
        Files.writeString(source, html);
        return YundaHtmlParser.parse(source.toFile());
    }
}
