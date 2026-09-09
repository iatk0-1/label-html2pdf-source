package com.label;

import java.util.ArrayList;
import java.util.List;

public class WaybillData {
    public String date;
    public String trackingNumber;
    public String sortingCode;
    public String transferStation;
    public String recipientInfo;    // name + phone
    public String recipientAddr;    // full address lines
    public String senderInfo;       // name + phone + province
    public String senderAddr;       // city + district + detail address
    public String codLabel;         // "货到付款" or similar
    public String verifiedStamp;    // "已验视"
    public String productInfo;      // 商品信息（格式：商品名称 * 数量）
    public List<WaybillOrderItem> orderItems = new ArrayList<>(); // 商品明细及备注
    public String expressCode;      // 快递公司编码（如：ZTO、YUNDA）

    public byte[] logoImage;
    public byte[] barcodeImage;
    public byte[] bottomBarcodeImage;  // second barcode at bottom
    public byte[] qrCodeImage;
    public byte[] icon1Image;
    public byte[] icon2Image;  // right-side image in same row

    public Long id;               // 数据库ID（用于标记已打印）
    public String sourceFile;
    public String printHtml;  // 微信返回的面单HTML（base64编码）
    public String waybillCreatedAt;  // 面单创建时间（null 表示无）
    public String orderCreatedAt;    // 订单创建时间（null 表示无）
    public String lastPrintedAt;     // 上次生成PDF时间（null 表示未打印）

    public static class ImageInfo {
        public byte[] data;
        public double left;
        public double top;
        public double width;
        public double height;
    }
    public List<ImageInfo> images = new ArrayList<>();

    public List<LineInfo> hlines = new ArrayList<>();
    public List<LineInfo> vlines = new ArrayList<>();

    /**
     * 生成 PDF 使用的商品信息。新接口优先使用带备注的商品明细，老数据回退到顶层商品信息。
     */
    public String getProductInfoForPdf() {
        if (orderItems != null && !orderItems.isEmpty()) {
            StringBuilder result = new StringBuilder();
            for (WaybillOrderItem item : orderItems) {
                if (item == null) continue;
                String line = item.toPdfLine();
                if (line.isEmpty()) continue;
                if (result.length() > 0) result.append('\n');
                result.append(line);
            }
            if (result.length() > 0) return result.toString();
        }
        return productInfo == null ? "" : productInfo.trim();
    }

    public String getOrderNumbersForDisplay() {
        if (orderItems == null || orderItems.isEmpty()) return "";
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (WaybillOrderItem item : orderItems) {
            if (item == null || item.orderNumber() == null) continue;
            String value = item.orderNumber().trim();
            if (!value.isEmpty()) seen.add(value);
        }
        return String.join("、", seen);
    }

    public String getRemarksForDisplay() {
        if (orderItems == null || orderItems.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        for (WaybillOrderItem item : orderItems) {
            if (item == null || item.remark() == null) continue;
            String value = item.remark().trim();
            if (value.isEmpty()) continue;
            if (result.length() > 0) result.append("；");
            result.append(value);
        }
        return result.toString();
    }

    public static class LineInfo {
        public double left, top, width, height;
        public int type; // 0=hline, 1=vline
    }
}
