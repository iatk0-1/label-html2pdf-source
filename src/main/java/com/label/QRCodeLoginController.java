package com.label;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 扫码登录控制器
 */
public class QRCodeLoginController {

    @FXML
    private ImageView qrCodeImageView;

    @FXML
    private Label statusLabel;

    @FXML
    private Label countdownLabel;

    @FXML
    private Button refreshButton;

    @FXML
    private Button backButton;

    private ApiClient apiClient;
    private final Gson gson = new Gson();
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pollingTask;
    private String currentTicket;
    private long qrCodeExpiresAt;
    private ScheduledFuture<?> countdownTask;
    private String serverUrl;

    @FXML
    public void initialize() {
        // 初始化时不生成二维码，等待 setServerUrl 调用
    }

    /**
     * 设置服务器地址并生成二维码
     */
    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
        this.apiClient = new ApiClient(serverUrl);
        generateQRCode();
    }

    /**
     * 生成二维码
     */
    private void generateQRCode() {
        try {
            statusLabel.setText("正在生成二维码...");
            refreshButton.setDisable(true);

            // 检查服务器地址
            if (apiClient == null) {
                throw new IllegalStateException("API 客户端未初始化");
            }

            // 调用后端 API 生成二维码
            String response = apiClient.post("/api/v1/printer-accounts/qrcode/generate", "{}");
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);

            currentTicket = jsonResponse.get("ticket").getAsString();
            String content = jsonResponse.get("content").getAsString();
            long expiresIn = jsonResponse.get("expiresIn").getAsLong();

            // 计算过期时间
            qrCodeExpiresAt = System.currentTimeMillis() + (expiresIn * 1000);

            // 生成二维码图片
            Image qrCodeImage = QRCodeGenerator.generateQRCodeImage(content, 250, 250);
            qrCodeImageView.setImage(qrCodeImage);

            statusLabel.setText("请使用微信小程序扫码");
            refreshButton.setDisable(false);

            // 启动轮询
            startPolling();

            // 启动倒计时
            startCountdown();

        } catch (Exception e) {
            e.printStackTrace();
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            statusLabel.setText("生成二维码失败：" + errorMsg);
            refreshButton.setDisable(false);
            showError("生成二维码失败",
                "错误信息：" + errorMsg + "\n\n请检查：\n1. 服务器地址是否正确\n2. 后端服务是否启动\n3. 网络连接是否正常");
        }
    }

    /**
     * 启动轮询检查二维码状态
     */
    private void startPolling() {
        // 停止之前的轮询
        stopPolling();

        scheduler = Executors.newScheduledThreadPool(1);
        pollingTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                checkQRCodeStatus();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * 检查二维码状态
     */
    private void checkQRCodeStatus() {
        try {
            String response = apiClient.get("/api/v1/printer-accounts/qrcode/check/" + currentTicket);
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);

            String status = jsonResponse.get("status").getAsString();
            String message = jsonResponse.get("message").getAsString();

            Platform.runLater(() -> {
                switch (status) {
                    case "pending":
                        statusLabel.setText("等待扫码授权...");
                        break;

                    case "authorized":
                        // 授权成功
                        stopPolling();
                        stopCountdown();
                        statusLabel.setText("授权成功！");

                        Long userId = jsonResponse.get("userId").getAsLong();
                        String username = jsonResponse.get("username").getAsString();

                        // 登录成功，跳转到主界面
                        onLoginSuccess(userId, username);
                        break;

                    case "expired":
                        // 二维码过期
                        stopPolling();
                        stopCountdown();
                        statusLabel.setText(message);
                        showInfo("二维码已过期", "请点击刷新按钮重新获取二维码");
                        break;

                    default:
                        statusLabel.setText("未知状态：" + status);
                        break;
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 启动倒计时
     */
    private void startCountdown() {
        stopCountdown();

        ScheduledExecutorService countdownScheduler = Executors.newScheduledThreadPool(1);
        countdownTask = countdownScheduler.scheduleAtFixedRate(() -> {
            long remainingSeconds = (qrCodeExpiresAt - System.currentTimeMillis()) / 1000;

            if (remainingSeconds <= 0) {
                Platform.runLater(() -> countdownLabel.setText("已过期"));
                stopCountdown();
            } else {
                long minutes = remainingSeconds / 60;
                long seconds = remainingSeconds % 60;
                Platform.runLater(() ->
                    countdownLabel.setText(String.format("有效期：%d分%d秒", minutes, seconds))
                );
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * 停止轮询
     */
    private void stopPolling() {
        if (pollingTask != null && !pollingTask.isCancelled()) {
            pollingTask.cancel(true);
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }

    /**
     * 停止倒计时
     */
    private void stopCountdown() {
        if (countdownTask != null && !countdownTask.isCancelled()) {
            countdownTask.cancel(true);
        }
    }

    /**
     * 登录成功回调
     */
    private void onLoginSuccess(Long userId, String username) {
        try {
            // 保存登录信息
            LoginController.saveLoginInfo(userId, username);

            // 跳转到主界面
            MainApp.showMainWindow(userId);

        } catch (Exception e) {
            e.printStackTrace();
            showError("登录失败", e.getMessage());
        }
    }

    /**
     * 刷新二维码
     */
    @FXML
    private void handleRefresh() {
        stopPolling();
        stopCountdown();
        generateQRCode();
    }

    /**
     * 返回账号登录
     */
    @FXML
    private void handleBack() {
        stopPolling();
        stopCountdown();
        MainApp.showLoginWindow();
    }

    /**
     * 显示错误对话框
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 显示信息对话框
     */
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 清理资源
     */
    public void cleanup() {
        stopPolling();
        stopCountdown();
    }
}
