package com.label;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SortingCodeExtractorTest {
    @TempDir Path directory;

    @Test void bothTemplatesPrintCodeWithoutFormatOrLengthRestrictions() throws Exception {
        for (boolean yunda : new boolean[]{true, false}) {
            for (String code : new String[]{"A", "123456789012345", "640A D020-00", "732A R182-47 S04", "华东/杭州-1", "A B C D"}) {
                Path source = directory.resolve("source.html");
                Files.writeString(source, """
                        <div style='left:8px;top:%dpx;' class='text item'>%s</div>
                        """.formatted(yunda ? 99 : 278, code));
                WaybillData data = yunda ? YundaHtmlParser.parse(source.toFile()) : HtmlParser.parse(source.toFile());
                assertEquals(code, data.sortingCode);
                Path output = directory.resolve("code.pdf");
                if (yunda) new YundaPdfGenerator().generate(data, output.toFile());
                else new PdfGenerator().generate(data, output.toFile());
                try (PDDocument pdf = PDDocument.load(output.toFile())) {
                    assertTrue(new PDFTextStripper().getText(pdf).contains(code));
                }
            }
        }
    }

    @Test void bothTemplatesCombineVisibleBlocksInDisplayOrder() throws Exception {
        for (boolean yunda : new boolean[]{true, false}) {
            int top = yunda ? 99 : 278;
            Path source = directory.resolve("blocks.html");
            Files.writeString(source, """
                    <div class='item text' style='left:200px;top:%1$dpx;'>末段/4</div>
                    <div class='item text' style='left:8px;top:%1$dpx;'><span>640A</span>&nbsp;<b>D020-00</b></div>
                    <div hidden><div class='item text' style='top:%1$dpx;'>旧码</div></div>
                    <div style='visibility:hidden'><div class='item text' style='top:%1$dpx;'>隐藏码</div></div>
                    <div class='item text' style='top:600px;'>其他区域</div>
                    """.formatted(top));
            WaybillData data = yunda ? YundaHtmlParser.parse(source.toFile()) : HtmlParser.parse(source.toFile());
            assertEquals("640A D020-00 末段/4", data.sortingCode);
        }
    }
}
