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

    public byte[] logoImage;
    public byte[] barcodeImage;
    public byte[] bottomBarcodeImage;  // second barcode at bottom
    public byte[] qrCodeImage;
    public byte[] icon1Image;
    public byte[] icon2Image;  // right-side image in same row

    public String sourceFile;
    public String printHtml;  // 微信返回的面单HTML（base64编码）

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

    public static class LineInfo {
        public double left, top, width, height;
        public int type; // 0=hline, 1=vline
    }
}
