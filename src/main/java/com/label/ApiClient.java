package com.label;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.ToNumberPolicy;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
/**
 * API 客户端，用于调用后端接口
 */
public class ApiClient {

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Gson gson;
    private volatile Long userId;
    private volatile String accessToken;

    public ApiClient() {
        this("http://192.168.10.217:8080");
    }

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new GsonBuilder()
                .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
                .create();
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void acceptSession(JsonObject response) throws IOException {
        if (!response.has("accessToken") || response.get("accessToken").isJsonNull()
                || response.get("accessToken").getAsString().isBlank()) {
            throw new IOException("服务未返回打印登录凭证，请确认后端和打印软件版本一致");
        }
        if (!response.has("userId") || response.get("userId").isJsonNull()) {
            throw new IOException("打印登录响应缺少用户编号");
        }
        this.userId = response.get("userId").getAsLong();
        this.accessToken = response.get("accessToken").getAsString();
    }

    private HttpRequest.Builder requestBuilder(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url));
        String token = accessToken;
        if (token != null && !token.isBlank()) builder.header("Authorization", "Bearer " + token);
        return builder;
    }

    private IOException httpFailure(String action, HttpResponse<String> response) {
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            accessToken = null;
            userId = null;
        }
        String reason = "HTTP " + response.statusCode();
        try {
            JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
            if (body.has("message")) reason = body.get("message").getAsString();
        } catch (RuntimeException ignored) {
            // 非JSON错误使用状态码提示。
        }
        return new IOException(action + "：" + reason);
    }

    /**
     * 登录
     */
    public boolean login(String username, String password) throws IOException, InterruptedException {
        accessToken = null;
        userId = null;
        String url = baseUrl + "/api/v1/printer-accounts/login";

        Map<String, String> requestBody = Map.of(
                "username", username,
                "password", password
        );

        String requestJson = gson.toJson(requestBody);
        Logger.logHttpRequest("POST", url, requestJson);

        HttpRequest request = requestBuilder(url)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Logger.logHttpResponse("POST", url, response.statusCode(), response.body());

        if (response.statusCode() == 200) {
            acceptSession(JsonParser.parseString(response.body()).getAsJsonObject());
            Logger.info("登录成功，用户ID: " + this.userId);
            return true;
        }

        Logger.error("登录失败: HTTP " + response.statusCode() + " body=" + response.body());
        if (response.statusCode() == 401) return false;
        throw httpFailure("打印登录失败", response);
    }

    public record WaybillQuery(int page, int size, String keyword, String printFilter,
                               String orderFrom, String orderTo, String waybillFrom, String waybillTo,
                               String sortBy, String sortDirection) {
        public WaybillQuery withPage(int newPage) {
            return new WaybillQuery(newPage, size, keyword, printFilter, orderFrom, orderTo,
                    waybillFrom, waybillTo, sortBy, sortDirection);
        }
        public WaybillQuery withSize(int newSize) {
            return new WaybillQuery(page, newSize, keyword, printFilter, orderFrom, orderTo,
                    waybillFrom, waybillTo, sortBy, sortDirection);
        }
    }

    public record WaybillPage(List<WaybillData> items, long total, int page, int size) {}

    public WaybillPage fetchWaybillPage(WaybillQuery query) throws IOException, InterruptedException {
        if (userId == null || accessToken == null) {
            throw new IllegalStateException("打印登录已失效，请重新登录");
        }

        StringBuilder urlBuilder = new StringBuilder(baseUrl + "/api/v1/waybills?userId=" + userId);
        addQuery(urlBuilder, "page", Integer.toString(query.page()));
        addQuery(urlBuilder, "size", Integer.toString(query.size()));
        addQuery(urlBuilder, "keyword", query.keyword());
        addQuery(urlBuilder, "printFilter", query.printFilter());
        addQuery(urlBuilder, "orderFrom", query.orderFrom());
        addQuery(urlBuilder, "orderTo", query.orderTo());
        addQuery(urlBuilder, "waybillFrom", query.waybillFrom());
        addQuery(urlBuilder, "waybillTo", query.waybillTo());
        addQuery(urlBuilder, "sortBy", query.sortBy());
        addQuery(urlBuilder, "sortDirection", query.sortDirection());
        String url = urlBuilder.toString();
        Logger.logHttpRequest("GET", url, null);

        HttpRequest request = requestBuilder(url)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Logger.logHttpResponse("GET", url, response.statusCode(), response.body());

        if (response.statusCode() == 200) {
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            List<Map<String, Object>> rawList = gson.fromJson(root.get("items"),
                    new TypeToken<List<Map<String, Object>>>(){}.getType());
            return new WaybillPage(rawList.stream().map(this::mapToWaybillData).toList(),
                    root.get("total").getAsLong(), root.get("page").getAsInt(), root.get("size").getAsInt());
        }

        Logger.error("拉取面单失败：HTTP " + response.statusCode());
        throw httpFailure("拉取面单失败", response);
    }

    private static void addQuery(StringBuilder url, String name, String value) {
        if (value != null && !value.isBlank()) {
            url.append('&').append(name).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
    }

    /**
     * 将 API 响应映射为 WaybillData
     */
    private WaybillData mapToWaybillData(Map<String, Object> map) {
        WaybillData data = new WaybillData();
        Object idObj = map.get("id");
        if (idObj instanceof Number number) data.id = number.longValue();
        else if (idObj != null) data.id = Long.parseLong(idObj.toString());
        data.trackingNumber = (String) map.get("waybillId");
        data.printHtml = (String) map.get("printHtml");
        data.expressCode = getString(map, "expressCode");  // 添加快递公司编码

        // 表格展示不脱敏，原始数据直接显示
        String recipientName = getString(map, "recipientName");
        String recipientPhone = getFirstString(map, "recipientPhone", "receiverPhone", "recipientMobile", "receiverMobile");
        data.recipientInfo = buildInfo(recipientName, recipientPhone);

        String senderName = getString(map, "senderName");
        String senderPhone = getFirstString(map, "senderPhone", "senderMobile", "senderTel", "shipperPhone", "shipperMobile");
        data.senderInfo = buildInfo(senderName, senderPhone);

        data.recipientAddr = getString(map, "recipientAddress");
        data.senderAddr = getString(map, "senderAddress");
        data.productInfo = getString(map, "productInfo");
        data.orderItems = parseOrderItems(map.get("orderItems"));
        // 读取时间字段
        data.waybillCreatedAt = getTimeString(map, "createdAt");
        data.orderCreatedAt = getTimeString(map, "orderCreatedAt");
        data.lastGeneratedAt = getFirstTimeString(map, "lastGeneratedAt", "lastPrintedAt");
        data.lastPrintedAt = getTimeString(map, "lastPrintedAt");
        data.printStatus = getString(map, "printStatus");

        data.sourceFile = "API-" + map.get("waybillId");
        return data;
    }

    private static String buildInfo(String name, String phone) {
        if (name != null && phone != null) return name + " " + phone;
        if (name != null) return name;
        if (phone != null) return phone;
        return "";
    }

    private static String getFirstString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            String value = getString(map, key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String getString(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static String getTimeString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        return value instanceof String ? ((String) value).trim() : String.valueOf(value);
    }

    private static String getFirstTimeString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            String value = getTimeString(map, key);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static List<WaybillOrderItem> parseOrderItems(Object rawValue) {
        if (!(rawValue instanceof List<?> rawItems)) {
            return new ArrayList<>();
        }

        List<WaybillOrderItem> items = new ArrayList<>();
        for (Object rawItem : rawItems) {
            if (!(rawItem instanceof Map<?, ?> item)) continue;

            items.add(new WaybillOrderItem(
                    getLong(item, "orderId"),
                    getLong(item, "orderItemId"),
                    getString(item, "adminSeqNo"),
                    getString(item, "productInfo"),
                    getInteger(item, "qty"),
                    getString(item, "remark"),
                    getString(item, "adminRemark")));
        }
        return items;
    }

    private static Long getLong(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer getInteger(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** 标记 PDF 已生成，但尚未实际打印。 */
    public void markGenerated(Long waybillDataId) throws IOException, InterruptedException {
        postWaybillStatus(waybillDataId, "mark-generated");
    }

    /** 标记运单已实际打印成功。 */
    public void markPrinted(Long waybillDataId) throws IOException, InterruptedException {
        postWaybillStatus(waybillDataId, "mark-printed");
    }

    /** 标记运单打印失败。 */
    public void markPrintFailed(Long waybillDataId) throws IOException, InterruptedException {
        postWaybillStatus(waybillDataId, "mark-print-failed");
    }

    private void postWaybillStatus(Long waybillDataId, String action)
            throws IOException, InterruptedException {
        String url = baseUrl + "/api/v1/waybills/" + waybillDataId + "/" + action;
        Logger.logHttpRequest("POST", url, null);

        HttpRequest request = requestBuilder(url)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Logger.logHttpResponse("POST", url, response.statusCode(), response.body());

        if (response.statusCode() != 204) {
            Logger.error("更新面单打印状态失败：HTTP " + response.statusCode());
            throw httpFailure("更新面单打印状态失败", response);
        }

        Logger.info("成功更新运单 " + waybillDataId + " 状态：" + action);
    }

    public Long getUserId() {
        return userId;
    }

    /**
     * 通用 GET 请求
     */
    public String get(String path) throws IOException, InterruptedException {
        String url = baseUrl + path;
        Logger.logHttpRequest("GET", url, null);

        HttpRequest request = requestBuilder(url)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Logger.logHttpResponse("GET", url, response.statusCode(), response.body());

        if (response.statusCode() == 200) {
            return response.body();
        }

        Logger.error("GET 请求失败：HTTP " + response.statusCode() + " " + response.body());
        throw httpFailure("请求失败", response);
    }

    /**
     * 通用 POST 请求
     */
    public String post(String path, String jsonBody) throws IOException, InterruptedException {
        String url = baseUrl + path;
        Logger.logHttpRequest("POST", url, jsonBody);

        HttpRequest request = requestBuilder(url)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Logger.logHttpResponse("POST", url, response.statusCode(), response.body());

        if (response.statusCode() == 200 || response.statusCode() == 201) {
            return response.body();
        }

        Logger.error("POST 请求失败：HTTP " + response.statusCode() + " " + response.body());
        throw httpFailure("请求失败", response);
    }
}
