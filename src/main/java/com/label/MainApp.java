package com.label;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class MainApp extends Application {

    private static Stage primaryStage;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;

        // Show login dialog (FXML)
        if (!showLoginDialog(stage)) {
            System.exit(0);
            return;
        }

        // Show main window (FXML)
        showMainWindow(stage);
    }

    private boolean showLoginDialog(Stage owner) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("login.fxml"));
            Parent root = loader.load();

            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(owner);
            dialog.setTitle("登录打单系统");

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
            dialog.setScene(scene);
            dialog.setResizable(false);

            LoginController controller = loader.getController();
            controller.setStage(dialog);
            dialog.showAndWait();

            if (controller.isLoginSuccess()) {
                // Stash for next scene
                owner.setUserData(controller);
                return true;
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void showMainWindow(Stage stage) {
        try {
            LoginController loginCtrl = (LoginController) stage.getUserData();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("main.fxml"));
            Parent root = loader.load();

            MainController controller = loader.getController();
            controller.setApiClient(loginCtrl.getApiClient());

            stage.setTitle("快递面单打印系统");
            Scene scene = new Scene(root, 950, 700);
            scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 显示扫码登录窗口
     */
    public static void showQRCodeLoginWindow(String serverUrl) {
        try {
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("qrcode-login.fxml"));
            Parent root = loader.load();

            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(primaryStage);
            dialog.setTitle("微信扫码登录");

            Scene scene = new Scene(root, 400, 600);
            scene.getStylesheets().add(MainApp.class.getResource("style.css").toExternalForm());
            dialog.setScene(scene);
            dialog.setResizable(false);

            QRCodeLoginController controller = loader.getController();
            controller.setServerUrl(serverUrl);

            // 窗口关闭时清理资源
            dialog.setOnCloseRequest(event -> controller.cleanup());

            dialog.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 显示登录窗口
     */
    public static void showLoginWindow() {
        try {
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("login.fxml"));
            Parent root = loader.load();

            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(primaryStage);
            dialog.setTitle("登录打单系统");

            Scene scene = new Scene(root);
            scene.getStylesheets().add(MainApp.class.getResource("style.css").toExternalForm());
            dialog.setScene(scene);
            dialog.setResizable(false);

            LoginController controller = loader.getController();
            controller.setStage(dialog);
            dialog.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 显示主窗口（扫码登录成功后调用）
     */
    public static void showMainWindow(Long userId) {
        try {
            // 创建 ApiClient
            String serverUrl = java.util.prefs.Preferences.userNodeForPackage(LoginController.class)
                .get("server", "http://192.168.10.217:8080");
            ApiClient apiClient = new ApiClient(serverUrl);
            apiClient.setUserId(userId);

            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("main.fxml"));
            Parent root = loader.load();

            MainController controller = loader.getController();
            controller.setApiClient(apiClient);

            primaryStage.setTitle("快递面单打印系统");
            Scene scene = new Scene(root, 950, 700);
            scene.getStylesheets().add(MainApp.class.getResource("style.css").toExternalForm());
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}