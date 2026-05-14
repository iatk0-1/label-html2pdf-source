package com.label;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;

public class WaybillItem {
    private final SimpleBooleanProperty selected = new SimpleBooleanProperty(true);
    private final SimpleStringProperty waybillId = new SimpleStringProperty();
    private final SimpleStringProperty recipientName = new SimpleStringProperty();
    private final SimpleStringProperty recipientAddress = new SimpleStringProperty();

    // Raw data for PDF generation
    private WaybillData data;

    public WaybillItem(WaybillData data) {
        this.data = data;
        this.waybillId.set(data.trackingNumber != null ? data.trackingNumber : "");
        this.recipientName.set(data.recipientInfo != null ? data.recipientInfo : "");
        this.recipientAddress.set(data.recipientAddr != null ? data.recipientAddr : "");
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

    public WaybillData getData() { return data; }
}