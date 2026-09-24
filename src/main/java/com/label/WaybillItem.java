package com.label;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;

import java.io.File;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;

public class WaybillItem {
    private static final DateTimeFormatter SPACE_DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .toFormatter();

    private final SimpleBooleanProperty selected = new SimpleBooleanProperty(false);
    private final SimpleStringProperty waybillId = new SimpleStringProperty();
    private final SimpleStringProperty recipientName = new SimpleStringProperty();
    private final SimpleStringProperty recipientAddress = new SimpleStringProperty();
    private final SimpleStringProperty orderNumbers = new SimpleStringProperty();
    private final SimpleStringProperty remarks = new SimpleStringProperty();
    private final SimpleStringProperty waybillCreatedTime = new SimpleStringProperty();   // 面单创建时间
    private final SimpleStringProperty orderCreatedTime = new SimpleStringProperty();     // 订单创建时间
    private final SimpleStringProperty lastGenTime = new SimpleStringProperty();          // 上次生成PDF
    private final SimpleStringProperty status = new SimpleStringProperty();
    private final SimpleStringProperty pdfStatus = new SimpleStringProperty();
    private final SimpleStringProperty printResult = new SimpleStringProperty();

    private WaybillData data;
    // Raw time strings for filtering (keep original ISO format for parsing)
    private String rawWaybillCreatedAt;
    private String rawOrderCreatedAt;
    private String rawLastGeneratedAt;
    private String rawLastPrintedAt;
    private File pdfFile;

    public WaybillItem(WaybillData data) {
        this.data = data;
        this.waybillId.set(data.trackingNumber != null ? data.trackingNumber : "");
        this.recipientName.set(data.recipientInfo != null ? data.recipientInfo : "");
        this.recipientAddress.set(data.recipientAddr != null ? data.recipientAddr : "");
        this.orderNumbers.set(data.getOrderNumbersForDisplay());
        this.remarks.set(data.getRemarksForDisplay());
        this.rawWaybillCreatedAt = data.waybillCreatedAt;
        this.rawOrderCreatedAt = data.orderCreatedAt;
        this.rawLastGeneratedAt = data.lastGeneratedAt;
        this.rawLastPrintedAt = data.lastPrintedAt;
        this.waybillCreatedTime.set(formatTime(data.waybillCreatedAt));
        this.orderCreatedTime.set(formatTime(data.orderCreatedAt));
        this.lastGenTime.set(formatTime(data.lastGeneratedAt));
        this.status.set(initialStatus(data));
        boolean generated = data.lastGeneratedAt != null && !data.lastGeneratedAt.isBlank()
                || "GENERATED".equalsIgnoreCase(data.printStatus)
                || "PRINTED".equalsIgnoreCase(data.printStatus)
                || "FAILED".equalsIgnoreCase(data.printStatus);
        this.pdfStatus.set(generated ? "已生成" : "未生成");
        this.printResult.set("PRINTED".equalsIgnoreCase(data.printStatus) ? "已打印"
                : "FAILED".equalsIgnoreCase(data.printStatus) ? "打印失败" : "未打印");
    }

    private static String initialStatus(WaybillData data) {
        return switch (data.printStatus == null ? "" : data.printStatus.trim().toUpperCase()) {
            case "PRINTED" -> "已打印";
            case "FAILED" -> "打印失败";
            case "GENERATED" -> "已生成";
            default -> data.lastGeneratedAt == null || data.lastGeneratedAt.isBlank() ? "未生成" : "已生成";
        };
    }

    private static String formatTime(String raw) {
        if (raw == null || raw.isEmpty()) return "-";
        if (raw.length() >= 16 && raw.contains("T")) {
            return raw.substring(5, 16).replace("T", " ");
        }
        return raw;
    }

    public LocalDateTime getWaybillCreatedDateTime() {
        return parseDateTime(rawWaybillCreatedAt);
    }

    public LocalDateTime getOrderCreatedDateTime() {
        return parseDateTime(rawOrderCreatedAt);
    }

    private static LocalDateTime parseDateTime(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;

        String value = raw.trim();

        // Gson represents numeric JSON values as numbers. Accept both epoch seconds
        // and epoch milliseconds in case the API uses a numeric timestamp.
        if (value.matches("-?\\d+(\\.\\d+)?")) {
            try {
                BigDecimal number = new BigDecimal(value);
                long timestamp = number.longValueExact();
                Instant instant = value.replace("-", "").length() >= 12
                        ? Instant.ofEpochMilli(timestamp)
                        : Instant.ofEpochSecond(timestamp);
                return instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
            } catch (ArithmeticException | DateTimeParseException ignored) {
                // Fall through to the textual formats below.
            }
        }

        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // Continue with offset and legacy server formats.
        }

        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Continue with the space-separated format.
        }

        try {
            return LocalDateTime.parse(value, SPACE_DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // Continue with date-only values.
        }

        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    public boolean isSelected() { return selected.get(); }
    public void setSelected(boolean v) { selected.set(v); }
    public SimpleBooleanProperty selectedProperty() { return selected; }

    public String getWaybillId() { return waybillId.get(); }
    public SimpleStringProperty waybillIdProperty() { return waybillId; }

    public String getRecipientName() { return recipientName.get(); }
    public SimpleStringProperty recipientNameProperty() { return recipientName; }

    public String getRecipientAddress() { return recipientAddress.get(); }
    public SimpleStringProperty recipientAddressProperty() { return recipientAddress; }

    public String getOrderNumbers() { return orderNumbers.get(); }
    public SimpleStringProperty orderNumbersProperty() { return orderNumbers; }

    public String getRemarks() { return remarks.get(); }
    public SimpleStringProperty remarksProperty() { return remarks; }

    public String getWaybillCreatedTime() { return waybillCreatedTime.get(); }
    public SimpleStringProperty waybillCreatedTimeProperty() { return waybillCreatedTime; }

    public String getOrderCreatedTime() { return orderCreatedTime.get(); }
    public SimpleStringProperty orderCreatedTimeProperty() { return orderCreatedTime; }

    public String getLastGenTime() { return lastGenTime.get(); }
    public SimpleStringProperty lastGenTimeProperty() { return lastGenTime; }

    public String getStatus() { return status.get(); }
    public SimpleStringProperty statusProperty() { return status; }
    public SimpleStringProperty pdfStatusProperty() { return pdfStatus; }
    public SimpleStringProperty printResultProperty() { return printResult; }
    public void setPdfStatus(String value) { pdfStatus.set(value); }
    public void setPrintResult(String value) { printResult.set(value); }

    public File getPdfFile() { return pdfFile; }

    public void attachPdfFile(File file) {
        this.pdfFile = file;
    }

    public void markGenerated(File file) {
        this.pdfFile = file;
        String now = java.time.LocalDateTime.now().toString();
        this.rawLastGeneratedAt = now;
        this.lastGenTime.set(formatTime(now));
        this.status.set("已生成");
        this.pdfStatus.set("生成成功");
    }

    public void setStatus(String value) {
        this.status.set(value == null || value.isBlank() ? "未生成" : value);
    }

    public void markPrinted() {
        String now = java.time.LocalDateTime.now().toString();
        this.rawLastPrintedAt = now;
        this.status.set("已打印");
        this.printResult.set("打印成功");
    }

    public void markPrintFailed() {
        this.status.set("打印失败");
        this.printResult.set("打印失败");
    }

    public WaybillData getData() { return data; }
    public Long getWaybillDataId() { return data.id; }
    public String getExpressCode() { return data.expressCode; }
}
