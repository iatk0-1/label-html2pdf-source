package com.label;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
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

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            Map<String, Object> result = gson.fromJson(response.body(), new TypeToken<Map<String, Object>>(){}.getType());
            Object userIdObj = result.get("userId");
            if (userIdObj instanceof Number) {
                this.userId = ((Number) userIdObj).longValue();
            } else {
                this.userId = Long.parseLong(String.valueOf(userIdObj));
            }
            return true;
        }

        System.err.println("登录失败: HTTP " + response.statusCode() + " body=" + response.body());
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

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            List<Map<String, Object>> rawList = gson.fromJson(response.body(), new TypeToken<List<Map<String, Object>>>(){}.getType());
            return rawList.stream().map(this::mapToWaybillData).toList();
        }

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

        // 对收件人姓名和手机号进行脱敏
        String recipientName = (String) map.get("recipientName");
        String recipientPhone = (String) map.get("recipientPhone");
        if (recipientName != null && recipientPhone != null) {
            data.recipientInfo = PrivacyMasker.maskName(recipientName) + " " + PrivacyMasker.maskPhone(recipientPhone);
        } else if (recipientName != null) {
            data.recipientInfo = PrivacyMasker.maskName(recipientName);
        } else if (recipientPhone != null) {
            data.recipientInfo = PrivacyMasker.maskPhone(recipientPhone);
        }

        // 对发件人手机号脱敏（姓名不脱敏）
        String senderName = (String) map.get("senderName");
        String senderPhone = (String) map.get("senderPhone");
        if (senderName != null && senderPhone != null) {
            data.senderInfo = senderName + " " + PrivacyMasker.maskPhone(senderPhone);
        } else if (senderName != null) {
            data.senderInfo = senderName;
        } else if (senderPhone != null) {
            data.senderInfo = PrivacyMasker.maskPhone(senderPhone);
        }

        data.recipientAddr = (String) map.get("recipientAddress");
        data.senderAddr = (String) map.get("senderAddress");
        data.productInfo = (String) map.get("productInfo");
        // 读取打印时间
        Object lastPrinted = map.get("lastPrintedAt");
        data.lastPrintedAt = lastPrinted != null ? String.valueOf(lastPrinted) : null;

        data.sourceFile = "API-" + map.get("waybillId");
        return data;
    }

    /**
     * 标记运单已打印
     */
    public void markPrinted(Long waybillDataId) throws IOException, InterruptedException {
        String url = baseUrl + "/api/v1/waybills/" + waybillDataId + "/mark-printed";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 204) {
            throw new IOException("标记已打印失败：HTTP " + response.statusCode());
        }
    }

    public Long getUserId() {
        return userId;
    }
}
