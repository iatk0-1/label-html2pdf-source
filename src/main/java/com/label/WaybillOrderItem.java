package com.label;

/**
 * 商品明细、用户备注及管理员备注，由面单接口的 orderItems 返回。
 */
public record WaybillOrderItem(
        Long orderId,
        Long orderItemId,
        String orderNumber,
        String productInfo,
        Integer qty,
        String remark,
        String adminRemark) {

    public WaybillOrderItem(Long orderId, Long orderItemId, String orderNumber,
                            String productInfo, Integer qty, String remark) {
        this(orderId, orderItemId, orderNumber, productInfo, qty, remark, null);
    }

    public String toPdfLine() {
        String product = normalize(productInfo);
        if (product.isEmpty()) {
            product = "商品";
        }
        String orderPrefix = normalize(orderNumber);
        if (!orderPrefix.isEmpty()) {
            product = orderPrefix + " " + product;
        }
        String notes = notesForDisplay();
        return notes.isEmpty() ? product : product + "\n" + notes;
    }

    public String notesForDisplay() {
        String userNote = normalize(remark);
        String adminNote = normalize(adminRemark);
        StringBuilder result = new StringBuilder();
        if (!userNote.isEmpty()) result.append("备注：").append(userNote);
        if (!adminNote.isEmpty()) {
            if (!result.isEmpty()) result.append("  ");
            result.append("商家备注：").append(adminNote);
        }
        return result.toString();
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.replace("\r\n", "\n").replace('\r', '\n').trim();
    }
}
