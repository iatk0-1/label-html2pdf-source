package com.label;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 按模板区域读取可见分拣文字，不限制字符、长度或段数。 */
final class SortingCodeExtractor {
    private SortingCodeExtractor() {}

    static String read(Document document, double fromTop, double toTop, boolean excludeHeaderMetadata) {
        List<Element> blocks = new ArrayList<>();
        for (Element element : document.select("div.item.text")) {
            double top = position(element, "top");
            String text = text(element);
            if (top < fromTop || top >= toTop || text.isEmpty() || isHidden(element)) continue;
            // 韵达的分拣区与页头共用上方区域，日期、时间和代收标签单独处理。
            if (excludeHeaderMetadata && (text.matches("\\d{4}[/-]\\d{2}[/-]\\d{2}.*")
                    || text.matches("\\d{2}:\\d{2}:\\d{2}")
                    || text.contains("货到付款") || text.contains("代收"))) continue;
            blocks.add(element);
        }
        blocks.sort(Comparator.comparingDouble((Element element) -> position(element, "top"))
                .thenComparingDouble(element -> position(element, "left")));
        String result = blocks.stream().map(SortingCodeExtractor::text)
                .collect(java.util.stream.Collectors.joining(" "));
        return result.isEmpty() ? null : result;
    }

    static boolean isHidden(Element element) {
        for (Element current = element; current != null; current = current.parent()) {
            if (current.hasAttr("hidden") || current.attr("style").matches(
                    "(?is).*(?:\\bdisplay\\s*:\\s*none\\b|\\bvisibility\\s*:\\s*(?:hidden|collapse)\\b).*")) {
                return true;
            }
        }
        return false;
    }

    private static String text(Element element) {
        // HTML 源码缩进和空格实体按页面显示折叠，码本身的字符保持不变。
        return element.text().replaceAll("(?U)\\s+", " ").trim();
    }

    private static double position(Element element, String property) {
        Matcher matcher = Pattern.compile("(?i)(?:^|;)\\s*" + property + "\\s*:\\s*([\\d.]+)\\s*px")
                .matcher(element.attr("style"));
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : 0;
    }
}
