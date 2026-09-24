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
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public class MainController {

    // === Fetch tab ===
    @FXML private Button btnFetch;
    @FXML private Button btnGenerate;
    @FXML private Button btnPrintSelected;
    @FXML private Label fetchStatusLabel;
    @FXML private Label printerStatusLabel;
    @FXML private ProgressBar progressBar;
    @FXML private TableView<WaybillItem> waybillTable;
    @FXML private TableColumn<WaybillItem, Void> sequenceCol;
    @FXML private TableColumn<WaybillItem, Boolean> selectCol;
    @FXML private TableColumn<WaybillItem, String> waybillCol;
    @FXML private TableColumn<WaybillItem, String> recipientCol;
    @FXML private TableColumn<WaybillItem, String> addressCol;
    @FXML private TableColumn<WaybillItem, String> orderNumberCol;
    @FXML private TableColumn<WaybillItem, String> remarkCol;
    @FXML private TableColumn<WaybillItem, String> waybillCreatedTimeCol;
    @FXML private TableColumn<WaybillItem, String> orderCreatedTimeCol;
    @FXML private TableColumn<WaybillItem, String> lastGenTimeCol;
    @FXML private TableColumn<WaybillItem, String> statusCol;
    @FXML private TableColumn<WaybillItem, Void> actionCol;
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
    @FXML private TextField keywordField;
    @FXML private ComboBox<String> printFilterBox;
    @FXML private ComboBox<String> pageSizeBox;
    @FXML private ComboBox<String> orderQuickRange;
    @FXML private ComboBox<String> waybillQuickRange;
    @FXML private Button previousPageButton;
    @FXML private Button nextPageButton;
    @FXML private Label pageLabel;
    @FXML private Label totalCountLabel;
    @FXML private Label selectedCountLabel;
    @FXML private TableView<WaybillItem> selectedTable;
    @FXML private TableColumn<WaybillItem, String> selectedWaybillCol;
    @FXML private TableColumn<WaybillItem, String> selectedOrderCol;
    @FXML private TableColumn<WaybillItem, String> selectedPdfCol;
    @FXML private TableColumn<WaybillItem, String> selectedPrintCol;
    @FXML private TableColumn<WaybillItem, Void> selectedRemoveCol;

    // === Print tab ===
    @FXML private ComboBox<String> printerComboBox;
    @FXML private Label printTabPrinterLabel;
    @FXML private Button btnSelectPrintFolder;
    @FXML private Button btnPrint;
    @FXML private Label printFolderLabel;
    @FXML private TableView<PdfFileItem> pdfTable;
    @FXML private TableColumn<PdfFileItem, Boolean> pdfSelectCol;
    @FXML private TableColumn<PdfFileItem, String> pdfNameCol;

    @FXML private TabPane tabPane;

    private ApiClient apiClient;
    private final ObservableList<WaybillItem> waybillItems = FXCollections.observableArrayList();
    private final ObservableList<WaybillItem> selectedItems = FXCollections.observableArrayList();
    private final Map<Long, WaybillItem> selectedById = new LinkedHashMap<>();
    private int currentPage = 0;
    private long totalItems = 0;
    private ApiClient.WaybillQuery activeQuery;
    private boolean loadingPage = false;
    private boolean processing = false;
    private long requestVersion = 0;
    private final ObservableList<PdfFileItem> pdfItems = FXCollections.observableArrayList();
    private File selectedPrintFolder;

    public void setApiClient(ApiClient client) {
        this.apiClient = client;
        loadPage(0, buildQuery(0));
    }

    @FXML
    public void initialize() {
        sequenceCol.setReorderable(false);
        sequenceCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : Integer.toString(currentPage * Integer.parseInt(pageSizeBox.getValue()) + getIndex() + 1));
            }
        });

        selectCol.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        waybillCol.setCellValueFactory(cellData -> cellData.getValue().waybillIdProperty());
        recipientCol.setCellValueFactory(cellData -> cellData.getValue().recipientNameProperty());
        addressCol.setCellValueFactory(cellData -> cellData.getValue().recipientAddressProperty());
        orderNumberCol.setCellValueFactory(cellData -> cellData.getValue().orderNumbersProperty());
        remarkCol.setCellValueFactory(cellData -> cellData.getValue().remarksProperty());
        waybillCreatedTimeCol.setCellValueFactory(cellData -> cellData.getValue().waybillCreatedTimeProperty());
        orderCreatedTimeCol.setCellValueFactory(cellData -> cellData.getValue().orderCreatedTimeProperty());
        lastGenTimeCol.setCellValueFactory(cellData -> cellData.getValue().lastGenTimeProperty());
        statusCol.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
        actionCol.setSortable(false);
        actionCol.setReorderable(false);
        actionCol.setCellFactory(column -> new TableCell<>() {
            private final Button generateButton = new Button("生成");
            private final Button printButton = new Button("打印");
            private final HBox buttons = new HBox(4, generateButton, printButton);

            {
                generateButton.setOnAction(event -> {
                    WaybillItem row = getTableView().getItems().get(getIndex());
                    runGenerateTask(List.of(row));
                });
                printButton.setOnAction(event -> {
                    if (!hasSelectedPrinter()) {
                        showAlert("请先选择打印机");
                        return;
                    }
                    WaybillItem row = getTableView().getItems().get(getIndex());
                    onPrintItems(List.of(row));
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : buttons);
            }
        });
        waybillTable.setItems(waybillItems);
        waybillTable.setSortPolicy(table -> {
            if (apiClient != null && !loadingPage) loadPage(0, buildQuery(0));
            return true;
        });

        selectedWaybillCol.setCellValueFactory(c -> c.getValue().waybillIdProperty());
        selectedOrderCol.setCellValueFactory(c -> c.getValue().orderNumbersProperty());
        selectedPdfCol.setCellValueFactory(c -> c.getValue().pdfStatusProperty());
        selectedPrintCol.setCellValueFactory(c -> c.getValue().printResultProperty());
        selectedRemoveCol.setSortable(false);
        selectedRemoveCol.setCellFactory(column -> new TableCell<>() {
            private final Button remove = new Button("移除");
            { remove.setOnAction(event -> getTableView().getItems().get(getIndex()).setSelected(false)); }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : remove);
            }
        });
        selectedTable.setItems(selectedItems);
        printFilterBox.getItems().addAll("全部", "未打印", "已打印", "未生成", "已生成");
        printFilterBox.setValue("全部");
        pageSizeBox.getItems().addAll("20", "50", "100", "200");
        pageSizeBox.setValue("20");
        pageSizeBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (apiClient != null && newValue != null) loadPage(0, buildQuery(0));
        });
        orderQuickRange.getItems().addAll("近3天", "近7天", "近一个月");
        waybillQuickRange.getItems().addAll("近3天", "近7天", "近一个月");

        pdfSelectCol.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        pdfSelectCol.setCellFactory(CheckBoxTableCell.forTableColumn(pdfSelectCol));
        pdfNameCol.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        pdfTable.setItems(pdfItems);

        printerComboBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && !newValue.isBlank()) {
                PrinterPreferences.setLastPrinter(newValue);
            }
            updatePrinterLabels();
        });

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

    // ==================== 分页查询 ====================

    private ApiClient.WaybillQuery buildQuery(int page) {
        String sortBy = "waybillCreatedAt";
        String direction = "desc";
        if (!waybillTable.getSortOrder().isEmpty()) {
            TableColumn<WaybillItem, ?> column = waybillTable.getSortOrder().get(0);
            if (column == sequenceCol) sortBy = "sequence";
            else if (column == orderNumberCol) sortBy = "orderNumber";
            else if (column == waybillCol) sortBy = "waybillId";
            else if (column == recipientCol) sortBy = "recipientName";
            else if (column == addressCol) sortBy = "recipientAddress";
            else if (column == remarkCol) sortBy = "remark";
            else if (column == waybillCreatedTimeCol) sortBy = "waybillCreatedAt";
            else if (column == orderCreatedTimeCol) sortBy = "orderCreatedAt";
            else if (column == lastGenTimeCol) sortBy = "lastGeneratedAt";
            else if (column == statusCol) sortBy = "printStatus";
            direction = column.getSortType() == TableColumn.SortType.ASCENDING ? "asc" : "desc";
        }
        String filter = switch (printFilterBox.getValue()) {
            case "未打印" -> "UNPRINTED";
            case "已打印" -> "PRINTED";
            case "未生成" -> "NOT_GENERATED";
            case "已生成" -> "GENERATED";
            default -> "ALL";
        };
        return new ApiClient.WaybillQuery(page, Integer.parseInt(pageSizeBox.getValue()),
                keywordField.getText().trim(), filter,
                dateTimeParam(orderDateFrom, orderHourFrom, orderMinuteFrom),
                dateTimeParam(orderDateTo, orderHourTo, orderMinuteTo),
                dateTimeParam(waybillDateFrom, waybillHourFrom, waybillMinuteFrom),
                dateTimeParam(waybillDateTo, waybillHourTo, waybillMinuteTo), sortBy, direction);
    }

    private static String dateTimeParam(DatePicker date, ComboBox<String> hour, ComboBox<String> minute) {
        LocalDateTime value = combineDateTime(date.getValue(), hour.getValue(), minute.getValue());
        return value == null ? null : value.toString();
    }

    private void loadPage(int page, ApiClient.WaybillQuery query) {
        if (apiClient == null) return;
        if (processing) return;
        if (query.orderFrom() != null && query.orderTo() != null && query.orderFrom().compareTo(query.orderTo()) > 0
                || query.waybillFrom() != null && query.waybillTo() != null
                && query.waybillFrom().compareTo(query.waybillTo()) > 0) {
            showAlert("开始时间不能晚于结束时间");
            return;
        }
        long version = ++requestVersion;
        loadingPage = true;
        btnFetch.setDisable(true);
        waybillTable.setDisable(true);
        previousPageButton.setDisable(true);
        nextPageButton.setDisable(true);
        fetchStatusLabel.setText("正在查询...");
        Task<ApiClient.WaybillPage> task = new Task<>() {
            @Override protected ApiClient.WaybillPage call() throws Exception {
                return apiClient.fetchWaybillPage(query);
            }
        };
        task.setOnSucceeded(event -> {
            if (version != requestVersion) return;
            ApiClient.WaybillPage result = task.getValue();
            activeQuery = query;
            currentPage = page;
            totalItems = result.total();
            List<WaybillItem> rows = new ArrayList<>();
            for (WaybillData data : result.items()) {
                WaybillItem chosen = selectedById.get(data.id);
                WaybillItem item = chosen != null ? chosen : new WaybillItem(data);
                if (chosen == null) watchSelection(item);
                rows.add(item);
            }
            waybillItems.setAll(rows);
            finishPageLoad();
        });
        task.setOnFailed(event -> {
            if (version != requestVersion) return;
            fetchStatusLabel.setText("查询失败：" + safeMessage(task.getException()));
            loadingPage = false;
            btnFetch.setDisable(false);
            waybillTable.setDisable(false);
            updatePagination();
        });
        new Thread(task, "waybill-page").start();
    }

    private void finishPageLoad() {
        loadingPage = false;
        btnFetch.setDisable(false);
        waybillTable.setDisable(false);
        fetchStatusLabel.setText("共 " + totalItems + " 条，当前 " + waybillItems.size() + " 条");
        updatePagination();
        updateFetchActionState();
    }

    private void updatePagination() {
        long pages = Math.max(1, (totalItems + Integer.parseInt(pageSizeBox.getValue()) - 1)
                / Integer.parseInt(pageSizeBox.getValue()));
        totalCountLabel.setText("共 " + totalItems + " 条");
        pageLabel.setText("第 " + (currentPage + 1) + " / " + pages + " 页");
        previousPageButton.setDisable(loadingPage || currentPage == 0);
        nextPageButton.setDisable(loadingPage || currentPage + 1 >= pages);
    }

    private void watchSelection(WaybillItem item) {
        item.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            Long id = item.getWaybillDataId();
            if (id == null) return;
            if (isSelected) {
                selectedById.put(id, item);
                if (!selectedItems.contains(item)) selectedItems.add(item);
            } else {
                selectedById.remove(id);
                selectedItems.remove(item);
            }
            selectedCountLabel.setText("已选中 " + selectedItems.size() + " 条");
            updateFetchActionState();
        });
    }

    @FXML private void onPreviousPage() { loadPage(currentPage - 1, activeQuery.withPage(currentPage - 1)); }
    @FXML private void onNextPage() { loadPage(currentPage + 1, activeQuery.withPage(currentPage + 1)); }

    @FXML
    private void onGenerate() {
        List<WaybillItem> selected = getSelectedWaybills();

        if (selected.isEmpty()) {
            fetchStatusLabel.setText("请至少勾选一条面单数据");
            Logger.warn("用户未勾选任何面单");
            return;
        }

        selectedTable.requestFocus();
        runGenerateTask(selected);
    }

    private void runGenerateTask(List<WaybillItem> selected) {
        Logger.info("开始生成面单 PDF，共 " + selected.size() + " 条");
        btnFetch.setDisable(true);
        btnGenerate.setDisable(true);
        btnPrintSelected.setDisable(true);
        processing = true;
        selectedTable.setDisable(true);
        waybillTable.setDisable(true);
        pageSizeBox.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);
        logArea.clear();

        File outputDir = getPdfOutputDir();
        Logger.info("输出目录: " + outputDir.getAbsolutePath());

        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() {
                int success = 0;
                int total = selected.size();
                PdfGenerator generator = new PdfGenerator();
                YundaPdfGenerator yundaGenerator = new YundaPdfGenerator();

                for (int i = 0; i < total; i++) {
                    WaybillItem item = selected.get(i);
                    try {
                        Logger.info("开始处理面单 [" + (i+1) + "/" + total + "]: " + item.getWaybillId());

                        setItemStatus(item, "生成中");
                        Platform.runLater(() -> item.setPdfStatus("生成中"));
                        File pdfFile = generatePdfForItem(item, outputDir, generator, yundaGenerator);
                        markGenerated(item, pdfFile);
                        appendLog("✓ " + pdfFile.getName() + "：PDF 生成成功\n");
                        success++;
                    } catch (Exception ex) {
                        Logger.error("处理面单失败: " + item.getWaybillId(), ex);
                        setItemStatus(item, "生成失败");
                        Platform.runLater(() -> item.setPdfStatus("生成失败：" + safeMessage(ex)));
                        appendLog("✗ " + item.getWaybillId() + " - " + safeMessage(ex) + "\n");
                    }

                    final int idx = i;
                    Platform.runLater(() -> progressBar.setProgress((double) (idx + 1) / total));
                }
                Logger.info("PDF 生成任务完成，成功: " + success + "/" + total);
                return success;
            }
        };

        task.setOnSucceeded(ev -> {
            int ok = task.getValue();
            fetchStatusLabel.setText("完成！成功生成 " + ok + " / " + selected.size() + " 个 PDF");
            logArea.appendText("\nPDF 保存位置：" + outputDir.getAbsolutePath() + "\n");
            progressBar.setProgress(1);
            progressBar.setVisible(false);
            waybillTable.setDisable(false);
            selectedTable.setDisable(false);
            pageSizeBox.setDisable(false);
            processing = false;
            btnFetch.setDisable(false);
            updateFetchActionState();
        });

        task.setOnFailed(ev -> {
            logArea.appendText("错误：" + task.getException().getMessage() + "\n");
            progressBar.setVisible(false);
            waybillTable.setDisable(false);
            selectedTable.setDisable(false);
            pageSizeBox.setDisable(false);
            processing = false;
            btnFetch.setDisable(false);
            updateFetchActionState();
        });

        new Thread(task).start();
    }

    @FXML
    private void onPrintSelected() {
        List<WaybillItem> selected = getSelectedWaybills();
        if (selected.isEmpty()) {
            showAlert("请至少勾选一条面单数据");
            return;
        }
        selectedTable.requestFocus();
        onPrintItems(selected);
    }

    private List<WaybillItem> getSelectedWaybills() {
        return List.copyOf(selectedItems);
    }

    private void onPrintItems(List<WaybillItem> selected) {
        if (!hasSelectedPrinter()) {
            showAlert("请先选择打印机");
            return;
        }

        String printer = printerComboBox.getValue();
        File outputDir = getPdfOutputDir();
        setWaybillActionsDisabled(true);
        processing = true;
        selectedTable.setDisable(true);
        pageSizeBox.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);
        logArea.clear();

        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() {
                int success = 0;
                for (int i = 0; i < selected.size(); i++) {
                    WaybillItem item = selected.get(i);
                    boolean printStarted = false;
                    try {
                        File pdfFile = resolvePdfFile(item);
                        if (!pdfFile.isFile()) {
                            setItemStatus(item, "生成中");
                            Platform.runLater(() -> item.setPdfStatus("生成中"));
                            pdfFile = generatePdfForItem(item, outputDir, new PdfGenerator(), new YundaPdfGenerator());
                            markGenerated(item, pdfFile);
                            appendLog("✓ " + pdfFile.getName() + "：PDF 生成成功\n");
                        } else {
                            item.attachPdfFile(pdfFile);
                            Platform.runLater(() -> item.setPdfStatus("PDF已存在"));
                        }

                        setItemStatus(item, "打印中");
                        Platform.runLater(() -> item.setPrintResult("打印中"));
                        printStarted = true;
                        PdfPrinter.printPdf(pdfFile, printer);
                        markPrinted(item);
                        appendLog("✓ " + pdfFile.getName() + "：打印成功\n");
                        success++;
                    } catch (Exception ex) {
                        if (printStarted) {
                            markPrintFailed(item);
                        }
                        setItemStatus(item, printStarted ? "打印失败" : "生成失败");
                        if (!printStarted) Platform.runLater(() -> item.setPdfStatus("生成失败：" + safeMessage(ex)));
                        else Platform.runLater(() -> item.setPrintResult("打印失败：" + safeMessage(ex)));
                        appendLog("✗ " + item.getWaybillId() + " - " + safeMessage(ex) + "\n");
                    }
                    final int index = i;
                    Platform.runLater(() -> progressBar.setProgress((double) (index + 1) / selected.size()));
                }
                return success;
            }
        };
        task.setOnSucceeded(event -> {
            progressBar.setVisible(false);
            waybillTable.setDisable(false);
            selectedTable.setDisable(false);
            pageSizeBox.setDisable(false);
            processing = false;
            btnFetch.setDisable(false);
            updateFetchActionState();
            showAlert("打印完成！成功: " + task.getValue() + " / " + selected.size());
        });
        task.setOnFailed(event -> {
            progressBar.setVisible(false);
            waybillTable.setDisable(false);
            selectedTable.setDisable(false);
            pageSizeBox.setDisable(false);
            processing = false;
            btnFetch.setDisable(false);
            updateFetchActionState();
            showAlert("打印错误：" + safeMessage(task.getException()));
        });
        new Thread(task).start();
    }

    @FXML
    private void onSelectAll() {
        if (processing || activeQuery == null || totalItems == 0) return;
        ApiClient.WaybillQuery snapshot = activeQuery.withSize(200);
        long selectionVersion = requestVersion;
        processing = true;
        btnFetch.setDisable(true);
        btnGenerate.setDisable(true);
        btnPrintSelected.setDisable(true);
        selectedTable.setDisable(true);
        pageSizeBox.setDisable(true);
        fetchStatusLabel.setText("正在选中全部查询结果...");
        Task<List<WaybillData>> task = new Task<>() {
            @Override protected List<WaybillData> call() throws Exception {
                List<WaybillData> result = new ArrayList<>();
                long expected = totalItems;
                for (int page = 0; page * 200L < expected; page++) {
                    ApiClient.WaybillPage response = apiClient.fetchWaybillPage(snapshot.withPage(page));
                    expected = response.total();
                    if (response.items().isEmpty()) break;
                    result.addAll(response.items());
                }
                return result;
            }
        };
        task.setOnSucceeded(event -> {
            if (selectionVersion != requestVersion) {
                btnFetch.setDisable(false);
                processing = false;
                selectedTable.setDisable(false);
                pageSizeBox.setDisable(false);
                updateFetchActionState();
                return;
            }
            for (WaybillData data : task.getValue()) {
                WaybillItem item = selectedById.get(data.id);
                if (item == null) {
                    item = waybillItems.stream().filter(row -> data.id.equals(row.getWaybillDataId()))
                            .findFirst().orElseGet(() -> new WaybillItem(data));
                    if (!waybillItems.contains(item)) watchSelection(item);
                }
                item.setSelected(true);
            }
            btnFetch.setDisable(false);
            processing = false;
            selectedTable.setDisable(false);
            pageSizeBox.setDisable(false);
            updateFetchActionState();
            fetchStatusLabel.setText("已选中全部查询结果，共 " + selectedItems.size() + " 条");
        });
        task.setOnFailed(event -> {
            btnFetch.setDisable(false);
            processing = false;
            selectedTable.setDisable(false);
            pageSizeBox.setDisable(false);
            updateFetchActionState();
            fetchStatusLabel.setText("全选失败：" + safeMessage(task.getException()));
        });
        new Thread(task, "waybill-select-all").start();
    }

    @FXML
    private void onDeselectAll() {
        if (processing) return;
        List.copyOf(selectedItems).forEach(i -> i.setSelected(false));
    }

    @FXML
    private void onSelectUnprinted() {
        if (processing) return;
        waybillItems.forEach(i -> i.setSelected(!"已打印".equals(i.getStatus())));
    }

    @FXML
    private void onFilter() {
        loadPage(0, buildQuery(0));
    }

    @FXML
    private void onClearFilter() {
        if (processing) return;
        keywordField.clear();
        printFilterBox.setValue("全部");
        orderQuickRange.setValue(null);
        waybillQuickRange.setValue(null);
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
        loadingPage = true;
        waybillTable.getSortOrder().clear();
        loadingPage = false;
        loadPage(0, buildQuery(0));
    }

    @FXML private void onWaybillQuickRange() {
        applyQuickRange(waybillQuickRange.getValue(), waybillDateFrom, waybillHourFrom,
                waybillMinuteFrom, waybillDateTo, waybillHourTo, waybillMinuteTo);
    }

    @FXML private void onOrderQuickRange() {
        applyQuickRange(orderQuickRange.getValue(), orderDateFrom, orderHourFrom,
                orderMinuteFrom, orderDateTo, orderHourTo, orderMinuteTo);
    }

    private void applyQuickRange(String choice, DatePicker fromDate, ComboBox<String> fromHour,
                                 ComboBox<String> fromMinute, DatePicker toDate,
                                 ComboBox<String> toHour, ComboBox<String> toMinute) {
        if (choice == null) return;
        LocalDateTime end = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        LocalDateTime start = switch (choice) {
            case "近3天" -> end.minusDays(3);
            case "近7天" -> end.minusDays(7);
            default -> end.minusMonths(1);
        };
        fromDate.setValue(start.toLocalDate());
        fromHour.setValue(String.format("%02d", start.getHour()));
        fromMinute.setValue(String.format("%02d", start.getMinute()));
        toDate.setValue(end.toLocalDate());
        toHour.setValue(String.format("%02d", end.getHour()));
        toMinute.setValue(String.format("%02d", end.getMinute()));
    }

    /** 将 DatePicker + 时/分 ComboBox 合并为 LocalDateTime。未选日期返回 null。 */
    private static LocalDateTime combineDateTime(LocalDate date, String hour, String minute) {
        if (date == null) return null;
        int h = hour != null ? Integer.parseInt(hour) : 0;
        int m = minute != null ? Integer.parseInt(minute) : 0;
        return LocalDateTime.of(date, LocalTime.of(h, m, 0));
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

    private File getPdfOutputDir() {
        File outputDir = new File(System.getProperty("user.home"), "Desktop/面单PDF");
        if (!outputDir.exists() && !outputDir.mkdirs() && !outputDir.isDirectory()) {
            throw new IllegalStateException("无法创建 PDF 输出目录: " + outputDir.getAbsolutePath());
        }
        return outputDir;
    }

    private File generatePdfForItem(WaybillItem item, File outputDir,
                                    PdfGenerator generator, YundaPdfGenerator yundaGenerator) throws Exception {
        WaybillData data = item.getData();
        if (data.printHtml == null || data.printHtml.isEmpty()) {
            throw new IllegalArgumentException("无 print_html 数据");
        }

        byte[] htmlBytes = Base64.getDecoder().decode(data.printHtml);
        String html = new String(htmlBytes, StandardCharsets.UTF_8);
        Path tmpFile = Files.createTempFile("waybill_", ".html");
        Files.write(tmpFile, html.getBytes(StandardCharsets.UTF_8));
        Logger.debug("临时 HTML 文件: " + tmpFile.toAbsolutePath());

        try {
            boolean isYunda = "YUNDA".equalsIgnoreCase(item.getExpressCode());
            WaybillData parsedData = isYunda
                    ? YundaHtmlParser.parse(tmpFile.toFile())
                    : HtmlParser.parse(tmpFile.toFile());

            parsedData.sourceFile = data.sourceFile;
            String pdfProductInfo = data.getProductInfoForPdf();
            parsedData.productInfo = pdfProductInfo;
            // PDF 中脱敏，表格显示不脱敏；接口信息缺电话时保留 HTML 解析出的面单信息。
            parsedData.recipientInfo = buildPdfInfo(data.recipientInfo, parsedData.recipientInfo, true);
            parsedData.senderInfo = buildPdfInfo(data.senderInfo, parsedData.senderInfo, false);
            parsedData.recipientAddr = PrivacyMasker.maskPhonesInText(parsedData.recipientAddr);
            parsedData.senderAddr = PrivacyMasker.maskPhonesInText(parsedData.senderAddr);

            String filename = item.getWaybillId().replaceAll("[\\\\/:*?\"<>|]", "_") + ".pdf";
            File pdfFile = new File(outputDir, filename);
            Logger.info("生成 PDF: " + pdfFile.getAbsolutePath());

            boolean hasContent = (parsedData.trackingNumber != null && !parsedData.trackingNumber.isEmpty())
                    || !parsedData.hlines.isEmpty() || !parsedData.vlines.isEmpty() || !parsedData.images.isEmpty();
            if (hasContent) {
                if (isYunda) {
                    yundaGenerator.generate(parsedData, pdfFile);
                } else {
                    generator.generate(parsedData, pdfFile);
                }
            } else {
                Logger.warn("面单内容为空，使用 HTML 直接生成");
                generator.generateFromHtml(tmpFile.toFile(), pdfFile, pdfProductInfo);
            }
            return pdfFile;
        } finally {
            try {
                Files.deleteIfExists(tmpFile);
            } catch (Exception ignored) {
            }
        }
    }

    private void markGenerated(WaybillItem item, File pdfFile) {
        if (item.getWaybillDataId() != null) {
            try {
                apiClient.markGenerated(item.getWaybillDataId());
            } catch (Exception ex) {
                Logger.warn("同步已生成状态失败 (id=" + item.getWaybillDataId() + "): " + ex.getMessage());
                appendLog("⚠ 同步已生成状态失败 (" + item.getWaybillId() + "): " + safeMessage(ex) + "\n");
            }
        } else {
            appendLog("⚠ 无法标记已生成：waybillDataId 为空 (" + item.getWaybillId() + ")\n");
        }
        Platform.runLater(() -> item.markGenerated(pdfFile));
    }

    private void markPrinted(WaybillItem item) {
        if (item.getWaybillDataId() != null) {
            try {
                apiClient.markPrinted(item.getWaybillDataId());
            } catch (Exception ex) {
                Logger.warn("同步打印成功状态失败 (id=" + item.getWaybillDataId() + "): " + ex.getMessage());
                appendLog("⚠ 同步打印成功状态失败 (" + item.getWaybillId() + "): " + safeMessage(ex) + "\n");
            }
        }
        Platform.runLater(item::markPrinted);
    }

    private void markPrintFailed(WaybillItem item) {
        if (item.getWaybillDataId() != null) {
            try {
                apiClient.markPrintFailed(item.getWaybillDataId());
            } catch (Exception ex) {
                Logger.warn("同步打印失败状态失败 (id=" + item.getWaybillDataId() + "): " + ex.getMessage());
                appendLog("⚠ 同步打印失败状态失败 (" + item.getWaybillId() + "): " + safeMessage(ex) + "\n");
            }
        }
        Platform.runLater(item::markPrintFailed);
    }

    private void setItemStatus(WaybillItem item, String status) {
        Platform.runLater(() -> item.setStatus(status));
    }

    private void appendLog(String message) {
        Platform.runLater(() -> logArea.appendText(message));
    }

    private File resolvePdfFile(WaybillItem item) {
        if (item.getPdfFile() != null) {
            return item.getPdfFile();
        }
        File file = new File(getPdfOutputDir(),
                item.getWaybillId().replaceAll("[\\\\/:*?\"<>|]", "_") + ".pdf");
        if (file.isFile()) {
            item.attachPdfFile(file);
            Platform.runLater(() -> item.setStatus("已生成"));
        }
        return file;
    }

    private boolean hasSelectedPrinter() {
        return printerComboBox.getValue() != null && !printerComboBox.getValue().isBlank();
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null) return "未知错误";
        return throwable.getMessage() == null || throwable.getMessage().isBlank()
                ? throwable.getClass().getSimpleName()
                : throwable.getMessage();
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
                String lastPrinter = PrinterPreferences.getLastPrinter();
                if (lastPrinter != null && printers.contains(lastPrinter)) {
                    printerComboBox.getSelectionModel().select(lastPrinter);
                    printerStatusLabel.setText("已找到 " + printers.size() + " 台打印机");
                } else {
                    printerComboBox.getSelectionModel().selectFirst();
                    if (lastPrinter != null && !lastPrinter.isBlank()) {
                        printerStatusLabel.setText("上次打印机不可用，已切换到当前可用打印机");
                    } else {
                        printerStatusLabel.setText("已找到 " + printers.size() + " 台打印机");
                    }
                }
            } else {
                printerStatusLabel.setText("未发现可用打印机");
            }
            updatePrinterLabels();
        });
        task.setOnFailed(e -> printerStatusLabel.setText("读取打印机失败：" + safeMessage(task.getException())));
        new Thread(task).start();
    }

    @FXML
    private void onRefreshPrinters() {
        printerStatusLabel.setText("正在读取打印机...");
        loadPrinters();
    }

    private void updatePrinterLabels() {
        String printer = printerComboBox.getValue();
        printTabPrinterLabel.setText(printer == null || printer.isBlank()
                ? "请在“面单查询”页选择"
                : "当前使用：" + printer);
    }

    private void updateFetchActionState() {
        boolean hasItems = !selectedItems.isEmpty();
        btnGenerate.setDisable(processing || !hasItems);
        btnPrintSelected.setDisable(processing || !hasItems);
    }

    private void restoreFetchActions() {
        waybillTable.setDisable(false);
        btnFetch.setDisable(false);
        updateFetchActionState();
    }

    private void setWaybillActionsDisabled(boolean disabled) {
        btnFetch.setDisable(disabled);
        btnGenerate.setDisable(disabled);
        btnPrintSelected.setDisable(disabled);
        waybillTable.setDisable(disabled);
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
