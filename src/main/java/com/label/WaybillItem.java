package com.label;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;

public class WaybillItem {
    private final SimpleBooleanProperty selected = new SimpleBooleanProperty(true);
    private final SimpleStringProperty waybillId = new SimpleStringProperty();
    private final SimpleStringProperty recipientName = new SimpleStringProperty();
    private final SimpleStringProperty recipientAddress = new SimpleStringProperty();
    private final SimpleStringProperty lastGenTime = new SimpleStringProperty();

    // Raw data for PDF generation
    private WaybillData data;

    public WaybillItem(WaybillData data) {
        this.data = data;
        this.waybillId.set(data.trackingNumber != null ? data.trackingNumber : "");
        this.recipientName.set(data.recipientInfo != null ? data.recipientInfo : "");
        this.recipientAddress.set(data.recipientAddr != null ? data.recipientAddr : "");
        this.lastGenTime.set(formatTime(data.lastPrintedAt));
    }

    private static String formatTime(String raw) {
        if (raw == null || raw.isEmpty()) return "-";
        // 后端返回 LocalDateTime 格式如 "2026-05-14T16:06:46"（无时区）
        if (raw.length() >= 16 && raw.contains("T")) {
            return raw.substring(5, 16).replace("T", " ");
        }
        return raw;
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

    public String getLastGenTime() { return lastGenTime.get(); }
    public SimpleStringProperty lastGenTimeProperty() { return lastGenTime; }

    public void markPrinted() {
        this.lastGenTime.set(formatTime(java.time.LocalDateTime.now().toString()));
    }

    public WaybillData getData() { return data; }
    public Long getWaybillDataId() { return data.id; }
}