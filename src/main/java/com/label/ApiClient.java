package com.label;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
    private Long userId;

    public ApiClient() {
        this("http://192.168.10.217:8080");
    }

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * 登录
     */
    public boolean login(String username, String password) throws IOException, InterruptedException {
        String url = baseUrl + "/api/v1/printer-accounts/login";

        Map<String, String> requestBody = Map.of(
                "username", username,
                "password", password
        );

        String requestJson = gson.toJson(requestBody);
        Logger.logHttpRequest("POST", url, requestJson);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Logger.logHttpResponse("POST", url, response.statusCode(), response.body());

        if (response.statusCode() == 200) {
            Map<String, Object> result = gson.fromJson(response.body(), new TypeToken<Map<String, Object>>(){}.getType());
            Object userIdObj = result.get("userId");
            if (userIdObj instanceof Number) {
                this.userId = ((Number) userIdObj).longValue();
            } else {
                this.userId = Long.parseLong(String.valueOf(userIdObj));
            }
            Logger.info("登录成功，用户ID: " + this.userId);
            return true;
        }

        Logger.error("登录失败: HTTP " + response.statusCode() + " body=" + response.body());
        return false;
    }

    /**
     * 拉取面单数据
     */
    public List<WaybillData> fetchWaybills() throws IOException, InterruptedException {
        if (userId == null) {
            throw new IllegalStateException("未登录");
        }

        String url = baseUrl + "/api/v1/waybills?userId=" + userId;
        Logger.logHttpRequest("GET", url, null);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Logger.logHttpResponse("GET", url, response.statusCode(), response.body());

        if (response.statusCode() == 200) {
            List<Map<String, Object>> rawList = gson.fromJson(response.body(), new TypeToken<List<Map<String, Object>>>(){}.getType());
            Logger.info("成功拉取 " + rawList.size() + " 条面单数据");
            return rawList.stream().map(this::mapToWaybillData).toList();
        }

        Logger.error("拉取面单失败：HTTP " + response.statusCode());
        throw new IOException("拉取面单失败：HTTP " + response.statusCode());
    }

    /**
     * 将 API 响应映射为 WaybillData
     */
    private WaybillData mapToWaybillData(Map<String, Object> map) {
        WaybillData data = new WaybillData();
        Object idObj = map.get("id");
        if (idObj != null) data.id = Long.parseLong(idObj.toString());
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
        data.lastPrintedAt = getTimeString(map, "lastPrintedAt");

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
                    getString(item, "remark")));
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

    /**
     * 标记运单已打印
     */
    public void markPrinted(Long waybillDataId) throws IOException, InterruptedException {
        String url = baseUrl + "/api/v1/waybills/" + waybillDataId + "/mark-printed";
        Logger.logHttpRequest("POST", url, null);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Logger.logHttpResponse("POST", url, response.statusCode(), response.body());

        if (response.statusCode() != 204) {
            Logger.error("标记已打印失败：HTTP " + response.statusCode());
            throw new IOException("标记已打印失败：HTTP " + response.statusCode());
        }

        Logger.info("成功标记运单 " + waybillDataId + " 为已打印");
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

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Logger.logHttpResponse("GET", url, response.statusCode(), response.body());

        if (response.statusCode() == 200) {
            return response.body();
        }

        Logger.error("GET 请求失败：HTTP " + response.statusCode() + " " + response.body());
        throw new IOException("GET 请求失败：HTTP " + response.statusCode() + " " + response.body());
    }

    /**
     * 通用 POST 请求
     */
    public String post(String path, String jsonBody) throws IOException, InterruptedException {
        String url = baseUrl + path;
        Logger.logHttpRequest("POST", url, jsonBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Logger.logHttpResponse("POST", url, response.statusCode(), response.body());

        if (response.statusCode() == 200 || response.statusCode() == 201) {
            return response.body();
        }

        Logger.error("POST 请求失败：HTTP " + response.statusCode() + " " + response.body());
        throw new IOException("POST 请求失败：HTTP " + response.statusCode() + " " + response.body());
    }
}
