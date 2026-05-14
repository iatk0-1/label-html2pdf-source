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
        data.trackingNumber = (String) map.get("waybillId");
        data.printHtml = (String) map.get("printHtml");
        data.recipientInfo = (String) map.get("recipientName");
        data.recipientAddr = (String) map.get("recipientAddress");
        data.sourceFile = "API-" + map.get("waybillId");
        return data;
    }

    public Long getUserId() {
        return userId;
    }
}
