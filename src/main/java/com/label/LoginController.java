package com.label;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

public class LoginController {

    private static final Preferences PREFS = Preferences.userNodeForPackage(LoginController.class);
    private static final String KEY_SERVER = "server";
    private static final String KEY_USER = "username";
    private static final String KEY_PASS = "password";
    private static final String KEY_REMEMBER = "remember";

    // 账号密码登录区域
    @FXML private VBox accountLoginArea;
    @FXML private HBox serverRow;
    @FXML private TextField serverField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private CheckBox rememberPwdCheck;
    @FXML private Label statusLabel;
    @FXML private Button loginBtn;
    @FXML private Button qrCodeLoginBtn;

    // 扫码登录区域
    @FXML private VBox qrCodeLoginArea;
    @FXML private ImageView qrCodeImageView;
    @FXML private Label qrStatusLabel;
    @FXML private Label qrCountdownLabel;
    @FXML private Button refreshQRBtn;

    private Stage stage;
    private boolean loginSuccess = false;
    private String serverUrl;
    private String username;
    private String password;
    private ApiClient apiClient;
    private Long userId;

    // 扫码登录相关
    private final Gson gson = new Gson();
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pollingTask;
    private String currentTicket;
    private long qrCodeExpiresAt;
    private ScheduledFuture<?> countdownTask;

    @FXML
    public void initialize() {
        serverField.setText(PREFS.get(KEY_SERVER, "http://192.168.10.217:8080"));
        usernameField.setText(PREFS.get(KEY_USER, ""));
        passwordField.setText(PREFS.get(KEY_PASS, ""));
        rememberPwdCheck.setSelected(PREFS.getBoolean(KEY_REMEMBER, false));
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    private void onSettings() {
        boolean show = !serverRow.isVisible();
        serverRow.setVisible(show);
        serverRow.setManaged(show);
    }

    @FXML
    private void onLogin() {
        String server = serverField.getText().trim();
        String user = usernameField.getText().trim();
        String pass = passwordField.getText().trim();

        if (server.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            statusLabel.setText("请填写完整信息");
            return;
        }

        loginBtn.setDisable(true);
        statusLabel.setText("登录中...");
        statusLabel.setStyle("-fx-text-fill: #4a6cf7;");

        new Thread(() -> {
            try {
                ApiClient client = new ApiClient(server);
                boolean ok = client.login(user, pass);

                javafx.application.Platform.runLater(() -> {
                    if (ok) {
                        // Save preferences
                        PREFS.put(KEY_SERVER, server);
                        PREFS.put(KEY_USER, user);
                        if (rememberPwdCheck.isSelected()) {
                            PREFS.put(KEY_PASS, pass);
                            PREFS.putBoolean(KEY_REMEMBER, true);
                        } else {
                            PREFS.remove(KEY_PASS);
                            PREFS.putBoolean(KEY_REMEMBER, false);
                        }

                        this.apiClient = client;
                        this.serverUrl = server;
                        this.username = user;
                        this.password = pass;
                        this.loginSuccess = true;
                        stage.close();
                    } else {
                        statusLabel.setText("用户名或密码错误");
                        statusLabel.setStyle("-fx-text-fill: #e74c3c;");
                        loginBtn.setDisable(false);
                    }
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("连接失败: " + ex.getMessage());
                    statusLabel.setStyle("-fx-text-fill: #e74c3c;");
                    loginBtn.setDisable(false);
                });
            }
        }).start();
    }

    @FXML
    private void onCancel() {
        stopQRCodePolling();
        stage.close();
    }

    /**
     * 切换到扫码登录
     */
    @FXML
    private void switchToQRCodeLogin() {
        String server = serverField.getText().trim();
        if (server.isEmpty()) {
            statusLabel.setText("请先设置服务器地址");
            return;
        }

        // 保存服务器地址
        PREFS.put(KEY_SERVER, server);

        // 切换界面
        accountLoginArea.setVisible(false);
        accountLoginArea.setManaged(false);
        qrCodeLoginArea.setVisible(true);
        qrCodeLoginArea.setManaged(true);

        // 生成二维码
        generateQRCode(server);
    }

    /**
     * 切换到账号密码登录
     */
    @FXML
    private void switchToAccountLogin() {
        // 停止轮询
        stopQRCodePolling();

        // 切换界面
        qrCodeLoginArea.setVisible(false);
        qrCodeLoginArea.setManaged(false);
        accountLoginArea.setVisible(true);
        accountLoginArea.setManaged(true);
    }

    /**
     * 生成二维码
     */
    private void generateQRCode(String serverUrl) {
        qrStatusLabel.setText("正在生成二维码...");
        refreshQRBtn.setDisable(true);

        new Thread(() -> {
            try {
                ApiClient client = new ApiClient(serverUrl);
                String response = client.post("/api/v1/printer-accounts/qrcode/generate", "{}");
                JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);

                currentTicket = jsonResponse.get("ticket").getAsString();
                String content = jsonResponse.get("content").getAsString();
                long expiresIn = jsonResponse.get("expiresIn").getAsLong();

                // 计算过期时间
                qrCodeExpiresAt = System.currentTimeMillis() + (expiresIn * 1000);

                // 生成二维码图片
                Image qrCodeImage = QRCodeGenerator.generateQRCodeImage(content, 200, 200);

                Platform.runLater(() -> {
                    qrCodeImageView.setImage(qrCodeImage);
                    qrStatusLabel.setText("请使用微信小程序扫码");
                    refreshQRBtn.setDisable(false);

                    // 启动轮询
                    startQRCodePolling(serverUrl);

                    // 启动倒计时
                    startCountdown();
                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    qrStatusLabel.setText("生成失败：" + errorMsg);
                    refreshQRBtn.setDisable(false);
                    showError("生成二维码失败", "错误：" + errorMsg + "\n\n请检查服务器地址和网络连接");
                });
            }
        }).start();
    }

    /**
     * 刷新二维码
     */
    @FXML
    private void onRefreshQRCode() {
        stopQRCodePolling();
        String server = serverField.getText().trim();
        generateQRCode(server);
    }

    /**
     * 启动轮询检查二维码状态
     */
    private void startQRCodePolling(String serverUrl) {
        stopQRCodePolling();

        scheduler = Executors.newScheduledThreadPool(1);
        pollingTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                checkQRCodeStatus(serverUrl);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * 检查二维码状态
     */
    private void checkQRCodeStatus(String serverUrl) {
        try {
            ApiClient client = new ApiClient(serverUrl);
            String response = client.get("/api/v1/printer-accounts/qrcode/check/" + currentTicket);
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);

            String status = jsonResponse.get("status").getAsString();
            String message = jsonResponse.get("message").getAsString();

            Platform.runLater(() -> {
                switch (status) {
                    case "pending":
                        qrStatusLabel.setText("等待扫码授权...");
                        break;

                    case "authorized":
                        // 授权成功
                        stopQRCodePolling();
                        qrStatusLabel.setText("授权成功！");

                        Long userId = jsonResponse.get("userId").getAsLong();
                        String username = jsonResponse.get("username").getAsString();

                        // 保存登录信息
                        this.apiClient = client;
                        this.apiClient.setUserId(userId);  // 设置 userId
                        this.serverUrl = serverUrl;
                        this.username = username;
                        this.userId = userId;
                        this.loginSuccess = true;

                        // 关闭登录窗口
                        stage.close();
                        break;

                    case "expired":
                        // 二维码过期
                        stopQRCodePolling();
                        qrStatusLabel.setText(message);
                        showInfo("二维码已过期", "请点击刷新按钮重新获取二维码");
                        break;

                    default:
                        qrStatusLabel.setText("未知状态：" + status);
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
                Platform.runLater(() -> qrCountdownLabel.setText("已过期"));
                stopCountdown();
            } else {
                long minutes = remainingSeconds / 60;
                long seconds = remainingSeconds % 60;
                Platform.runLater(() ->
                    qrCountdownLabel.setText(String.format("有效期：%d分%d秒", minutes, seconds))
                );
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * 停止轮询
     */
    private void stopQRCodePolling() {
        if (pollingTask != null && !pollingTask.isCancelled()) {
            pollingTask.cancel(true);
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
        stopCountdown();
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

    @FXML
    private void onQRCodeLogin() {
        // 这个方法已经不需要了，保留以防 FXML 引用
        switchToQRCodeLogin();
    }

    /**
     * 保存登录信息（供扫码登录使用）
     */
    public static void saveLoginInfo(Long userId, String username) {
        PREFS.putLong("userId", userId);
        PREFS.put(KEY_USER, username);
    }

    public boolean isLoginSuccess() { return loginSuccess; }
    public String getServerUrl() { return serverUrl; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public ApiClient getApiClient() { return apiClient; }
    public Long getUserId() { return userId; }
}