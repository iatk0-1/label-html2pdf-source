package com.label;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 韵达快递面单 HTML 解析器
 *
 * 韵达面单的 HTML 结构与中通不同，需要专门的解析逻辑
 */
public class YundaHtmlParser {

    // 韵达面单的原始尺寸（根据实际 HTML：宽=607.65px, 高=960.00px）
    public static final double YUNDA_ORIG_WIDTH = 607.65;
    public static final double YUNDA_ORIG_HEIGHT = 960.00;

    private static final Pattern TEXT_DIV = Pattern.compile(
        "<div\\s+class=\"item text\"\\s+style=\"([^\"]+)\">\\s*(.*?)\\s*</div>",
        Pattern.DOTALL);

    public static WaybillData parse(File htmlFile) throws IOException {
        String rawHtml = new String(Files.readAllBytes(htmlFile.toPath()), StandardCharsets.UTF_8);
        Document doc = Jsoup.parse(rawHtml);

        WaybillData data = new WaybillData();
        data.sourceFile = htmlFile.getName();

        // 解析线条
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

        // 解析文本内容
        Matcher m = TEXT_DIV.matcher(rawHtml);
        java.util.List<TextEntry> textEntries = new java.util.ArrayList<>();
        while (m.find()) {
            String style = m.group(1);
            String content = m.group(2).trim();
            if (content.isEmpty()) continue;
            if (style.matches(".*display\\s*:\\s*none.*")) continue;

            TextEntry entry = new TextEntry();
            entry.style = style;
            entry.content = content;
            entry.top = extractPx(style, "top");
            entry.left = extractPx(style, "left");
            textEntries.add(entry);
        }

        // 按位置排序
        textEntries.sort((a, b) -> Double.compare(a.top, b.top));

        // 根据位置分配字段（基于真实韵达面单数据）
        for (TextEntry entry : textEntries) {
            String text = cleanText(entry.content);
            if (text.isEmpty()) continue;

            double top = entry.top;
            double left = entry.left;

            // 韵达面单的布局分析（基于实际数据）
            if (top < 100) {
                // 顶部区域：日期、时间、分拣码等
                if (text.matches("\\d{4}[/-]\\d{2}[/-]\\d{2}.*")) {
                    data.date = text;
                } else if (text.matches("\\d{2}:\\d{2}:\\d{2}")) {
                    // 时间信息，可以附加到日期
                    if (data.date != null) {
                        data.date = data.date + " " + text;
                    }
                } else if (text.matches("[A-Z0-9]+\\s+[A-Z0-9-]+\\s+\\d+")) {
                    // 分拣码（如：602C N216-00 39）
                    // 移除换行符，替换为空格
                    data.sortingCode = text.replaceAll("\\s+", " ").trim();
                } else if (text.contains("货到付款") || text.contains("代收")) {
                    // 检查是否隐藏（display:none）
                    if (!entry.style.contains("display:none") && !entry.style.contains("display: none")) {
                        data.codLabel = text;
                    }
                }
            } else if (top >= 290 && top < 350) {
                // 运单号区域（top=290.9 和 top=345.5）
                if (text.matches("[\\d]{10,}")) {
                    data.trackingNumber = text.replaceAll("\\s+", "");
                } else if (text.contains("-") && text.length() < 30) {
                    // 分拣码/中转站信息（如：浙江杭州-广州）
                    data.transferStation = text;
                }
            } else if (top >= 350 && top < 500) {
                // 收件人信息区域（top=398.0）
                parseRecipient(data, text);
            } else if (top >= 500 && top < 650) {
                // 寄件人信息区域（top=541.5）
                parseSender(data, text);
            } else if (top >= 650) {
                // 底部区域：验视章等（top=666.7）
                if (text.contains("验") || text.contains("已")) {
                    data.verifiedStamp = text;
                }
            }
        }

        // 解析图片（条形码、二维码等）
        Elements imgs = doc.select("img.item.image");
        for (Element img : imgs) {
            String src = img.attr("src");
            if (!src.startsWith("data:image/")) continue;

            // 检查图片本身是否隐藏
            String imgStyle = img.attr("style");
            if (imgStyle.contains("display:none") || imgStyle.contains("display: none")) {
                continue; // 跳过隐藏的图片
            }

            // 检查父元素是否隐藏
            if (img.parent() != null) {
                String parentStyle = img.parent().attr("style");
                if (parentStyle.contains("display:none") || parentStyle.contains("display: none")) {
                    continue; // 跳过父元素隐藏的图片
                }
            }

            String base64 = src.substring(src.indexOf("base64,") + 7).trim();
            try {
                byte[] imageData = Base64.getDecoder().decode(base64);
                double left = extractPx(imgStyle, "left");
                double top = extractPx(imgStyle, "top");
                double w = extractPx(imgStyle, "width");
                double h = extractPx(imgStyle, "height");

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

                // 根据位置和尺寸分类图片
                if (top < 100 && w > 80 && w < 200 && h > 80 && h < 200) {
                    // 二维码（右上角）
                    data.qrCodeImage = imageData;
                } else if (w < 100 && h < 100) {
                    // 小图标
                    if (data.icon1Image == null) {
                        data.icon1Image = imageData;
                    } else {
                        data.icon2Image = imageData;
                    }
                } else if (top > 800 && w > 150) {
                    // 底部条形码
                    data.bottomBarcodeImage = imageData;
                }
            } catch (Exception e) {
                System.err.println("Failed to decode image: " + e.getMessage());
            }
        }

        // 韵达面单特殊处理：解析所有 img 标签（包括非 class="item image" 的）
        Elements allImgs = doc.select("img");
        for (Element img : allImgs) {
            String src = img.attr("src");
            if (!src.startsWith("data:image/")) continue;

            // 检查图片本身是否隐藏
            String imgStyle = img.attr("style");
            if (imgStyle.contains("display:none") || imgStyle.contains("display: none")) {
                continue; // 跳过隐藏的图片
            }

            // 检查父元素是否隐藏
            if (img.parent() != null) {
                String parentStyle = img.parent().attr("style");
                if (parentStyle.contains("display:none") || parentStyle.contains("display: none")) {
                    continue; // 跳过父元素隐藏的图片
                }
            }

            String base64 = src.substring(src.indexOf("base64,") + 7).trim();
            try {
                byte[] imageData = Base64.getDecoder().decode(base64);
                double left = extractPx(imgStyle, "left");
                double top = extractPx(imgStyle, "top");
                double w = extractPx(imgStyle, "width");
                double h = extractPx(imgStyle, "height");

                // 如果图片本身没有位置，从父元素获取
                if (left == 0 && top == 0 && img.parent() != null) {
                    String parentStyle = img.parent().attr("style");
                    left = extractPx(parentStyle, "left");
                    top = extractPx(parentStyle, "top");
                    if (w == 0) w = extractPx(parentStyle, "width");
                    if (h == 0) h = extractPx(parentStyle, "height");
                }

                // Logo（左上角，宽度>100）
                if (top < 100 && left < 100 && w > 100 && data.logoImage == null) {
                    data.logoImage = imageData;

                    WaybillData.ImageInfo info = new WaybillData.ImageInfo();
                    info.data = imageData;
                    info.left = left;
                    info.top = top;
                    info.width = w;
                    info.height = h;
                    data.images.add(info);
                }
                // 主条形码（运单号上方，宽度>300）
                else if (top > 100 && top < 290 && w > 300 && data.barcodeImage == null) {
                    data.barcodeImage = imageData;

                    WaybillData.ImageInfo info = new WaybillData.ImageInfo();
                    info.data = imageData;
                    info.left = left;
                    info.top = top;
                    info.width = w;
                    info.height = h;
                    data.images.add(info);
                }
                // 竖向条形码（高度>宽度，在收寄件人右侧）
                else if (h > w && h > 200 && top > 300 && top < 700) {
                    WaybillData.ImageInfo info = new WaybillData.ImageInfo();
                    info.data = imageData;
                    info.left = left;
                    info.top = top;
                    info.width = w;
                    info.height = h;
                    data.images.add(info);
                }
            } catch (Exception e) {
                System.err.println("Failed to decode image: " + e.getMessage());
            }
        }

        return data;
    }

    private static String cleanText(String raw) {
        raw = raw.replaceAll("<br\\s*/?>", "\n");
        raw = raw.replaceAll("<[^>]+>", "");
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
            data.recipientInfo = PrivacyMasker.maskNameAndPhone(lines[0].trim());
            return;
        }
        data.recipientInfo = PrivacyMasker.maskNameAndPhone(lines[0].trim());
        StringBuilder addr = new StringBuilder();
        for (int i = 1; i < lines.length; i++) {
            if (addr.length() > 0) addr.append('\n');
            addr.append(lines[i].trim());
        }
        data.recipientAddr = addr.toString();
    }

    private static void parseSender(WaybillData data, String text) {
        String[] lines = text.split("\\n");

        // 韵达面单寄件人格式：
        // 第1行：寄件
        // 第2行：手机号
        // 第3+行：地址

        StringBuilder info = new StringBuilder();
        StringBuilder addr = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            // 跳过"寄件"标签
            if (line.equals("寄件") || line.equals("寄")) {
                continue;
            }

            // 第一个非标签行是姓名+手机号
            if (info.length() == 0) {
                // 不在这里脱敏，让 MainController 统一处理
                info.append(line);
            } else {
                // 其余行是地址
                if (addr.length() > 0) addr.append('\n');
                addr.append(line);
            }
        }

        data.senderInfo = info.toString();
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
