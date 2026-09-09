package com.label;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class MainController {

    // === Fetch tab ===
    @FXML private Button btnFetch;
    @FXML private Button btnGenerate;
    @FXML private Label fetchStatusLabel;
    @FXML private ProgressBar progressBar;
    @FXML private TableView<WaybillItem> waybillTable;
    @FXML private TableColumn<WaybillItem, Boolean> selectCol;
    @FXML private TableColumn<WaybillItem, String> waybillCol;
    @FXML private TableColumn<WaybillItem, String> recipientCol;
    @FXML private TableColumn<WaybillItem, String> addressCol;
    @FXML private TableColumn<WaybillItem, String> waybillCreatedTimeCol;
    @FXML private TableColumn<WaybillItem, String> orderCreatedTimeCol;
    @FXML private TableColumn<WaybillItem, String> lastGenTimeCol;
    @FXML private Button btnSelectUnprinted;
    @FXML private TextArea logArea;
    @FXML private DatePicker waybillDateFrom;
    @FXML private ComboBox<String> waybillHourFrom;
    @FXML private ComboBox<String> waybillMinuteFrom;
    @FXML private DatePicker waybillDateTo;
    @FXML private ComboBox<String> waybillHourTo;
    @FXML private ComboBox<String> waybillMinuteTo;
    @FXML private DatePicker orderDateFrom;
    @FXML private ComboBox<String> orderHourFrom;
    @FXML private ComboBox<String> orderMinuteFrom;
    @FXML private DatePicker orderDateTo;
    @FXML private ComboBox<String> orderHourTo;
    @FXML private ComboBox<String> orderMinuteTo;

    // === Print tab ===
    @FXML private ComboBox<String> printerComboBox;
    @FXML private Button btnSelectPrintFolder;
    @FXML private Button btnPrint;
    @FXML private Label printFolderLabel;
    @FXML private TableView<PdfFileItem> pdfTable;
    @FXML private TableColumn<PdfFileItem, Boolean> pdfSelectCol;
    @FXML private TableColumn<PdfFileItem, String> pdfNameCol;

    @FXML private TabPane tabPane;

    private ApiClient apiClient;
    private final ObservableList<WaybillItem> waybillItems = FXCollections.observableArrayList();
    private final List<WaybillItem> allItems = new ArrayList<>();
    private final ObservableList<PdfFileItem> pdfItems = FXCollections.observableArrayList();
    private File selectedPrintFolder;

    public void setApiClient(ApiClient client) {
        this.apiClient = client;
    }

    @FXML
    public void initialize() {
        selectCol.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        waybillCol.setCellValueFactory(cellData -> cellData.getValue().waybillIdProperty());
        recipientCol.setCellValueFactory(cellData -> cellData.getValue().recipientNameProperty());
        addressCol.setCellValueFactory(cellData -> cellData.getValue().recipientAddressProperty());
        waybillCreatedTimeCol.setCellValueFactory(cellData -> cellData.getValue().waybillCreatedTimeProperty());
        orderCreatedTimeCol.setCellValueFactory(cellData -> cellData.getValue().orderCreatedTimeProperty());
        lastGenTimeCol.setCellValueFactory(cellData -> cellData.getValue().lastGenTimeProperty());
        waybillTable.setItems(waybillItems);

        pdfSelectCol.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        pdfSelectCol.setCellFactory(CheckBoxTableCell.forTableColumn(pdfSelectCol));
        pdfNameCol.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        pdfTable.setItems(pdfItems);

        loadPrinters();
        initTimeCombos();
    }

    private void initTimeCombos() {
        List<String> hours = new ArrayList<>();
        List<String> minutes = new ArrayList<>();
        for (int i = 0; i < 24; i++) hours.add(String.format("%02d", i));
        for (int i = 0; i < 60; i++) minutes.add(String.format("%02d", i));

        for (ComboBox<String> cb : new ComboBox[]{waybillHourFrom, waybillHourTo, orderHourFrom, orderHourTo}) {
            cb.getItems().addAll(hours);
            cb.setValue("00");
            cb.setVisibleRowCount(12);
        }
        for (ComboBox<String> cb : new ComboBox[]{waybillMinuteFrom, waybillMinuteTo, orderMinuteFrom, orderMinuteTo}) {
            cb.getItems().addAll(minutes);
            cb.setValue("00");
            cb.setVisibleRowCount(12);
        }
    }

    // ==================== Fetch ====================

    @FXML
    private void onFetch() {
        btnFetch.setDisable(true);
        btnGenerate.setDisable(true);
        logArea.clear();
        fetchStatusLabel.setText("正在拉取面单数据...");
        waybillItems.clear();

        Task<List<WaybillData>> task = new Task<>() {
            @Override
            protected List<WaybillData> call() throws Exception {
                return apiClient.fetchWaybills();
            }
        };

        task.setOnSucceeded(ev -> {
            List<WaybillData> list = task.getValue();
            fetchStatusLabel.setText("拉取成功，共 " + list.size() + " 条面单数据");

            allItems.clear();
            for (WaybillData wd : list) {
                allItems.add(new WaybillItem(wd));
            }

            applyFilter();

            btnGenerate.setDisable(list.isEmpty());
            btnFetch.setDisable(false);
        });

        task.setOnFailed(ev -> {
            fetchStatusLabel.setText("拉取失败");
            logArea.appendText("错误：" + task.getException().getMessage() + "\n");
            btnFetch.setDisable(false);
        });

        new Thread(task).start();
    }

    @FXML
    private void onGenerate() {
        List<WaybillItem> selected = waybillItems.stream()
                .filter(WaybillItem::isSelected)
                .toList();

        if (selected.isEmpty()) {
            fetchStatusLabel.setText("请至少勾选一条面单数据");
            return;
        }

        btnFetch.setDisable(true);
        btnGenerate.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);
        logArea.clear();

        File outputDir = new File(System.getProperty("user.home"), "Desktop/面单PDF");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() {
                int success = 0;
                int total = selected.size();
                PdfGenerator generator = new PdfGenerator();

                for (int i = 0; i < total; i++) {
                    WaybillItem item = selected.get(i);
                    WaybillData data = item.getData();

                    try {
                        if (data.printHtml == null || data.printHtml.isEmpty()) {
                            Platform.runLater(() ->
                                logArea.appendText("✗ " + item.getWaybillId() + " - 无 print_html 数据\n")
                            );
                            continue;
                        }

                        byte[] htmlBytes = Base64.getDecoder().decode(data.printHtml);
                        String html = new String(htmlBytes, StandardCharsets.UTF_8);
                        Path tmpFile = Files.createTempFile("waybill_", ".html");
                        Files.write(tmpFile, html.getBytes(StandardCharsets.UTF_8));

                        try {
                            WaybillData parsedData = HtmlParser.parse(tmpFile.toFile());
                            parsedData.sourceFile = data.sourceFile;
                            parsedData.productInfo = data.productInfo;
                            // PDF 中脱敏，表格显示不脱敏；接口信息缺电话时保留 HTML 解析出的面单信息。
                            parsedData.recipientInfo = buildPdfInfo(data.recipientInfo, parsedData.recipientInfo, true);
                            parsedData.senderInfo = buildPdfInfo(data.senderInfo, parsedData.senderInfo, false);
                            parsedData.recipientAddr = PrivacyMasker.maskPhonesInText(parsedData.recipientAddr);
                            parsedData.senderAddr = PrivacyMasker.maskPhonesInText(parsedData.senderAddr);

                            String filename = item.getWaybillId().replaceAll("[\\\\/:*?\"<>|]", "_") + ".pdf";
                            File pdfFile = new File(outputDir, filename);

                            boolean hasContent = (parsedData.trackingNumber != null && !parsedData.trackingNumber.isEmpty())
                                    || !parsedData.hlines.isEmpty() || !parsedData.vlines.isEmpty() || !parsedData.images.isEmpty();

                            if (hasContent) {
                                generator.generate(parsedData, pdfFile);
                            } else {
                                generator.generateFromHtml(tmpFile.toFile(), pdfFile, data.productInfo);
                            }

                            if (item.getWaybillDataId() != null) {
                                try {
                                    apiClient.markPrinted(item.getWaybillDataId());
                                    Platform.runLater(() -> item.markPrinted());
                                } catch (Exception ex) {
                                    Platform.runLater(() ->
                                        logArea.appendText("⚠ 标记已生成失败 (id=" + item.getWaybillDataId() + "): " + ex.getMessage() + "\n")
                                    );
                                }
                            } else {
                                Platform.runLater(() ->
                                    logArea.appendText("⚠ 无法标记已生成：waybillDataId 为空 (" + item.getWaybillId() + ")\n")
                                );
                            }

                            Platform.runLater(() ->
                                logArea.appendText("✓ " + filename + "\n")
                            );
                            success++;
                        } finally {
                            try { Files.deleteIfExists(tmpFile); } catch (Exception ignored) {}
                        }
                    } catch (Exception ex) {
                        Platform.runLater(() ->
                            logArea.appendText("✗ " + item.getWaybillId() + " - " + ex.getMessage() + "\n")
                        );
                    }

                    final int idx = i;
                    Platform.runLater(() -> progressBar.setProgress((double) (idx + 1) / total));
                }
                return success;
            }
        };

        task.setOnSucceeded(ev -> {
            int ok = task.getValue();
            fetchStatusLabel.setText("完成！成功生成 " + ok + " / " + selected.size() + " 个 PDF");
            logArea.appendText("\nPDF 保存位置：" + outputDir.getAbsolutePath() + "\n");
            progressBar.setVisible(false);
            btnFetch.setDisable(false);
            btnGenerate.setDisable(false);
        });

        task.setOnFailed(ev -> {
            logArea.appendText("错误：" + task.getException().getMessage() + "\n");
            progressBar.setVisible(false);
            btnFetch.setDisable(false);
            btnGenerate.setDisable(false);
        });

        new Thread(task).start();
    }

    @FXML
    private void onSelectAll() {
        waybillItems.forEach(i -> i.setSelected(true));
    }

    @FXML
    private void onDeselectAll() {
        waybillItems.forEach(i -> i.setSelected(false));
    }

    @FXML
    private void onSelectUnprinted() {
        waybillItems.forEach(i -> i.setSelected("-".equals(i.getLastGenTime())));
    }

    @FXML
    private void onFilter() {
        applyFilter();
    }

    @FXML
    private void onClearFilter() {
        waybillDateFrom.setValue(null);
        waybillHourFrom.setValue(null);
        waybillMinuteFrom.setValue(null);
        waybillDateTo.setValue(null);
        waybillHourTo.setValue(null);
        waybillMinuteTo.setValue(null);
        orderDateFrom.setValue(null);
        orderHourFrom.setValue(null);
        orderMinuteFrom.setValue(null);
        orderDateTo.setValue(null);
        orderHourTo.setValue(null);
        orderMinuteTo.setValue(null);
        applyFilter();
    }

    private void applyFilter() {
        LocalDateTime wdFrom = combineDateTime(waybillDateFrom.getValue(), waybillHourFrom.getValue(), waybillMinuteFrom.getValue());
        LocalDateTime wdTo = combineDateTime(waybillDateTo.getValue(), waybillHourTo.getValue(), waybillMinuteTo.getValue());
        LocalDateTime odFrom = combineDateTime(orderDateFrom.getValue(), orderHourFrom.getValue(), orderMinuteFrom.getValue());
        LocalDateTime odTo = combineDateTime(orderDateTo.getValue(), orderHourTo.getValue(), orderMinuteTo.getValue());

        waybillItems.clear();
        for (WaybillItem item : allItems) {
            if (!matchDateTimeRange(item.getWaybillCreatedDateTime(), wdFrom, wdTo)) continue;
            if (!matchDateTimeRange(item.getOrderCreatedDateTime(), odFrom, odTo)) continue;
            waybillItems.add(item);
        }
        fetchStatusLabel.setText("显示 " + waybillItems.size() + " / " + allItems.size() + " 条");
    }

    /** 将 DatePicker + 时/分 ComboBox 合并为 LocalDateTime。未选日期返回 null。 */
    private static LocalDateTime combineDateTime(LocalDate date, String hour, String minute) {
        if (date == null) return null;
        int h = hour != null ? Integer.parseInt(hour) : 0;
        int m = minute != null ? Integer.parseInt(minute) : 0;
        return LocalDateTime.of(date, LocalTime.of(h, m, 0));
    }

    private static boolean matchDateTimeRange(LocalDateTime value, LocalDateTime from, LocalDateTime to) {
        if (from == null && to == null) return true;
        if (value == null) return false;
        if (from != null && value.isBefore(from)) return false;
        if (to != null && value.isAfter(to)) return false;
        return true;
    }

    private static String buildPdfInfo(String apiInfo, String parsedInfo, boolean recipient) {
        String source = hasPhoneNumber(apiInfo) || !hasText(parsedInfo) ? apiInfo : parsedInfo;
        if (!hasText(source)) return source;
        return recipient ? PrivacyMasker.maskRecipientInfo(source) : PrivacyMasker.maskSenderInfo(source);
    }

    private static boolean hasPhoneNumber(String value) {
        return value != null && value.replaceAll("[^0-9]", "").length() >= 7;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    // ==================== Print ====================

    private void loadPrinters() {
        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() {
                return PdfPrinter.getAvailablePrinters();
            }
        };
        task.setOnSucceeded(e -> {
            List<String> printers = task.getValue();
            printerComboBox.getItems().setAll(printers);
            if (!printers.isEmpty()) {
                printerComboBox.getSelectionModel().selectFirst();
            }
        });
        new Thread(task).start();
    }

    @FXML
    private void onSelectPrintFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择包含 PDF 文件的文件夹");
        File dir = chooser.showDialog(btnSelectPrintFolder.getScene().getWindow());
        if (dir != null) {
            selectedPrintFolder = dir;
            printFolderLabel.setText(dir.getAbsolutePath());
            loadPdfFiles(dir);
        }
    }

    private void loadPdfFiles(File folder) {
        pdfItems.clear();
        File[] files = folder.listFiles((d, name) -> name.toLowerCase().endsWith(".pdf"));
        if (files != null) {
            for (File f : files) {
                pdfItems.add(new PdfFileItem(f));
            }
        }
    }

    @FXML
    private void onPdfSelectAll() {
        pdfItems.forEach(i -> i.setSelected(true));
    }

    @FXML
    private void onPdfDeselectAll() {
        pdfItems.forEach(i -> i.setSelected(false));
    }

    @FXML
    private void onPrint() {
        String printer = printerComboBox.getValue();
        if (printer == null || printer.isEmpty()) {
            showAlert("请先选择打印机");
            return;
        }

        List<PdfFileItem> selected = pdfItems.stream()
                .filter(PdfFileItem::isSelected)
                .toList();

        if (selected.isEmpty()) {
            showAlert("请至少选择一个 PDF 文件");
            return;
        }

        btnPrint.setDisable(true);
        btnSelectPrintFolder.setDisable(true);

        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() {
                List<String> results = new ArrayList<>();
                for (PdfFileItem item : selected) {
                    try {
                        PdfPrinter.printPdf(item.getFile(), printer);
                        results.add("OK: " + item.getName());
                    } catch (Exception ex) {
                        results.add("FAIL: " + item.getName() + " — " + ex.getMessage());
                    }
                }
                return results;
            }
        };

        task.setOnSucceeded(ev -> {
            long ok = task.getValue().stream().filter(s -> s.startsWith("OK")).count();
            showAlert("打印完成！成功: " + ok + " / " + selected.size());
            btnPrint.setDisable(false);
            btnSelectPrintFolder.setDisable(false);
        });

        task.setOnFailed(ev -> {
            showAlert("打印错误: " + task.getException().getMessage());
            btnPrint.setDisable(false);
            btnSelectPrintFolder.setDisable(false);
        });

        new Thread(task).start();
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("提示");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    // ==================== PdfFileItem ====================

    public static class PdfFileItem {
        private final File file;
        private final SimpleBooleanProperty selected;
        private final SimpleStringProperty name;

        public PdfFileItem(File file) {
            this.file = file;
            this.selected = new SimpleBooleanProperty(true);
            this.name = new SimpleStringProperty(file.getName());
        }

        public File getFile() { return file; }
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean v) { selected.set(v); }
        public SimpleBooleanProperty selectedProperty() { return selected; }
        public String getName() { return name.get(); }
        public SimpleStringProperty nameProperty() { return name; }
    }
}
