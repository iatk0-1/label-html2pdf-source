package com.label;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlParser {

    // Matches: <div class="item text" style="...">content</div>
    private static final Pattern TEXT_DIV = Pattern.compile(
        "<div\\s+class=\"item text\"\\s+style=\"([^\"]+)\">\\s*(.*?)\\s*</div>",
        Pattern.DOTALL);

    public static WaybillData parse(File htmlFile) throws IOException {
        String rawHtml = new String(Files.readAllBytes(htmlFile.toPath()), StandardCharsets.UTF_8);
        Document doc = Jsoup.parse(rawHtml);

        WaybillData data = new WaybillData();
        data.sourceFile = htmlFile.getName();

        // Parse lines from raw HTML
        Elements items = doc.select("div.item");
        for (Element el : items) {
            String cls = el.className();
            String style = el.attr("style");
            double left = extractPx(style, "left");
            double top = extractPx(style, "top");

            if (cls.contains("hline")) {
                WaybillData.LineInfo l = new WaybillData.LineInfo();
                l.left = left;
                l.top = top;
                l.width = extractPx(style, "width");
                l.type = 0;
                data.hlines.add(l);
            } else if (cls.contains("vline")) {
                WaybillData.LineInfo l = new WaybillData.LineInfo();
                l.left = left;
                l.top = top;
                l.height = extractPx(style, "height");
                l.type = 1;
                data.vlines.add(l);
            }
        }

        // Extract text divs directly from raw HTML (preserves newlines)
        Matcher m = TEXT_DIV.matcher(rawHtml);
        List<TextEntry> textEntries = new ArrayList<>();
        while (m.find()) {
            String style = m.group(1);
            String content = m.group(2).trim();
            if (content.isEmpty()) continue;
            // Skip hidden elements — e.g. COD label with display:none
            if (style.matches(".*display\\s*:\\s*none.*")) continue;

            TextEntry entry = new TextEntry();
            entry.style = style;
            entry.content = content;
            entry.top = extractPx(style, "top");
            entry.left = extractPx(style, "left");
            textEntries.add(entry);
        }

        // Sort by top position
        textEntries.sort((a, b) -> Double.compare(a.top, b.top));

        for (TextEntry entry : textEntries) {
            String text = cleanText(entry.content);
            if (text.isEmpty()) continue;

            double top = entry.top;
            double left = entry.left;

            if (top < 109) {
                // Header: date or COD label
                if (text.matches("\\d{4}/\\d{2}/\\d{2}")) {
                    data.date = text;
                } else if (left > 200) {
                    data.codLabel = text;
                }
            } else if (top < 264) {
                // Barcode area: tracking number
                if (text.matches("[\\d ]{10,}")) {
                    data.trackingNumber = text;
                }
            } else if (top < 346) {
                // Sorting code
                if (text.length() > 5) {
                    data.sortingCode = text;
                }
            } else if (top < 418) {
                // Transfer station
                if (text.length() < 15 && !text.matches("[\\d ]{10,}")) {
                    data.transferStation = text;
                }
            } else if (top < 544) {
                // Recipient — multi-line
                parseRecipient(data, text);
            } else if (top < 670) {
                // Sender — multi-line
                parseSender(data, text);
            } else if (top < 762) {
                // Bottom tracking number repeat
                // Already captured
            } else {
                // Verified stamp
                if (text.contains("验")) {
                    data.verifiedStamp = text;
                }
            }
        }

        // Parse images
        Elements imgs = doc.select("img.item.image");
        for (Element img : imgs) {
            String src = img.attr("src");
            if (!src.startsWith("data:image/")) continue;

            String base64 = src.substring(src.indexOf("base64,") + 7).trim();
            try {
                byte[] imageData = Base64.getDecoder().decode(base64);
                String imgStyle = img.attr("style");
                double left = extractPx(imgStyle, "left");
                double top = extractPx(imgStyle, "top");
                double w = extractPx(imgStyle, "width");
                double h = extractPx(imgStyle, "height");

                // Inherit position from parent text div if not on img
                if (left == 0 && top == 0 && img.parent() != null) {
                    String parentStyle = img.parent().attr("style");
                    left = extractPx(parentStyle, "left");
                    top = extractPx(parentStyle, "top");
                }

                WaybillData.ImageInfo info = new WaybillData.ImageInfo();
                info.data = imageData;
                info.left = left;
                info.top = top;
                info.width = w;
                info.height = h;
                data.images.add(info);

                // Categorize by position and size
                if (top < 100 && left < 300 && w > 100) {
                    data.logoImage = imageData;
                } else if (top > 100 && top < 250 && w > 400) {
                    data.barcodeImage = imageData;
                } else if (top > 340 && top < 440 && w < 100) {
                    if (data.icon1Image == null) data.icon1Image = imageData;
                    else data.icon2Image = imageData;
                } else if (top > 650 && top < 750 && w > 200 && h < 70) {
                    data.bottomBarcodeImage = imageData;
                } else if (top > 750 && w < 200 && h < 200 && h > 50) {
                    data.qrCodeImage = imageData;
                }
            } catch (Exception e) {
                System.err.println("Failed to decode image: " + e.getMessage());
            }
        }

        return data;
    }

    /** Collapse internal whitespace to spaces, but keep structure */
    private static String cleanText(String raw) {
        // Replace HTML line breaks with newline
        raw = raw.replaceAll("<br\\s*/?>", "\n");
        // Remove other HTML tags
        raw = raw.replaceAll("<[^>]+>", "");
        // Strip leading/trailing whitespace per line, collapse blank lines
        String[] lines = raw.split("\\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(trimmed);
            }
        }
        return sb.toString();
    }

    private static void parseRecipient(WaybillData data, String text) {
        String[] lines = text.split("\\n");
        if (lines.length == 1) {
            data.recipientInfo = lines[0].trim();
            return;
        }
        data.recipientInfo = lines[0].trim();
        StringBuilder addr = new StringBuilder();
        for (int i = 1; i < lines.length; i++) {
            if (addr.length() > 0) addr.append('\n');
            addr.append(lines[i].trim());
        }
        data.recipientAddr = addr.toString();
    }

    private static void parseSender(WaybillData data, String text) {
        String[] lines = text.split("\\n");
        if (lines.length == 1) {
            data.senderInfo = lines[0].trim();
            return;
        }
        data.senderInfo = lines[0].trim();
        StringBuilder addr = new StringBuilder();
        for (int i = 1; i < lines.length; i++) {
            if (addr.length() > 0) addr.append('\n');
            addr.append(lines[i].trim());
        }
        data.senderAddr = addr.toString();
    }

    private static double extractPx(String style, String prop) {
        Pattern p = Pattern.compile(prop + "\\s*:\\s*([\\d.]+)\\s*px");
        Matcher m = p.matcher(style);
        if (m.find()) {
            return Double.parseDouble(m.group(1));
        }
        return 0;
    }

    private static class TextEntry {
        String style;
        String content;
        double top;
        double left;
    }
}
