package com.label;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;

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

    private final SimpleBooleanProperty selected = new SimpleBooleanProperty(true);
    private final SimpleStringProperty waybillId = new SimpleStringProperty();
    private final SimpleStringProperty recipientName = new SimpleStringProperty();
    private final SimpleStringProperty recipientAddress = new SimpleStringProperty();
    private final SimpleStringProperty waybillCreatedTime = new SimpleStringProperty();   // 面单创建时间
    private final SimpleStringProperty orderCreatedTime = new SimpleStringProperty();     // 订单创建时间
    private final SimpleStringProperty lastGenTime = new SimpleStringProperty();          // 上次生成PDF

    private WaybillData data;
    // Raw time strings for filtering (keep original ISO format for parsing)
    private String rawWaybillCreatedAt;
    private String rawOrderCreatedAt;
    private String rawLastPrintedAt;

    public WaybillItem(WaybillData data) {
        this.data = data;
        this.waybillId.set(data.trackingNumber != null ? data.trackingNumber : "");
        this.recipientName.set(data.recipientInfo != null ? data.recipientInfo : "");
        this.recipientAddress.set(data.recipientAddr != null ? data.recipientAddr : "");
        this.rawWaybillCreatedAt = data.waybillCreatedAt;
        this.rawOrderCreatedAt = data.orderCreatedAt;
        this.rawLastPrintedAt = data.lastPrintedAt;
        this.waybillCreatedTime.set(formatTime(data.waybillCreatedAt));
        this.orderCreatedTime.set(formatTime(data.orderCreatedAt));
        this.lastGenTime.set(formatTime(data.lastPrintedAt));
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

    public String getWaybillCreatedTime() { return waybillCreatedTime.get(); }
    public SimpleStringProperty waybillCreatedTimeProperty() { return waybillCreatedTime; }

    public String getOrderCreatedTime() { return orderCreatedTime.get(); }
    public SimpleStringProperty orderCreatedTimeProperty() { return orderCreatedTime; }

    public String getLastGenTime() { return lastGenTime.get(); }
    public SimpleStringProperty lastGenTimeProperty() { return lastGenTime; }

    public void markPrinted() {
        String now = java.time.LocalDateTime.now().toString();
        this.rawLastPrintedAt = now;
        this.lastGenTime.set(formatTime(now));
    }

    public WaybillData getData() { return data; }
    public Long getWaybillDataId() { return data.id; }
    public String getExpressCode() { return data.expressCode; }
}
