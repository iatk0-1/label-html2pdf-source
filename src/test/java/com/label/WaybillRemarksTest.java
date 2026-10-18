package com.label;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WaybillRemarksTest {
    @Test void printsUserNoteBeforeMerchantNoteAndOmitsEmptyLabels() {
        assertEquals("A001 商品 * 2 备注：用户要求  商家备注：发货提醒",
                item(" 用户要求 ", " 发货提醒 ").toPdfLine());
        assertEquals("A001 商品 * 2 商家备注：发货提醒", item(null, "发货提醒").toPdfLine());
        assertEquals("A001 商品 * 2 备注：用户要求", item("用户要求", "  ").toPdfLine());
        assertEquals("A001 商品 * 2", item(null, null).toPdfLine());
        WaybillData data = new WaybillData();
        data.orderItems = List.of(item("用户要求", "发货提醒"));
        assertEquals("备注：用户要求  商家备注：发货提醒", data.getRemarksForDisplay());
        assertEquals(data.orderItems.get(0).toPdfLine(), data.getProductInfoForPdf());
        assertTrue(data.getProductLinesForPdf().get(0).bold());
    }

    @Test void readsMerchantRemarkFromWaybillApiAndAcceptsOldResponses() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/waybills", exchange -> {
            byte[] body = """
                    {"items":[{"id":1,"waybillId":"TEST","orderItems":[
                    {"orderId":1,"orderItemId":2,"adminSeqNo":"A001","productInfo":"商品 * 2",
                    "qty":2,"remark":"用户要求","adminRemark":"发货提醒"},
                    {"orderId":1,"orderItemId":3,"productInfo":"旧商品","qty":1,"remark":"旧备注"}
                    ]}],"total":1,"page":0,"size":20}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (var stream = exchange.getResponseBody()) { stream.write(body); }
        });
        server.start();
        try {
            ApiClient client = new ApiClient("http://127.0.0.1:" + server.getAddress().getPort());
            client.setUserId(1L);
            var page = client.fetchWaybillPage(new ApiClient.WaybillQuery(0,20,null,"ALL",null,null,null,null,
                    "waybillCreatedAt","desc"));
            var items = page.items().get(0).orderItems;
            assertEquals("发货提醒", items.get(0).adminRemark());
            assertEquals("A001 商品 * 2 备注：用户要求  商家备注：发货提醒", items.get(0).toPdfLine());
            assertNull(items.get(1).adminRemark());
            assertEquals("旧商品 备注：旧备注", items.get(1).toPdfLine());
        } finally { server.stop(0); }
    }

    private static WaybillOrderItem item(String user, String admin) {
        return new WaybillOrderItem(1L,2L,"A001","商品 * 2",2,user,admin);
    }
}
