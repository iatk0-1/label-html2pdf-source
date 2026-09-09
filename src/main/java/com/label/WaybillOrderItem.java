package com.label;

/**
 * 商品明细及其下单备注，由面单接口的 orderItems 返回。
 */
public record WaybillOrderItem(
        Long orderId,
        Long orderItemId,
        String orderNumber,
        String productInfo,
        Integer qty,
        String remark) {

    public String toPdfLine() {
        String product = normalize(productInfo);
        String note = normalize(remark);
        if (product.isEmpty()) {
            product = "商品";
        }
        String orderPrefix = normalize(orderNumber);
        if (!orderPrefix.isEmpty()) {
            product = orderPrefix + " " + product;
        }
        if (note.isEmpty()) {
            return product;
        }
        return product + " 备注：" + note;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.replace("\r\n", "\n").replace('\r', '\n').trim();
    }
}
