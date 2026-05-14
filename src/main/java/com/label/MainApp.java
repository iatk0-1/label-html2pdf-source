package com.label;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) {
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
            Scene scene = new Scene(root, 780, 620);
            scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}