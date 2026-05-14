package com.label;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import java.util.prefs.Preferences;

public class LoginController {

    private static final Preferences PREFS = Preferences.userNodeForPackage(LoginController.class);
    private static final String KEY_SERVER = "server";
    private static final String KEY_USER = "username";
    private static final String KEY_PASS = "password";
    private static final String KEY_REMEMBER = "remember";

    @FXML private HBox serverRow;
    @FXML private TextField serverField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private CheckBox rememberPwdCheck;
    @FXML private Label statusLabel;
    @FXML private Button loginBtn;

    private Stage stage;
    private boolean loginSuccess = false;
    private String serverUrl;
    private String username;
    private String password;
    private ApiClient apiClient;

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
        stage.close();
    }

    public boolean isLoginSuccess() { return loginSuccess; }
    public String getServerUrl() { return serverUrl; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public ApiClient getApiClient() { return apiClient; }
}